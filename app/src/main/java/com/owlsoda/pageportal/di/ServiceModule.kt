package com.owlsoda.pageportal.di

import com.owlsoda.pageportal.core.database.dao.ServerDao
import com.owlsoda.pageportal.services.ServiceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Hilt module for providing service-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    
    @Provides
    @Singleton
    fun provideServiceManager(
        serverDao: ServerDao,
        okHttpClient: OkHttpClient
    ): ServiceManager {
        return ServiceManager(serverDao, okHttpClient)
    }
}
