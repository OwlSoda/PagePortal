package com.owlsoda.pageportal.util

import android.util.Log
import com.owlsoda.pageportal.core.database.entity.BookEntity
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

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
        val authorPart = sanitize(book.authors.ifBlank { "Unknown Author" })
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
            DownloadFormat.READALOUD -> "$baseFileName (readaloud).epub"
        }
        return File(bookDir, fileName)
    }

    /**
     * Download a file with support for:
     * - Resumable downloads (Range header)
     * - Progress callbacks
     * - SHA-256 hash verification (if server provides X-Storyteller-Hash header)
     * 
     * @param client OkHttpClient configured with auth headers
     * @param url The download URL
     * @param file The destination file
     * @param onProgress Callback for progress updates (0.0 to 1.0)
     */
    suspend fun downloadFile(
        client: OkHttpClient,
        url: String,
        file: File,
        onProgress: (Float) -> Unit
    ) {
        var serverHash: String? = null
        val existingSize = if (file.exists()) file.length() else 0L
        
        // Ensure parent directory exists
        file.parentFile?.mkdirs()
        
        val request = Request.Builder()
            .url(url)
            .apply {
                // Request resume from existing position if file exists
                if (existingSize > 0) {
                    addHeader("Range", "bytes=$existingSize-")
                    Log.d(TAG, "Resuming download from byte $existingSize")
                }
            }
            .build()
            
        client.newCall(request).execute().use { response ->
            // 206 = Partial Content (resume successful)
            // 416 = Range Not Satisfiable (file already complete)
            if (!response.isSuccessful && response.code != 206) {
                if (response.code == 416) {
                    Log.d(TAG, "File already complete (416)")
                    onProgress(1f)
                    return
                }
                throw Exception("HTTP Error ${response.code}: ${response.message}")
            }

            // Check for Storyteller hash header for verification
            serverHash = response.header("X-Storyteller-Hash")
            if (serverHash != null) {
                Log.i(TAG, "Server provided hash for verification: $serverHash")
            }
            
            val body = response.body ?: throw Exception("Empty response body")
            val contentLength = body.contentLength()
            val totalSize = if (response.code == 206) contentLength + existingSize else contentLength
            
            // Append if resuming, otherwise overwrite
            val append = response.code == 206
            FileOutputStream(file, append).use { output ->
                val input = body.byteStream()
                val buffer = ByteArray(128 * 1024) // 128KB buffer
                var bytesRead: Int
                var totalBytesRead = existingSize
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (totalSize > 0) {
                        onProgress(totalBytesRead.toFloat() / totalSize)
                    }
                }
            }
        }

        // Verify hash if server provided one
        if (serverHash != null && file.exists()) {
            verifyHash(file, serverHash!!)
        }
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

    enum class DownloadFormat {
        AUDIO,
        EBOOK,
        READALOUD
    }
}
