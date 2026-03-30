package com.owlsoda.pageportal.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.dao.ServerDao
import com.owlsoda.pageportal.core.database.dao.UnifiedBookDao
import com.owlsoda.pageportal.core.database.entity.BookEntity
import com.owlsoda.pageportal.core.database.entity.ServerEntity
import com.owlsoda.pageportal.core.database.entity.UnifiedBookEntity
import com.owlsoda.pageportal.reader.epub.EpubParser
import com.owlsoda.pageportal.services.ServiceType
import com.owlsoda.pageportal.util.DownloadUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalBookImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val unifiedBookDao: UnifiedBookDao,
    private val serverDao: ServerDao
) {

    suspend fun importBook(uri: Uri): Result<BookEntity> = withContext(Dispatchers.IO) {
        try {
            // 1. Ensure Local Server exists
            val localServerId = getOrCreateLocalServer()

            // 2. Resolve file name and copy to internal storage
            val fileName = getFileName(uri) ?: "unknown_book_${System.currentTimeMillis()}"
            val destinationFile = File(context.filesDir, "imported/$fileName")
            destinationFile.parentFile?.mkdirs()

            context.contentResolver.openInputStream(uri)?.use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Failed to read file"))

            // 3. Determine type and Parse Metadata
            val extension = destinationFile.extension.lowercase()
            val bookMetadata = when (extension) {
                "epub" -> parseEpub(destinationFile)
                "mp3", "m4b", "aac", "mp4", "m4a" -> parseAudiobook(destinationFile)
                else -> return@withContext Result.failure(Exception("Unsupported file type: $extension"))
            }

            // 4. Create BookEntity
            val bookEntity = BookEntity(
                serverId = localServerId,
                serviceBookId = destinationFile.absolutePath, // Use local path as ID
                title = bookMetadata.title,
                authors = bookMetadata.author, // Simple string for now
                duration = bookMetadata.duration, // in seconds
                coverUrl = bookMetadata.coverUrl, // Local path or null
                
                // Format flags
                hasEbook = extension == "epub",
                hasAudiobook = extension in listOf("mp3", "m4b", "aac", "mp4", "m4a"),
                hasReadAloud = bookMetadata.hasMediaOverlays,

                // Download status (it's local, so it's "downloaded")
                downloadStatus = "COMPLETED",
                isEbookDownloaded = extension == "epub",
                isAudiobookDownloaded = extension in listOf("mp3", "m4b", "aac", "mp4", "m4a"),
                isReadAloudDownloaded = bookMetadata.hasMediaOverlays,
                
                localFilePath = destinationFile.absolutePath,
                addedAt = System.currentTimeMillis(),
                description = bookMetadata.description,
                series = bookMetadata.series,
                seriesIndex = bookMetadata.seriesIndex,
                tags = bookMetadata.tags.joinToString(",")
            )
            
            // 5. Insert into DB (Unified + Book)
            val unifiedBook = UnifiedBookEntity(
                title = bookEntity.title,
                authors = bookEntity.authors,
                coverUrl = bookEntity.coverUrl,
                lastUpdated = System.currentTimeMillis()
            )
            val unifiedId = unifiedBookDao.insert(unifiedBook)
            
            val bookId = bookDao.insertBook(bookEntity.copy(unifiedBookId = unifiedId))
            
            // 6. Proactive Unzip for ReadAloud (Now we have the real ID)
            if (bookEntity.hasReadAloud) {
                try {
                    val cacheDir = File(context.cacheDir, "readaloud/$bookId")
                    if (!cacheDir.exists() || cacheDir.listFiles()?.isEmpty() == true) {
                        DownloadUtils.unzipFile(destinationFile, cacheDir)
                        android.util.Log.i("LocalBookImporter", "Proactively unzipped ReadAloud for book $bookId")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LocalBookImporter", "Failed to proactive unzip", e)
                }
            }
            
            Result.success(bookEntity.copy(id = bookId))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private suspend fun getOrCreateLocalServer(): Long {
        val existing = serverDao.getServerByUrlAndType("local://", ServiceType.LOCAL.name)
        if (existing != null) return existing.id

        val newServer = ServerEntity(
            serviceType = ServiceType.LOCAL.name,
            displayName = "Local Library",
            serverUrl = "local://",
            username = "User",
            authToken = null,
            userId = "local_user"
        )
        return serverDao.insertServer(newServer)
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    data class ParsedMetadata(
        val title: String,
        val author: String,
        val duration: Long? = null,
        val coverUrl: String?,
        val hasMediaOverlays: Boolean = false,
        val description: String? = null,
        val series: String? = null,
        val seriesIndex: String? = null,
        val tags: List<String> = emptyList()
    )

    private suspend fun parseEpub(file: File): ParsedMetadata {
        val parser = EpubParser()
        val result = parser.parse(file).getOrThrow()
        
        // Extract cover image from EPUB ZIP to a local file
        var coverUrl: String? = null
        if (result.coverImage != null) {
            try {
                val coverBytes = parser.getInputStream(result.coverImage)?.readBytes()
                if (coverBytes != null && coverBytes.isNotEmpty()) {
                    val extension = result.coverImage.substringAfterLast('.', "jpg")
                    val coverFile = saveCoverImage(coverBytes, file.nameWithoutExtension, extension)
                    coverUrl = android.net.Uri.fromFile(coverFile).toString()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        parser.close()
        
        return ParsedMetadata(
            title = result.title,
            author = result.author,
            coverUrl = coverUrl,
            hasMediaOverlays = result.hasMediaOverlays,
            description = result.description,
            series = result.series,
            seriesIndex = result.seriesIndex,
            tags = result.tags
        )
    }

    private fun parseAudiobook(file: File): ParsedMetadata {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension
            val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val durationSec = durationMs?.let { it / 1000 }
            
            // Extract embedded cover art
            var coverUrl: String? = null
            val art = retriever.embeddedPicture
            if (art != null && art.isNotEmpty()) {
                try {
                    val coverFile = saveCoverImage(art, file.nameWithoutExtension, "jpg")
                    coverUrl = android.net.Uri.fromFile(coverFile).toString()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            return ParsedMetadata(
                title = title,
                author = artist,
                duration = durationSec,
                coverUrl = coverUrl
            )
        } catch (e: Exception) {
            return ParsedMetadata(file.nameWithoutExtension, "Unknown", null, null)
        } finally {
            retriever.release()
        }
    }
    
    private fun saveCoverImage(bytes: ByteArray, bookName: String, extension: String): File {
        val coversDir = File(context.filesDir, "covers")
        coversDir.mkdirs()
        val coverFile = File(coversDir, "${bookName}_cover.$extension")
        coverFile.writeBytes(bytes)
        return coverFile
    }
}
