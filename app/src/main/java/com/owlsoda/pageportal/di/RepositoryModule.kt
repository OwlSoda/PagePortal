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
    
    // LibraryRepository uses @Inject constructor directly

}
