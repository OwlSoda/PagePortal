package com.owlsoda.pageportal.reader.comic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Collections
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Parser for comic book archives (CBZ and CBR files).
 * Supports ZIP/CBZ via standard ZipFile and RAR/CBR via Junrar.
 */
class ComicParser {
    
    data class ComicBook(
        val path: String,
        val title: String,
        val pageCount: Int,
        val pages: List<ComicPage>
    )
    
    data class ComicPage(
        val index: Int,
        val name: String,
        val width: Int?,
        val height: Int?
    )
    
    private var currentFile: File? = null
    
    // Abstracted entries to handle both ZIP and RAR
    private interface ArchiveEntry {
        val name: String
        val isDirectory: Boolean
    }
    
    private class ZipArchiveEntry(val entry: ZipEntry) : ArchiveEntry {
        override val name: String get() = entry.name
        override val isDirectory: Boolean get() = entry.isDirectory
    }
    
    private class RarArchiveEntry(val header: FileHeader) : ArchiveEntry {
        override val name: String get() = header.fileName
        override val isDirectory: Boolean get() = header.isDirectory
    }
    
    // Archive handles
    private var zipFile: ZipFile? = null
    private var rarArchive: Archive? = null
    
    // Sorted list of entries to access
    private var sortedEntries: List<ArchiveEntry> = emptyList()
    
    // Map index to native entry object for fast retrieval
    private val entryMap = mutableMapOf<Int, Any>()
    
    /**
     * Open a comic archive (CBZ or CBR) and parse its structure.
     */
    suspend fun open(file: File): Result<ComicBook> = withContext(Dispatchers.IO) {
        try {
            close()
            
            if (!file.exists()) {
                return@withContext Result.failure(Exception("File not found: ${file.absolutePath}"))
            }
            
            currentFile = file
            val extension = file.extension.lowercase()
            
            when (extension) {
                "cbz", "zip" -> openCbz(file)
                "cbr", "rar" -> openCbr(file)
                else -> Result.failure(Exception("Unsupported format: $extension"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun openCbz(file: File): Result<ComicBook> {
        try {
            val zip = ZipFile(file)
            zipFile = zip
            
            val entries = zip.entries().asSequence()
                .map { ZipArchiveEntry(it) }
                .filter { isImage(it.name) && !it.isDirectory }
                .toList()
            
            processEntries(entries, file)
            
            // Map indices to ZipEntry objects
            sortedEntries.forEachIndexed { index, entry ->
                entryMap[index] = (entry as ZipArchiveEntry).entry
            }
            
            return Result.success(createComicBook(file, sortedEntries.size))
            
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    
    private fun openCbr(file: File): Result<ComicBook> {
        try {
            val rar = Archive(file)
            rarArchive = rar
            
            val headers = rar.fileHeaders
            val entries = headers.asSequence()
                .map { RarArchiveEntry(it) }
                .filter { isImage(it.name) && !it.isDirectory }
                .toList()
                
            processEntries(entries, file)
            
            // Map indices to FileHeader objects
            sortedEntries.forEachIndexed { index, entry ->
                entryMap[index] = (entry as RarArchiveEntry).header
            }
            
            return Result.success(createComicBook(file, sortedEntries.size))
            
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    
    private fun processEntries(entries: List<ArchiveEntry>, file: File) {
        if (entries.isEmpty()) {
            throw Exception("No images found in archive: ${file.name}")
        }
        
        sortedEntries = entries.sortedWith(NaturalOrderComparator())
    }
    
    private fun createComicBook(file: File, size: Int): ComicBook {
        val pages = sortedEntries.mapIndexed { index, entry ->
            ComicPage(
                index = index,
                name = entry.name.substringAfterLast('/'),
                width = null,
                height = null
            )
        }
        
        return ComicBook(
            path = file.absolutePath,
            title = file.nameWithoutExtension,
            pageCount = size,
            pages = pages
        )
    }
    
    private fun isImage(filename: String): Boolean {
        val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        return filename.substringAfterLast('.').lowercase() in imageExtensions
    }
    
    /**
     * Load a specific page as a Bitmap.
     */
    suspend fun loadPage(pageIndex: Int, maxWidth: Int? = null): Result<Bitmap> = 
        withContext(Dispatchers.IO) {
            try {
                if (pageIndex < 0 || pageIndex >= sortedEntries.size) {
                    return@withContext Result.failure(Exception("Page index out of bounds: $pageIndex"))
                }
                
                val rawBytes = getPageBytes(pageIndex) 
                    ?: return@withContext Result.failure(Exception("Failed to read page data"))
                
                // Decode options
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                
                // Decode bounds
                BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
                
                // Calculate sample size
                options.inJustDecodeBounds = false
                if (maxWidth != null && options.outWidth > maxWidth) {
                    options.inSampleSize = calculateSampleSize(options.outWidth, maxWidth)
                }
                options.inPreferredConfig = Bitmap.Config.RGB_565
                
                // Decode actual bitmap
                val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
                    ?: return@withContext Result.failure(Exception("Failed to decode image"))
                    
                Result.success(bitmap)
                
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    /**
     * Helper to get raw bytes for a page, handling both Zip and Rar complexities
     */
    private fun getPageBytes(pageIndex: Int): ByteArray? {
        val entryObj = entryMap[pageIndex] ?: return null
        
        return when {
            zipFile != null && entryObj is ZipEntry -> {
                zipFile?.getInputStream(entryObj)?.use { it.readBytes() }
            }
            rarArchive != null && entryObj is FileHeader -> {
                val rar = rarArchive ?: return null
                val os = ByteArrayOutputStream()
                rar.extractFile(entryObj, os)
                os.toByteArray()
            }
            else -> null
        }
    }
    
    /**
     * Close the comic file and release resources.
     */
    fun close() {
        try {
            zipFile?.close()
            rarArchive?.close()
        } catch (e: Exception) {
            // Ignore
        }
        zipFile = null
        rarArchive = null
        sortedEntries = emptyList()
        entryMap.clear()
        currentFile = null
    }
    
    private fun calculateSampleSize(width: Int, maxWidth: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > maxWidth * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }
    
    /**
     * Natural order comparator for sorting filenames properly.
     */
    private class NaturalOrderComparator : Comparator<ArchiveEntry> {
        override fun compare(e1: ArchiveEntry, e2: ArchiveEntry): Int {
            return compareNatural(e1.name, e2.name)
        }
        
        private fun compareNatural(s1: String, s2: String): Int {
            var i1 = 0
            var i2 = 0
            
            while (i1 < s1.length && i2 < s2.length) {
                val c1 = s1[i1]
                val c2 = s2[i2]
                
                if (c1.isDigit() && c2.isDigit()) {
                    var num1 = 0
                    while (i1 < s1.length && s1[i1].isDigit()) {
                        num1 = num1 * 10 + (s1[i1] - '0')
                        i1++
                    }
                    
                    var num2 = 0
                    while (i2 < s2.length && s2[i2].isDigit()) {
                        num2 = num2 * 10 + (s2[i2] - '0')
                        i2++
                    }
                    
                    if (num1 != num2) return num1 - num2
                } else {
                    val cmp = c1.lowercaseChar().compareTo(c2.lowercaseChar())
                    if (cmp != 0) return cmp
                    i1++
                    i2++
                }
            }
            
            return s1.length - s2.length
        }
    }
    
    companion object {
        val SUPPORTED_EXTENSIONS = setOf("cbz", "cbr", "zip", "rar")
        
        fun isSupported(filename: String): Boolean {
            return filename.substringAfterLast('.').lowercase() in SUPPORTED_EXTENSIONS
        }
    }
}
