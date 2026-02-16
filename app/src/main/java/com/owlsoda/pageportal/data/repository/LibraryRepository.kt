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

@Singleton
class LibraryRepository @Inject constructor(
    private val serviceManager: ServiceManager,
    private val bookDao: BookDao,
    private val collectionDao: com.owlsoda.pageportal.core.database.dao.CollectionDao,
    private val matchingEngine: MatchingEngine,
    private val progressDao: com.owlsoda.pageportal.core.database.dao.ProgressDao
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
                bookDao.insertBooks(booksToInsert)
            }
            
            // Run matching
            try {
                matchingEngine.runMatching()
            } catch (e: Exception) {
                // Matching failure shouldn't fail entire sync
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to sync library: ${e.message}", e))
        }
    }

    suspend fun syncProgress(bookId: Long): Result<Unit> {
        return try {
            val progress = progressDao.getProgressByBookId(bookId) ?: return Result.success(Unit)
            val book = bookDao.getBookById(bookId) ?: return Result.failure(Exception("Book not found"))
            val service = serviceManager.getService(book.serverId) ?: return Result.failure(Exception("Service not found"))
            
            // Map Entity to Service Model
            val readingProgress = com.owlsoda.pageportal.services.ReadingProgress(
                bookId = book.serviceBookId,
                currentPosition = progress.currentPosition, // ms
                currentChapter = progress.currentChapter,
                percentComplete = progress.percentComplete,
                lastUpdated = progress.lastUpdated
            )
            
            service.updateProgress(book.serviceBookId, readingProgress)
            progressDao.markSynced(bookId)
            
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    suspend fun syncPendingProgress(): Result<Int> {
        return try {
            val pending = progressDao.getUnsyncedProgress()
            if (pending.isEmpty()) return Result.success(0)
            
            var successCount = 0
            for (progress in pending) {
                // Try to sync each one
                val result = syncProgress(progress.bookId)
                if (result.isSuccess) successCount++
            }
            Result.success(successCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
