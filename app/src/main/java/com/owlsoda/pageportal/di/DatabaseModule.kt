package com.owlsoda.pageportal.di

import android.content.Context
import androidx.room.Room
import com.owlsoda.pageportal.core.database.PagePortalDatabase
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.dao.ProgressDao
import com.owlsoda.pageportal.core.database.dao.UnifiedBookDao
import com.owlsoda.pageportal.core.database.dao.ServerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PagePortalDatabase {
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `highlights` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, `chapterIndex` INTEGER NOT NULL, `cfi` TEXT NOT NULL, `selectedText` TEXT NOT NULL, `color` TEXT NOT NULL, `note` TEXT, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_highlights_bookId` ON `highlights` (`bookId`)")
            }
        }

        return Room.databaseBuilder(
            context,
            PagePortalDatabase::class.java,
            "pageportal_db"
        )
            .addMigrations(MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    fun provideServerDao(database: PagePortalDatabase): ServerDao {
        return database.serverDao()
    }
    
    @Provides
    fun provideBookDao(database: PagePortalDatabase): BookDao {
        return database.bookDao()
    }
    
    @Provides
    fun provideProgressDao(database: PagePortalDatabase): ProgressDao {
        return database.progressDao()
    }
    
    @Provides
    fun provideUnifiedBookDao(database: PagePortalDatabase): UnifiedBookDao {
        return database.unifiedBookDao()
    }

    @Provides
    fun provideHighlightDao(database: PagePortalDatabase): com.owlsoda.pageportal.core.database.dao.HighlightDao {
        return database.highlightDao()
    }
    
    @Provides
    fun provideCollectionDao(database: PagePortalDatabase): com.owlsoda.pageportal.core.database.dao.CollectionDao {
        return database.collectionDao()
    }
}
