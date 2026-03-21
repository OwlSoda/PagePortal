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
        val indexPart = book.seriesIndex?.let { 
            it.toInt().toString().padStart(2, '0') 
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

    /**
     * Get the file path for a specific format.
     */
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
     * Download a file with support for:
     * - Parallel multi-part downloads (for speed)
     * - Resumable downloads (Range header)
     * - Progress callbacks
     * - SHA-256 hash verification
     * 
     * @param client OkHttpClient
     * @param url The download URL
     * @param file The destination file
     * @param numParts Number of parallel parts to download
     */
    suspend fun downloadFile(
        client: OkHttpClient,
        url: String,
        file: File,
        headers: Map<String, String> = emptyMap(),
        numParts: Int = 1,
        onProgress: suspend (Float) -> Unit
    ) = coroutineScope {
        // Force single stream for small files (< 10MB) or if only 1 part requested
        val useSingleStream = numParts <= 1
        
        if (useSingleStream) {
            downloadFileSingle(client, url, file, headers, onProgress)
            return@coroutineScope
        }

        file.parentFile?.mkdirs()
        
        // 1. Get total size and check if server supports ranges
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

        if (contentLength <= 10 * 1024 * 1024 || !acceptsRanges) {
            Log.d(TAG, "Server doesn't support ranges or file too small ($contentLength). Using single stream.")
            downloadFileSingle(client, url, file, headers, onProgress)
            return@coroutineScope
        }

        Log.d(TAG, "Starting multi-part download ($numParts parts, size=$contentLength)")
        
        val partSize = contentLength / numParts
        val progressMap = java.util.concurrent.ConcurrentHashMap<Int, Long>()
        var lastTotalReported = 0L

        // Initialize file size
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
                    // Guard against progress jumping back during retries
                    val currentStored = progressMap[i] ?: 0L
                    if (bytesRead > currentStored) {
                        progressMap[i] = bytesRead
                        val totalRead = progressMap.values.sum()
                        if (totalRead > lastTotalReported + (contentLength / 200)) { // 0.5% increments
                            lastTotalReported = totalRead
                            onProgress(totalRead.toFloat() / contentLength)
                        }
                    }
                }
            }
        }.awaitAll()
        
        onProgress(1.0f)
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
                        val buffer = ByteArray(1024 * 1024) // 1MB buffer
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
        val maxAttempts = 3
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
                            onProgress(1f)
                            return
                        }
                        throw Exception("HTTP Error ${response.code}: ${response.message}")
                    }

                    val serverHash = response.header("X-Storyteller-Hash")
                    val body = response.body ?: throw Exception("Empty response body")
                    val contentLength = body.contentLength()
                    val isResuming = response.code == 206
                    val totalSize = if (isResuming) contentLength + existingSize else contentLength
                    
                    FileOutputStream(file, isResuming).use { output ->
                        val input = body.byteStream()
                        val buffer = ByteArray(2 * 1024 * 1024) // 2MB buffer for single stream
                        var bytesRead: Int
                        var totalBytesRead = if (isResuming) existingSize else 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (totalSize > 0) {
                                onProgress(totalBytesRead.toFloat() / totalSize)
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
                Log.w(TAG, "Single download attempt $attempts failed: ${e.message}")
                if (attempts < maxAttempts) {
                    kotlinx.coroutines.delay(1000L * attempts)
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify hash", e)
        }
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
