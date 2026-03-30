package com.owlsoda.pageportal.core.database.dao

import androidx.room.*
import com.owlsoda.pageportal.core.database.entity.BookEntity
import com.owlsoda.pageportal.core.database.entity.UnifiedBookEntity
import kotlinx.coroutines.flow.Flow

data class UnifiedBookWithBooks(
    @Embedded val unifiedBook: UnifiedBookEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "unifiedBookId"
    )
    val books: List<BookEntity>
)

@Dao
interface UnifiedBookDao {
    @Query("SELECT * FROM unified_books")
    fun getAll(): Flow<List<UnifiedBookEntity>>
    
    @Transaction
    @Query("SELECT * FROM unified_books ORDER BY lastUpdated DESC")
    fun getAllWithBooks(): Flow<List<UnifiedBookWithBooks>>

    @Transaction
    @Query("SELECT * FROM unified_books ORDER BY lastUpdated DESC")
    fun getPagedBooks(): androidx.paging.PagingSource<Int, UnifiedBookWithBooks>

    @Transaction
    @Query("SELECT * FROM unified_books WHERE id = :id")
    suspend fun getUnifiedBookWithBooksById(id: Long): UnifiedBookWithBooks?
    
    @Query("SELECT * FROM unified_books")
    suspend fun getUnifiedBooksSnapshot(): List<UnifiedBookEntity>
    
    @Query("SELECT * FROM unified_books WHERE id = :id")
    suspend fun getById(id: Long): UnifiedBookEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(unifiedBook: UnifiedBookEntity): Long
    
    @Update
    suspend fun update(unifiedBook: UnifiedBookEntity)
    
    @Delete
    suspend fun delete(unifiedBook: UnifiedBookEntity)
    
    @Query("DELETE FROM unified_books")
    suspend fun clearAll()
}
