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

@Singleton
class LibraryRepository @Inject constructor(
    private val serviceManager: ServiceManager,
    private val bookDao: BookDao,
    private val collectionDao: com.owlsoda.pageportal.core.database.dao.CollectionDao,
    private val matchingEngine: MatchingEngine,
    private val progressDao: com.owlsoda.pageportal.core.database.dao.ProgressDao,
    private val syncRepository: SyncRepository
) {
    
    suspend fun syncLibrary(): Result<Unit> {
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
            
            android.util.Log.i("LibraryRepository", "Sync successful")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("LibraryRepository", "Sync failed", e)
            Result.failure(Exception("Failed to sync library: ${e.message}", e))
        }
    }

    suspend fun syncProgress(bookId: Long): Result<Unit> {
        return syncRepository.syncProgress(bookId)
    }
    
    suspend fun syncPendingProgress(): Result<Int> {
        return syncRepository.syncAll()
    }

    suspend fun triggerReadAloud(bookId: Long): Result<Unit> {
        return try {
            val book = bookDao.getBookById(bookId) ?: return Result.failure(Exception("Book not found"))
            val service = serviceManager.getService(book.serverId)
            if (service !is com.owlsoda.pageportal.services.storyteller.StorytellerService) {
                return Result.failure(Exception("Action only supported for Storyteller servers"))
            }

            val result = service.triggerReadAloudProcessing(book.serviceBookId)
            if (result.isSuccess) {
                // Update local status to reflect it's being processed
                val updatedBook = book.copy(
                    processingStatus = "processing",
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
}
