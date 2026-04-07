package com.owlsoda.pageportal.data.repository

import com.google.gson.Gson
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.entity.BookEntity
import com.owlsoda.pageportal.core.database.entity.CollectionEntity
import com.owlsoda.pageportal.core.matching.MatchingEngine
import com.owlsoda.pageportal.services.ServiceBook
import com.owlsoda.pageportal.services.ServiceManager
import javax.inject.Inject
import javax.inject.Singleton
import com.owlsoda.pageportal.data.repository.SyncRepository
import com.owlsoda.pageportal.util.LogManager

@Singleton
class LibraryRepository @Inject constructor(
    private val serviceManager: ServiceManager,
    private val bookDao: BookDao,
    private val collectionDao: com.owlsoda.pageportal.core.database.dao.CollectionDao,
    private val matchingEngine: MatchingEngine,
    private val progressDao: com.owlsoda.pageportal.core.database.dao.ProgressDao,
    private val syncRepository: SyncRepository
) {
    private fun log(message: String) {
        LogManager.log("LibraryRepository", message)
    }
    
    suspend fun syncLibrary(): Result<Unit> {
        log("syncLibrary() started")
        return try {
            val results = try {
                serviceManager.getAllBooks()
            } catch (e: Exception) {
                // If no servers configured or all failed, return empty list
                emptyList()
            }
            
            if (results.isEmpty()) {
                return Result.success(Unit) // No servers to sync, that's OK
            }
            
            val gson = Gson()
            val booksToInsert = mutableListOf<BookEntity>()
            
            for ((server, books) in results) {
                try {
                    for (searchBook in books) {
                        // Check if exists to preserve manual links or existing unifiedId
                        val existing = bookDao.getBookByServiceId(server.id, searchBook.serviceId)
                        
                        val entity = BookEntity(
                            id = existing?.id ?: 0,
                            serverId = server.id,
                            serviceBookId = searchBook.serviceId,
                            title = searchBook.title,
                            authors = gson.toJson(searchBook.authors),
                            narrators = gson.toJson(searchBook.narrators),
                            description = searchBook.description,
                            coverUrl = searchBook.coverUrl,
                            audiobookCoverUrl = searchBook.audiobookCoverUrl,
                            series = searchBook.series,
                            seriesIndex = searchBook.seriesIndex?.toString(),
                            hasEbook = searchBook.hasEbook,
                            hasAudiobook = searchBook.hasAudiobook,
                            hasReadAloud = searchBook.hasReadAloud,
                            duration = searchBook.duration,
                            publishedYear = searchBook.publishedYear,
                            tags = gson.toJson(searchBook.tags),
                            isbn = searchBook.isbn,
                            asin = searchBook.asin,
                            updatedAt = System.currentTimeMillis(),
                            
                            // Critical: Preserve linking state
                            unifiedBookId = existing?.unifiedBookId,
                            isManuallyLinked = existing?.isManuallyLinked ?: false,
                            
                            // Preserve download state
                            downloadStatus = existing?.downloadStatus ?: "NONE",
                            localFilePath = existing?.localFilePath,
                            downloadProgress = existing?.downloadProgress ?: 0f
                        )
                        booksToInsert.add(entity)
                    }
                } catch (e: Exception) {
                    // Skip this server if processing fails
                    continue
                }
                
                // Process Collections for this server
                try {
                    val collectionsMap = mutableMapOf<String, Pair<String, MutableList<String>>>() // ID -> (Name, BookIDs)
                    
                    for (searchBook in books) {
                        for (collection in searchBook.collections) {
                            val entry = collectionsMap.getOrPut(collection.id) { collection.name to mutableListOf() }
                            entry.second.add(searchBook.serviceId)
                        }
                    }
                    
                    // Fetch existing collections to preserve IDs
                    val existingCollections = collectionDao.getCollectionsList(server.id)
                    val existingMap = existingCollections.associate { it.serviceId to it.id }
                    
                    val collectionEntities = collectionsMap.map { (serviceId, data) ->
                        CollectionEntity(
                            id = existingMap[serviceId] ?: 0,
                            serverId = server.id,
                            serviceId = serviceId,
                            name = data.first,
                            bookIds = gson.toJson(data.second),
                            lastSync = System.currentTimeMillis()
                        )
                    }
                    
                    if (collectionEntities.isNotEmpty()) {
                        collectionDao.insertCollections(collectionEntities)
                    }
                } catch (e: Exception) {
                    // Log collection sync failure but continue
                }
            }
            
            // Batch insert (REPLACE strategy will overwrite but we preserved ID and unifiedId)
            if (booksToInsert.isNotEmpty()) {
                android.util.Log.d("LibraryRepository", "Inserting ${booksToInsert.size} books into database")
                bookDao.insertBooks(booksToInsert)
            }
            
            // Run matching
            try {
                android.util.Log.d("LibraryRepository", "Running matching engine")
                matchingEngine.runMatching()
            } catch (e: Exception) {
                android.util.Log.e("LibraryRepository", "Matching failed", e)
            }
            
            log("Sync successful (${booksToInsert.size} books)")
            Result.success(Unit)
        } catch (e: Exception) {
            log("Sync failed: ${e.message}")
            Result.failure(Exception("Failed to sync library: ${e.message}", e))
        }
    }

    suspend fun syncProgress(bookId: Long): Result<Unit> {
        return syncRepository.syncProgress(bookId)
    }
    
    suspend fun syncPendingProgress(): Result<Int> {
        return syncRepository.syncAll()
    }

    suspend fun triggerReadAloud(bookId: Long, restart: Boolean = false): Result<Unit> {
        return try {
            val book = bookDao.getBookById(bookId) ?: return Result.failure(Exception("Book not found"))
            val service = serviceManager.getService(book.serverId)
            if (service !is com.owlsoda.pageportal.services.storyteller.StorytellerService) {
                return Result.failure(Exception("Action only supported for Storyteller servers"))
            }

            val result = (service as com.owlsoda.pageportal.services.storyteller.StorytellerService).triggerReadAloudProcessing(book.serviceBookId, restart)
            if (result.isSuccess) {
                // Update local status to reflect it's being processed
                val updatedBook = book.copy(
                    processingStatus = "processing",
                    processingStage = if (restart) "restarting" else "queued",
                    updatedAt = System.currentTimeMillis()
                )
                bookDao.updateBook(updatedBook)
                Result.success(Unit)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error triggering sync"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateBookMetadata(bookId: Long, metadata: com.owlsoda.pageportal.services.MetadataUpdate): Result<Unit> {
        return try {
            val book = bookDao.getBookById(bookId) ?: return Result.failure(Exception("Book not found"))
            val service = serviceManager.getService(book.serverId) ?: return Result.failure(Exception("Service not found"))
            
            val result = service.updateMetadata(book.serviceBookId, metadata)
            if (result.isSuccess) {
                val updatedServiceBook = result.getOrThrow()
                val gson = Gson()
                val updatedBook = book.copy(
                    title = updatedServiceBook.title,
                    authors = gson.toJson(updatedServiceBook.authors),
                    narrators = gson.toJson(updatedServiceBook.narrators),
                    description = updatedServiceBook.description,
                    coverUrl = updatedServiceBook.coverUrl,
                    audiobookCoverUrl = updatedServiceBook.audiobookCoverUrl,
                    series = updatedServiceBook.series,
                    seriesIndex = updatedServiceBook.seriesIndex?.toString(),
                    tags = gson.toJson(updatedServiceBook.tags),
                    isbn = updatedServiceBook.isbn,
                    asin = updatedServiceBook.asin,
                    updatedAt = System.currentTimeMillis()
                )
                bookDao.updateBook(updatedBook)
                Result.success(Unit)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error updating metadata"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshBookMetadata(bookId: Long): Result<BookEntity> {
        log("refreshBookMetadata(bookId=$bookId) started")
        return try {
            val book = bookDao.getBookById(bookId) ?: return Result.failure(Exception("Book not found"))
            val service = serviceManager.getService(book.serverId) ?: return Result.failure(Exception("Service not found"))
            
            val details = service.getBookDetails(book.serviceBookId)
            val updatedBook = book.copy(
                processingStatus = details.readAloudStatus,
                processingStage = details.readAloudStage,
                processingProgress = details.readAloudProgress,
                hasEbook = details.files.any { it.mimeType.contains("epub") },
                hasAudiobook = details.files.any { it.mimeType.contains("audio") || it.filename.endsWith(".m4b") },
                hasReadAloud = details.readAloudStatus?.uppercase() == "COMPLETED" || details.readAloudStatus?.uppercase() == "READY" || details.readAloudStatus?.uppercase() == "ALIGNED",
                updatedAt = System.currentTimeMillis()
            )
            bookDao.updateBook(updatedBook)
            Result.success(updatedBook)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
