package com.owlsoda.pageportal.core.matching

import com.google.gson.Gson
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.dao.UnifiedBookDao
import com.owlsoda.pageportal.core.database.entity.BookEntity
import com.owlsoda.pageportal.core.database.entity.UnifiedBookEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatchingEngine @Inject constructor(
    private val bookDao: BookDao,
    private val unifiedBookDao: UnifiedBookDao
) {
    suspend fun runMatching() {
        // 1. Get unlinked books
        val unlinkedBooks = bookDao.getUnlinkedBooks()
        if (unlinkedBooks.isEmpty()) return
        
        // 2. Get snapshot of existing UnifiedBooks
        val existingUnifiedBooks = unifiedBookDao.getUnifiedBooksSnapshot().toMutableList()
        val gson = Gson()
        
        // 3. Iterate and match
        for (book in unlinkedBooks) {
            // Skip if manually unlinked? defined by isManuallyLinked=true AND unifiedBookId=null?
            // If isManuallyLinked is true, we generally assume the user set the state. 
            // If the user unlinked it, unifiedBookId is null.
            if (book.isManuallyLinked) continue
            
            val bookAuthors = parseAuthors(book.authors, gson)
            
            var bestMatch: UnifiedBookEntity? = null
            var bestScore = 0.0f
            
            // Search in existing (and newly created in this session)
            for (unified in existingUnifiedBooks) {
                val unifiedAuthors = listOf(unified.author) // UnifiedEntity has single string author for display??
                // Wait, UnifiedBookEntity should probably have list of authors or normalized string?
                // Current entity: val author: String.
                // We'll treat it as valid author string.
                
                // We compare against the UnifiedBook's metadata. 
                // Ideally UnifiedBook aggregates authors.
                
                val score = TitleMatcher.calculateMatchScore(
                    book.title,
                    bookAuthors,
                    unified.title,
                    listOf(unified.author)
                )
                
                if (score > bestScore) {
                    bestScore = score
                    bestMatch = unified
                }
            }
            
            if (bestMatch != null && bestScore > 0.85f) {
                // Link to existing
                bookDao.updateBook(book.copy(unifiedBookId = bestMatch.id))
            } else {
                // Create new UnifiedBook
                val newUnified = UnifiedBookEntity(
                    title = book.title, // Use this book's title as canonical for now
                    author = bookAuthors.firstOrNull() ?: "Unknown",
                    coverUrl = book.coverUrl,
                    audiobookCoverUrl = book.audiobookCoverUrl,
                    description = book.description,
                    lastUpdated = System.currentTimeMillis()
                )
                
                val newId = unifiedBookDao.insert(newUnified)
                val inserted = newUnified.copy(id = newId)
                existingUnifiedBooks.add(inserted)
                
                // Link
                bookDao.updateBook(book.copy(unifiedBookId = newId))
            }
        }
    }
    
    private fun parseAuthors(json: String, gson: Gson): List<String> {
        return try {
            gson.fromJson(json, Array<String>::class.java).toList()
        } catch (e: Exception) {
            listOf(json) // Fallback
        }
    }
}
