package com.owlsoda.pageportal.util

import android.util.Log
import com.owlsoda.pageportal.core.database.entity.BookEntity
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.*
import okhttp3.Response

/**
 * Utility class for handling book downloads.
 * Ported from ReadaloudBooks-main with adaptations for PagePortal.
 * 
 * Features:
 * - Resumable downloads with Range headers
 * - SHA-256 hash verification (when server provides X-Storyteller-Hash)
 * - Organized file structure: Author/Series/Title.ext
 */
object DownloadUtils {
    private const val TAG = "DownloadUtils"
    
    /**
     * Sanitize a string for use as a filename/directory name.
     * Removes characters that are invalid in file paths.
     */
    fun sanitize(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    /**
     * Get the directory where a book's files should be stored.
     * Structure: filesDir/Author/Series/ or filesDir/Author/
     */
    fun getBookDir(filesDir: File, book: BookEntity): File {
        var authorName = book.authors.ifBlank { "Unknown Author" }
        // Handle JSON array if present
        if (authorName.startsWith("[") && authorName.endsWith("]")) {
            try {
                // Simple parsing to avoid Gson dependency here if possible, or just strip
                val content = authorName.substring(1, authorName.length - 1)
                val authors = content.split(",").map { it.trim().removeSurrounding("\"") }
                authorName = authors.firstOrNull() ?: "Unknown Author"
            } catch (e: Exception) {
                // Fallback to raw string
            }
        }
        
        val authorPart = sanitize(authorName)
        val seriesPart = book.series?.let { sanitize(it) }
        
        return if (!seriesPart.isNullOrBlank()) {
            File(File(filesDir, authorPart), seriesPart)
        } else {
            File(filesDir, authorPart)
        }
    }

    /**
     * Get the base filename for a book (without extension).
     * Format: "01 - Title" if has series index, otherwise just "Title"
     */
    fun getBaseFileName(book: BookEntity): String {
        val indexPart = book.seriesIndex?.toFloatOrNull()?.toInt()?.let { 
            it.toString().padStart(2, '0') 
        }
        return if (indexPart != null) {
            sanitize("$indexPart - ${book.title}")
        } else {
            sanitize(book.title)
        }
    }

    /**
     * Check if an audiobook file exists for this book.
     */
    fun isAudiobookDownloaded(filesDir: File, book: BookEntity): Boolean {
        val bookDir = getBookDir(filesDir, book)
        val baseFileName = getBaseFileName(book)
        return File(bookDir, "$baseFileName.m4b").exists()
    }

    /**
     * Check if an ebook file exists for this book.
     */
    fun isEbookDownloaded(filesDir: File, book: BookEntity): Boolean {
        val bookDir = getBookDir(filesDir, book)
        val baseFileName = getBaseFileName(book)
        return File(bookDir, "$baseFileName.epub").exists()
    }

    /**
     * Check if a readaloud file exists for this book.
     */
    fun isReadAloudDownloaded(filesDir: File, book: BookEntity): Boolean {
        val bookDir = getBookDir(filesDir, book)
        val baseFileName = getBaseFileName(book)
        return File(bookDir, "$baseFileName (readaloud).epub").exists()
    }

    /**
     * Check if any content is downloaded for this book.
     */
    fun isBookDownloaded(filesDir: File, book: BookEntity): Boolean {
        val aExists = isAudiobookDownloaded(filesDir, book)
        val eExists = isEbookDownloaded(filesDir, book)
        val rExists = isReadAloudDownloaded(filesDir, book)
        
        if (aExists || eExists || rExists) {
            Log.d(TAG, "Found downloaded content for ${book.title}: Audio=$aExists, Ebook=$eExists, ReadAloud=$rExists")
        }
        
        return aExists || eExists || rExists
    }

    fun getFilePath(filesDir: File, book: BookEntity, format: DownloadFormat): File {
        val bookDir = getBookDir(filesDir, book)
        val baseFileName = getBaseFileName(book)
        val fileName = when (format) {
            DownloadFormat.AUDIO -> "$baseFileName.m4b"
            DownloadFormat.EBOOK -> "$baseFileName.epub"
            DownloadFormat.PDF -> "$baseFileName.pdf"
            DownloadFormat.READALOUD -> "$baseFileName (readaloud).epub"
        }
        return File(bookDir, fileName)
    }

    /**
     * Get a temporary file path for a book download that is independent of metadata.
     */
    fun getTempFilePath(filesDir: File, bookId: Long, format: DownloadFormat): File {
        val tempDir = File(filesDir, ".tmp_downloads")
        if (!tempDir.exists()) tempDir.mkdirs()
        
        val fileName = when (format) {
            DownloadFormat.AUDIO -> "book_${bookId}_audio.tmp"
            DownloadFormat.EBOOK -> "book_${bookId}_ebook.tmp"
            DownloadFormat.PDF -> "book_${bookId}_pdf.tmp"
            DownloadFormat.READALOUD -> "book_${bookId}_readaloud.tmp"
        }
        return File(tempDir, fileName)
    }

    /**
     * Safe file move/rename.
     */
    fun moveFile(src: File, dst: File) {
        if (!src.exists()) return
        dst.parentFile?.mkdirs()
        if (src.renameTo(dst)) return
        
        // Fallback for cross-partition move
        src.inputStream().use { input ->
            dst.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        src.delete()
    }

    /**
     * Download a file with smart strategy selection:
     * - Token URLs → always single stream (servers reject Range on token URLs)
     * - Large files + Range support → 2-part parallel
     * - Everything else → single stream with resume
     */
    suspend fun downloadFile(
        client: OkHttpClient,
        url: String,
        file: File,
        headers: Map<String, String> = emptyMap(),
        onProgress: suspend (Float) -> Unit
    ) = coroutineScope {
        // Smart strategy: token URLs always use single stream
        val hasToken = url.contains("token=")
        if (hasToken) {
            Log.d(TAG, "URL has embedded token — forcing single stream")
            downloadFileSingle(client, url, file, headers, onProgress)
            return@coroutineScope
        }

        file.parentFile?.mkdirs()
        
        // Probe server capabilities
        val headRequest = Request.Builder()
            .url(url)
            .head()
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()
            
        val (contentLength, acceptsRanges) = withContext(Dispatchers.IO) {
            try {
                client.newCall(headRequest).execute().use { resp ->
                    val length = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    val ranges = resp.header("Accept-Ranges") == "bytes" || resp.code == 206
                    length to ranges
                }
            } catch (e: Exception) {
                Log.w(TAG, "HEAD request failed, falling back to single stream: ${e.message}")
                -1L to false
            }
        }

        // Only use multi-part for large files (>20MB) with Range support
        if (contentLength <= 20 * 1024 * 1024 || !acceptsRanges) {
            Log.d(TAG, "Using single stream (size=$contentLength, ranges=$acceptsRanges)")
            downloadFileSingle(client, url, file, headers, onProgress)
            return@coroutineScope
        }

        try {
            val numParts = 2
            Log.d(TAG, "Starting multi-part download ($numParts parts, size=$contentLength)")
            
            val partSize = contentLength / numParts
            val progressMap = java.util.concurrent.ConcurrentHashMap<Int, Long>()
            var lastTotalReported = 0L

            withContext(Dispatchers.IO) {
                RandomAccessFile(file, "rw").use { raf ->
                    if (raf.length() != contentLength) {
                        raf.setLength(contentLength)
                    }
                }
            }

            (0 until numParts).map { i ->
                async(Dispatchers.IO) {
                    val start = i * partSize
                    val end = if (i == numParts - 1) contentLength - 1 else (i + 1) * partSize - 1
                    
                    downloadPart(client, url, file, start, end, headers) { bytesRead ->
                        val currentStored = progressMap[i] ?: 0L
                        if (bytesRead > currentStored) {
                            progressMap[i] = bytesRead
                            val totalRead = progressMap.values.sum()
                            if (totalRead > lastTotalReported + (contentLength / 200)) {
                                lastTotalReported = totalRead
                                onProgress(totalRead.toFloat() / contentLength)
                            }
                        }
                    }
                }
            }.awaitAll()
            
            onProgress(1.0f)
        } catch (e: Exception) {
            Log.w(TAG, "Multi-part download failed, falling back to single stream: ${e.message}")
            // Clear the file size if it was pre-allocated to avoid corrupted resume
            if (file.exists() && file.length() == contentLength) {
                 // don't delete, just truncate or let singleDownload handle it. 
                 // Actually, if we pre-allocated, we should probably delete to be safe.
                 file.delete() 
            }
            downloadFileSingle(client, url, file, headers, onProgress)
        }
    }

    private suspend fun downloadPart(
        client: OkHttpClient,
        url: String,
        file: File,
        start: Long,
        end: Long,
        headers: Map<String, String>,
        onProgress: suspend (Long) -> Unit
    ) {
        var attempts = 0
        val maxAttempts = 5
        var lastError: Exception? = null

        while (attempts < maxAttempts) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Range", "bytes=$start-$end")
                    .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        Log.e(TAG, "Part download HTTP error: ${response.code} for range $start-$end")
                        throw Exception("Part download failed (HTTP ${response.code})")
                    }

                    val body = response.body ?: throw Exception("Empty part body")
                    RandomAccessFile(file, "rw").use { raf ->
                        raf.seek(start)
                        val input = body.byteStream()
                        val buffer = ByteArray(8 * 1024 * 1024) // 8MB buffer for large part writes
                        var bytesRead: Int
                        var totalPartRead = 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            raf.write(buffer, 0, bytesRead)
                            totalPartRead += bytesRead
                            onProgress(totalPartRead)
                        }
                    }
                }
                return // Success
            } catch (e: Exception) {
                attempts++
                lastError = e
                Log.w(TAG, "Part download attempt $attempts/$maxAttempts failed ($start-$end): ${e.message}")
                if (attempts < maxAttempts) {
                    val delayMs = 2000L * attempts // Progressive delay
                    kotlinx.coroutines.delay(delayMs)
                }
            }
        }
        throw lastError ?: Exception("Unknown error in downloadPart")
    }

    private suspend fun downloadFileSingle(
        client: OkHttpClient,
        url: String,
        file: File,
        headers: Map<String, String> = emptyMap(),
        onProgress: suspend (Float) -> Unit
    ) {
        var attempts = 0
        val maxAttempts = 5
        var lastError: Exception? = null

        while (attempts < maxAttempts) {
            try {
                val existingSize = if (file.exists()) file.length() else 0L
                file.parentFile?.mkdirs()
                
                val request = Request.Builder()
                    .url(url)
                    .apply {
                        if (existingSize > 0) {
                            addHeader("Range", "bytes=$existingSize-")
                        }
                        headers.forEach { (key, value) -> addHeader(key, value) }
                    }
                    .build()
                    
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        if (response.code == 416) {
                            Log.w(TAG, "HTTP 416 Range Not Satisfiable — Local fragment may be larger than server file. Deleting and retrying...")
                            file.delete()
                            throw java.io.IOException("HTTP 416: Local fragment invalid, retrying from scratch")
                        }
                        // Special handling for 429 and 5xx: longer delay
                        if (response.code == 429 || response.code in 500..599) {
                            val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: (5L * (attempts + 1))
                            Log.w(TAG, "HTTP ${response.code} — waiting ${retryAfter}s before retry")
                            kotlinx.coroutines.delay(retryAfter * 1000)
                            throw java.io.IOException("HTTP ${response.code}: ${response.message}")
                        }
                        throw java.io.IOException("HTTP ${response.code}: ${response.message}")
                    }

                    val serverHash = response.header("X-Storyteller-Hash")
                    val body = response.body ?: throw Exception("Empty response body")
                    val contentLength = body.contentLength()
                    val isResuming = response.code == 206
                    val totalSize = if (isResuming) contentLength + existingSize else contentLength
                    
                    // If totalSize is unknown (contentLength == -1), report indeterminate progress
                    if (totalSize <= 0) {
                        onProgress(-1f)
                    }

                    FileOutputStream(file, isResuming).use { output ->
                        val input = body.byteStream()
                        val buffer = ByteArray(8 * 1024 * 1024) // 8MB buffer for large file writes
                        var bytesRead: Int
                        var totalBytesRead = if (isResuming) existingSize else 0L
                        var lastReportedBytes = totalBytesRead
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            
                            if (totalSize > 0) {
                                val progress = totalBytesRead.toFloat() / totalSize
                                // Report at most every 1% or 2MB to avoid flooding the UI
                                if (progress.isFinite() && (totalBytesRead > lastReportedBytes + (totalSize / 100) || totalBytesRead > lastReportedBytes + (2 * 1024 * 1024))) {
                                    lastReportedBytes = totalBytesRead
                                    onProgress(progress)
                                }
                            } else {
                                // Indeterminate progress: report -1f every 2MB to keep notification alive
                                if (totalBytesRead > lastReportedBytes + (2 * 1024 * 1024)) {
                                    lastReportedBytes = totalBytesRead
                                    onProgress(-1f)
                                }
                            }
                        }
                        output.flush()
                    }
                    
                    if (serverHash != null && file.exists()) {
                        verifyHash(file, serverHash)
                    }
                }
                return // Success
            } catch (e: Exception) {
                attempts++
                lastError = e
                Log.w(TAG, "Download attempt $attempts/$maxAttempts failed: ${e.message}")
                if (attempts < maxAttempts) {
                    // Exponential backoff: 2s, 4s, 8s, 16s
                    val delayMs = (2000L * (1L shl (attempts - 1))).coerceAtMost(32000L)
                    Log.d(TAG, "Waiting ${delayMs}ms before retry...")
                    kotlinx.coroutines.delay(delayMs)
                }
            }
        }
        throw lastError ?: Exception("Unknown error in downloadFileSingle")
    }

    /**
     * Verify file integrity using SHA-256 hash.
     */
    private fun verifyHash(file: File, expectedHash: String) {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val computedHash = file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            
            if (computedHash.equals(expectedHash, ignoreCase = true)) {
                Log.i(TAG, "Hash verification successful for ${file.name}")
            } else {
                Log.e(TAG, "Hash verification FAILED for ${file.name}! Expected: $expectedHash, Got: $computedHash")
                throw java.io.IOException("Hash verification failed: content is corrupted")
            }
        } catch (e: java.io.IOException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute or verify hash", e)
        }
    }

    /**
     * Validate a downloaded file by checking size and magic bytes.
     * Returns null on success, or a user-friendly error message on failure.
     */
    fun validateDownloadedFile(file: File, format: DownloadFormat): String? {
        if (!file.exists()) return "Downloaded file is missing"
        if (file.length() == 0L) return "Downloaded file is empty (0 bytes)"
        if (file.length() < 1024) return "Downloaded file is suspiciously small (${file.length()} bytes) — may be an error page"

        // Check magic bytes
        try {
            val header = ByteArray(12)
            file.inputStream().use { it.read(header) }
            val headerStr = String(header, 0, minOf(header.size, 5), Charsets.ISO_8859_1)

            when (format) {
                DownloadFormat.EBOOK, DownloadFormat.READALOUD -> {
                    // EPUB and ZIP both start with PK (0x50 0x4B)
                    if (header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
                        // Check if it's HTML
                        if (headerStr.contains("<") || headerStr.contains("html", ignoreCase = true)) {
                            return "Server returned an HTML error page instead of the file"
                        }
                        Log.w(TAG, "File magic bytes don't match ZIP/EPUB: ${header.take(4).map { "0x%02x".format(it) }}")
                        // Don't fail — some valid files have unusual headers
                    }
                }
                DownloadFormat.AUDIO -> {
                    // M4B/M4A/MP4 contain 'ftyp' at offset 4
                    val ftypCheck = String(header, 4, 4, Charsets.ISO_8859_1)
                    if (ftypCheck != "ftyp" && !headerStr.startsWith("ID3")) {
                        if (headerStr.contains("<") || headerStr.contains("html", ignoreCase = true)) {
                            return "Server returned an HTML error page instead of the audio file"
                        }
                        Log.w(TAG, "File magic bytes don't match audio: ${header.take(8).map { "0x%02x".format(it) }}")
                    }
                }
                DownloadFormat.PDF -> {
                    if (!headerStr.startsWith("%PDF")) {
                        if (headerStr.contains("<") || headerStr.contains("html", ignoreCase = true)) {
                            return "Server returned an HTML error page instead of the PDF"
                        }
                        Log.w(TAG, "File magic bytes don't match PDF: $headerStr")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Magic byte check failed: ${e.message}")
            // Don't fail validation for read errors
        }

        return null // Valid
    }

    /**
     * Unzip a file to a destination directory using ZipFile.
     * This is more robust than ZipInputStream for some archives.
     */
    fun unzipFile(zipFile: File, requestDestDir: File) {
        if (!requestDestDir.exists()) requestDestDir.mkdirs()
        
        java.util.zip.ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                
                val newFile = File(requestDestDir, entry.name)
                if (!newFile.canonicalPath.startsWith(requestDestDir.canonicalPath)) {
                    throw SecurityException("Zip Path Traversal detected: ${entry.name}")
                }
                
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(newFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    /**
     * Unzip from an input stream. Useful for streaming processing.
     */
    fun unzipStream(inputStream: InputStream, destDir: File) {
        if (!destDir.exists()) destDir.mkdirs()
        
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val newFile = File(destDir, entry.name)
                if (!newFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                    throw SecurityException("Zip Path Traversal detected: ${entry.name}")
                }
                
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { output ->
                        zip.copyTo(output)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    enum class DownloadFormat {
        AUDIO,
        EBOOK,
        PDF,
        READALOUD
    }
}
