package com.owlsoda.pageportal.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.dao.CollectionDao
import com.owlsoda.pageportal.core.database.dao.ProgressDao
import com.owlsoda.pageportal.core.database.dao.ServerDao
import com.owlsoda.pageportal.core.database.dao.UnifiedBookDao
import com.owlsoda.pageportal.core.database.entity.BookEntity
import com.owlsoda.pageportal.core.database.entity.CollectionEntity
import com.owlsoda.pageportal.core.database.entity.ProgressEntity
import com.owlsoda.pageportal.core.database.entity.ServerEntity
import com.owlsoda.pageportal.core.database.entity.UnifiedBookEntity

/**
 * Main Room database for PagePortal.
 * Stores servers, cached books, and reading progress.
 */
@Database(
    entities = [
        ServerEntity::class,
        BookEntity::class,
        ProgressEntity::class,
        com.owlsoda.pageportal.core.database.entity.UnifiedBookEntity::class,
        com.owlsoda.pageportal.core.database.entity.HighlightEntity::class,
        CollectionEntity::class
    ],
    version = 5,  // Incremented for Highlights
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PagePortalDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun bookDao(): BookDao
    abstract fun progressDao(): ProgressDao
    abstract fun unifiedBookDao(): com.owlsoda.pageportal.core.database.dao.UnifiedBookDao
    abstract fun highlightDao(): com.owlsoda.pageportal.core.database.dao.HighlightDao
    abstract fun collectionDao(): CollectionDao
}
