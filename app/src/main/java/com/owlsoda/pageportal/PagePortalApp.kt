package com.owlsoda.pageportal

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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
        com.owlsoda.pageportal.util.LogManager.init(this)
        android.util.Log.d("PagePortalApp", "Application Created. WorkerFactory injected: ${::workerFactory.isInitialized}")
        scheduleSyncWorker()
        registerForegroundSyncTrigger()
    }
    
    /**
     * Triggers an immediate one-time SyncWorker run when the app comes to foreground.
     * This handles the case where the user opens the app after reading on another device —
     * the 15-min periodic worker may not have run yet, but we need current progress NOW.
     */
    private fun registerForegroundSyncTrigger() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                android.util.Log.d("PagePortalApp", "App came to foreground — triggering immediate sync")
                val immediateSync = androidx.work.OneTimeWorkRequestBuilder<com.owlsoda.pageportal.workers.SyncWorker>()
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                androidx.work.WorkManager.getInstance(this@PagePortalApp)
                    .enqueueUniqueWork(
                        "foreground_sync",
                        androidx.work.ExistingWorkPolicy.KEEP, // Don't enqueue if one is already queued
                        immediateSync
                    )
            }
        })
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
