package com.owlsoda.pageportal.data.repository

import com.google.gson.Gson
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.entity.BookEntity
import com.owlsoda.pageportal.core.matching.MatchingEngine
import com.owlsoda.pageportal.services.ServiceBook
import com.owlsoda.pageportal.services.ServiceManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val serviceManager: ServiceManager,
    private val bookDao: BookDao,
    private val matchingEngine: MatchingEngine
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
                            isManuallyLinked = existing?.isManuallyLinked ?: false
                        )
                        booksToInsert.add(entity)
                    }
                } catch (e: Exception) {
                    // Skip this server if processing fails
                    continue
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
}
