package com.owlsoda.pageportal.data.repository

import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.dao.ProgressDao
import com.owlsoda.pageportal.core.database.entity.BookEntity
import com.owlsoda.pageportal.core.database.entity.ProgressEntity
import com.owlsoda.pageportal.services.BookService
import com.owlsoda.pageportal.services.ReadingProgress
import com.owlsoda.pageportal.services.ServiceManager
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import android.content.Context
import android.util.Log // Added Log import
import java.io.File

class SyncStressTest {

    private val progressDao = mockk<ProgressDao>(relaxed = true)
    private val bookDao = mockk<BookDao>(relaxed = true)
    private val serviceManager = mockk<ServiceManager>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val bookService = mockk<BookService>(relaxed = true)

    private lateinit var syncRepository: SyncRepository

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.v(any(), any()) } returns 0
        every { Log.w(any(), any() as String) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        
        syncRepository = SyncRepository(progressDao, bookDao, serviceManager, context)
        
        every { context.filesDir } returns File("/tmp")
        coEvery { bookDao.getBookById(any()) } returns BookEntity(
            id = 1,
            serverId = 1,
            serviceBookId = "service-1",
            title = "Test Book",
            authors = "[]" // Added missing field
        )
        coEvery { serviceManager.getService(any()) } returns bookService
    }

    @Test
    fun `test furthest progress wins within confidence window`() = runBlocking {
        val now = System.currentTimeMillis()
        
        // Scenario: Local is slightly OLDER in time but FURTHER in progress
        val localProgress = ProgressEntity(
            id = 1,
            bookId = 1,
            percentComplete = 50.0f,
            lastUpdated = now - 1000 // 1s ago
        )
        
        val remoteProgress = ReadingProgress(
            bookId = "service-1",
            percentComplete = 45.0f,
            lastUpdated = now // Current
        )

        coEvery { progressDao.getProgressByBookId(1) } returns localProgress
        coEvery { bookService.getProgress("service-1") } returns remoteProgress

        syncRepository.syncProgress(1)

        // VERIFY: Should NOT pull (remote is behind in progress)
        // AND should PUSH (local is ahead in progress despite being 'older' by 1s)
        coVerify(exactly = 0) { progressDao.insertProgress(any()) }
        coVerify(exactly = 1) { bookService.updateProgress(any(), any()) }
    }

    @Test
    fun `test latest timestamp wins outside confidence window`() = runBlocking {
        val now = System.currentTimeMillis()
        
        // Scenario: Local is MUCH OLDER (10 mins) and FURTHER in progress
        // Result: We trust the timestamp (User might have intentionally rewound)
        val localProgress = ProgressEntity(
            id = 1,
            bookId = 1,
            percentComplete = 50.0f,
            lastUpdated = now - 10 * 60 * 1000 // 10 mins ago
        )
        
        val remoteProgress = ReadingProgress(
            bookId = "service-1",
            percentComplete = 10.0f,
            lastUpdated = now // Current
        )

        coEvery { progressDao.getProgressByBookId(1) } returns localProgress
        coEvery { bookService.getProgress("service-1") } returns remoteProgress

        syncRepository.syncProgress(1)

        // VERIFY: Should PULL because remote is > 5 mins newer
        coVerify(exactly = 1) { progressDao.insertProgress(match { it.percentComplete == 10.0f }) }
    }
}
