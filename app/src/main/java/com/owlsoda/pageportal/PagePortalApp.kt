package com.owlsoda.pageportal

import android.app.Application
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

import coil.ImageLoader
import coil.ImageLoaderFactory

@HiltAndroidApp
class PagePortalApp : Application(), Configuration.Provider, ImageLoaderFactory {
    
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var imageLoader: ImageLoader

    override val workManagerConfiguration: Configuration
        get() {
            android.util.Log.d("PagePortalApp", "Initializing custom WorkManager configuration")
            return Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .build()
        }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("PagePortalApp", "Application Created. WorkerFactory injected: ${::workerFactory.isInitialized}")
        scheduleSyncWorker()
    }
    
    private fun scheduleSyncWorker() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
            
        val syncWork = androidx.work.PeriodicWorkRequestBuilder<com.owlsoda.pageportal.workers.SyncWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
            
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            com.owlsoda.pageportal.workers.SyncWorker.WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            syncWork
        )
    }

    override fun newImageLoader(): ImageLoader {
        return imageLoader
    }
}
