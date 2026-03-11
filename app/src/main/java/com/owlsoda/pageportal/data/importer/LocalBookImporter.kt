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
                "mp3", "m4b", "aac" -> parseAudiobook(destinationFile)
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
                hasAudiobook = extension in listOf("mp3", "m4b", "aac"),
                hasReadAloud = bookMetadata.hasMediaOverlays,

                // Download status (it's local, so it's "downloaded")
                downloadStatus = "COMPLETED",
                isEbookDownloaded = extension == "epub",
                isAudiobookDownloaded = extension in listOf("mp3", "m4b", "aac"),
                isReadAloudDownloaded = bookMetadata.hasMediaOverlays,
                
                localFilePath = destinationFile.absolutePath,
                addedAt = System.currentTimeMillis()
            )

            // 5. Insert into DB (Unified + Book)
            val unifiedBook = UnifiedBookEntity(
                title = bookEntity.title,
                author = bookEntity.authors,
                coverUrl = bookEntity.coverUrl,
                lastUpdated = System.currentTimeMillis()
            )
            val unifiedId = unifiedBookDao.insert(unifiedBook)
            
            val bookId = bookDao.insertBook(bookEntity.copy(unifiedBookId = unifiedId))
            
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
        val hasMediaOverlays: Boolean = false
    )

    private suspend fun parseEpub(file: File): ParsedMetadata {
        val parser = EpubParser()
        val result = parser.parse(file).getOrThrow()
        parser.close()
        
        // Extract cover to accessible file if needed?
        // For now, if coverImage is relative path inside ZIP, we can't display it easily in Coil 
        // without a custom Fetcher or extracting it.
        // TODO: Extract cover image to cache dir
        
        return ParsedMetadata(
            title = result.title,
            author = result.author,
            coverUrl = null, // TODO: Handle cover extraction
            hasMediaOverlays = result.hasMediaOverlays
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
            
            // Extract cover art
            // val art = retriever.embeddedPicture
            // TODO: Save art to file
            
            return ParsedMetadata(
                title = title,
                author = artist,
                duration = durationSec,
                coverUrl = null
            )
        } catch (e: Exception) {
            return ParsedMetadata(file.nameWithoutExtension, "Unknown", null, null)
        } finally {
            retriever.release()
        }
    }
}
