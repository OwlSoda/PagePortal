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
    
    @Query("SELECT * FROM books WHERE authors LIKE '%' || :author || '%' ORDER BY title ASC")
    fun getBooksByAuthor(author: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE authors LIKE '%' || :author || '%' AND serverId IN (:serverIds) ORDER BY title ASC")
    fun getBooksByAuthorAndServerIds(author: String, serverIds: List<Long>): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE series = :series ORDER BY seriesIndex ASC, title ASC")
    fun getBooksBySeries(series: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE series = :series AND serverId IN (:serverIds) ORDER BY seriesIndex ASC, title ASC")
    fun getBooksBySeriesAndServerIds(series: String, serverIds: List<Long>): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE tags LIKE '%' || :tag || '%' ORDER BY title ASC")
    fun getBooksByTag(tag: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE tags LIKE '%' || :tag || '%' AND serverId IN (:serverIds) ORDER BY title ASC")
    fun getBooksByTagAndServerIds(tag: String, serverIds: List<Long>): Flow<List<BookEntity>>
    
    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeBook(id: Long): Flow<BookEntity?>
    
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
    
    @Query("UPDATE books SET downloadStatus = :status, downloadProgress = :progress, localFilePath = :localPath, downloadError = :error WHERE id = :bookId")
    suspend fun updateDownloadStatus(bookId: Long, status: String, progress: Float, localPath: String?, error: String? = null)
    
    // Per-format download updates
    @Query("UPDATE books SET isAudiobookDownloaded = :downloaded, localFilePath = :path WHERE id = :bookId")
    suspend fun updateAudiobookDownloaded(bookId: Long, downloaded: Boolean, path: String?)
    
    @Query("UPDATE books SET isEbookDownloaded = :downloaded, localFilePath = :path WHERE id = :bookId")
    suspend fun updateEbookDownloaded(bookId: Long, downloaded: Boolean, path: String?)
    
    @Query("UPDATE books SET isReadAloudDownloaded = :downloaded, localFilePath = :path WHERE id = :bookId")
    suspend fun updateReadAloudDownloaded(bookId: Long, downloaded: Boolean, path: String?)
    
    // Update download URLs
    @Query("UPDATE books SET audiobookUrl = :url WHERE id = :bookId")
    suspend fun updateAudiobookUrl(bookId: Long, url: String?)
    
    @Query("UPDATE books SET ebookUrl = :url WHERE id = :bookId")  
    suspend fun updateEbookUrl(bookId: Long, url: String?)
    
    @Query("UPDATE books SET syncedUrl = :url WHERE id = :bookId")
    suspend fun updateSyncedUrl(bookId: Long, url: String?)
    
    // Get downloaded books
    @Query("SELECT * FROM books WHERE isAudiobookDownloaded = 1 OR isEbookDownloaded = 1 OR isReadAloudDownloaded = 1 ORDER BY title")
    fun getDownloadedBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE serverId = :serverId AND serviceBookId IN (:serviceIds) ORDER BY title ASC")
    fun getBooksByServiceIds(serverId: Long, serviceIds: List<String>): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE processingStatus IN ('queued', 'processing', 'restarting', 'aligning') ORDER BY title ASC")
    fun getActiveProcessingBooks(): Flow<List<BookEntity>>
}
