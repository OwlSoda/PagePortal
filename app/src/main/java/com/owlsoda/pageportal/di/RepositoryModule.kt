package com.owlsoda.pageportal.di

import com.owlsoda.pageportal.core.database.dao.ServerDao
import com.owlsoda.pageportal.core.repository.AuthRepository
import com.owlsoda.pageportal.services.ServiceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing repository dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    // AuthRepository removed - uses @Inject constructor directly
    
    @Provides
    @Singleton
    fun provideLibraryRepository(
        serviceManager: ServiceManager,
        bookDao: com.owlsoda.pageportal.core.database.dao.BookDao,
        matchingEngine: com.owlsoda.pageportal.core.matching.MatchingEngine
    ): com.owlsoda.pageportal.data.repository.LibraryRepository {
        return com.owlsoda.pageportal.data.repository.LibraryRepository(serviceManager, bookDao, matchingEngine)
    }
}
