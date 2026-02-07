package com.owlsoda.pageportal.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.owlsoda.pageportal.data.repository.LibraryRepository

/**
 * Worker to sync pending reading progress to remote servers.
 * Runs periodically or when network becomes available.
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncWorkerEntryPoint {
        fun libraryRepository(): LibraryRepository
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkerEntryPoint::class.java
        )
        val libraryRepository = entryPoint.libraryRepository()
        
        return try {
            val result = libraryRepository.syncPendingProgress()
            if (result.isSuccess) {
                 Result.success()
            } else {
                 Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
    
    companion object {
        const val WORK_NAME = "sync_progress_work"
    }
}
