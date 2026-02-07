package com.owlsoda.pageportal.services.local

import com.owlsoda.pageportal.services.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class LocalService : BookService {
    override val serviceType: ServiceType = ServiceType.LOCAL
    override val displayName: String = "Local Library"

    override suspend fun authenticate(serverUrl: String, username: String, password: String): AuthResult {
        return AuthResult(success = true, token = "local_token", userId = "local_user")
    }

    override suspend fun listBooks(page: Int, pageSize: Int): List<ServiceBook> {
        // Local books are managed directly via DB specific to local import, 
        // not via this service interface which is designed for remote syncing.
        return emptyList()
    }

    override suspend fun getBookDetails(bookId: String): ServiceBookDetails {
        throw NotImplementedError("LocalService does not support remote details fetching")
    }

    override suspend fun getProgress(bookId: String): ReadingProgress? {
        return null
    }

    override suspend fun updateProgress(bookId: String, progress: ReadingProgress) {
        // No-op
    }

    override suspend fun downloadBook(bookId: String): Flow<DownloadProgress> {
        return flowOf(DownloadProgress(bookId, 0, 0, DownloadStatus.COMPLETED))
    }

    override fun getCoverUrl(bookId: String): String {
        return bookId // ID is local path
    }

    override fun supportsFeature(feature: ServiceFeature): Boolean {
        return when (feature) {
            ServiceFeature.EBOOK_READING -> true
            ServiceFeature.AUDIOBOOK_PLAYBACK -> true
            ServiceFeature.READALOUD_SYNC -> true
            else -> false
        }
    }
}
