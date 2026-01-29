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
        return Room.databaseBuilder(
            context,
            PagePortalDatabase::class.java,
            "pageportal_db"
        )
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
}
