package com.owlsoda.pageportal.core.database.dao

import androidx.room.*
import com.owlsoda.pageportal.core.database.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY title ASC")
    fun getAllBooks(): Flow<List<BookEntity>>
    
    @Query("SELECT * FROM books WHERE serverId = :serverId ORDER BY title ASC")
    fun getBooksByServer(serverId: Long): Flow<List<BookEntity>>
    
    @Query("""
        SELECT * FROM books 
        WHERE title LIKE '%' || :query || '%' 
           OR authors LIKE '%' || :query || '%'
        ORDER BY title ASC
    """)
    fun searchBooks(query: String): Flow<List<BookEntity>>
    
    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): BookEntity?
    
    @Query("SELECT * FROM books WHERE serverId = :serverId AND serviceBookId = :serviceBookId LIMIT 1")
    suspend fun getBookByServiceId(serverId: Long, serviceBookId: String): BookEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>)
    
    @Update
    suspend fun updateBook(book: BookEntity)
    
    @Delete
    suspend fun deleteBook(book: BookEntity)
    
    @Query("DELETE FROM books WHERE serverId = :serverId")
    suspend fun deleteBooksByServer(serverId: Long)
    
    @Query("SELECT COUNT(*) FROM books")
    suspend fun getBookCount(): Int
    
    @Query("SELECT COUNT(*) FROM books WHERE serverId = :serverId")
    suspend fun getBookCountByServer(serverId: Long): Int
    
    // Get books added recently
    @Query("SELECT * FROM books ORDER BY addedAt DESC LIMIT :limit")
    fun getRecentBooks(limit: Int = 20): Flow<List<BookEntity>>
    
    // Get books with ebook/audiobook capabilities
    @Query("SELECT * FROM books WHERE hasEbook = 1 ORDER BY title ASC")
    fun getEbooks(): Flow<List<BookEntity>>
    
    @Query("SELECT * FROM books WHERE hasAudiobook = 1 ORDER BY title ASC")
    fun getAudiobooks(): Flow<List<BookEntity>>
    
    @Query("SELECT * FROM books WHERE hasReadAloud = 1 ORDER BY title ASC")
    fun getReadAloudBooks(): Flow<List<BookEntity>>
    
    @Query("SELECT * FROM books WHERE unifiedBookId IS NULL")
    suspend fun getUnlinkedBooks(): List<BookEntity>
}
