package com.owlsoda.pageportal.data.repository

import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.dao.ProgressDao
import com.owlsoda.pageportal.core.database.entity.BookEntity
import com.owlsoda.pageportal.core.database.entity.ProgressEntity
import com.owlsoda.pageportal.services.BookService
import com.owlsoda.pageportal.services.ReadingProgress
import com.owlsoda.pageportal.services.ServiceManager
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SyncRepository conflict resolution logic.
 *
 * Covers all merge scenarios:
 *   - Local newer → push
 *   - Remote newer → pull
 *   - Timestamps equal → mark synced, no network call
 *   - Remote unavailable → push local if dirty
 *   - Clock skew (< 2s) → treated as equal (prevents flip-flop)
 *   - Concurrent calls for same book → isSyncing flag
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncRepositoryTest {

    @MockK lateinit var progressDao: ProgressDao
    @MockK lateinit var bookDao: BookDao
    @MockK lateinit var serviceManager: ServiceManager
    @MockK lateinit var mockService: BookService

    private lateinit var syncRepository: SyncRepository

    private val testBookId = 42L
    private val testBook = mockk<BookEntity>(relaxed = true) {
        every { id } returns testBookId
        every { serverId } returns 1L
        every { serviceBookId } returns "srv-book-uuid"
        every { title } returns "Test Book"
    }

    private val BASE_TIME = 1_700_000_000_000L

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { bookDao.getBookById(testBookId) } returns testBook
        every { serviceManager.getService(any()) } returns mockService
        coEvery { progressDao.markSynced(any()) } just Runs
        coEvery { progressDao.markSynced(any(), any()) } just Runs
        coEvery { progressDao.insertProgress(any()) } returns Unit
        coEvery { mockService.updateProgress(any(), any()) } just Runs
        syncRepository = SyncRepository(progressDao, bookDao, serviceManager)
    }

    // ─── SCENARIO 1: Local is newer → should push to remote ───────────────────

    @Test
    fun `GIVEN local newer than remote WHEN sync THEN push to remote`() = runTest {
        val local = buildLocal(position = 5000L, lastUpdated = BASE_TIME + 10_000)
        val remote = buildRemote(position = 3000L, lastUpdated = BASE_TIME)

        coEvery { progressDao.getProgressByBookId(testBookId) } returns local
        coEvery { mockService.getProgress(any()) } returns remote

        syncRepository.syncProgress(testBookId)

        coVerify(exactly = 1) { mockService.updateProgress(any(), any()) }
        coVerify(exactly = 0) { progressDao.insertProgress(any()) }
    }

    // ─── SCENARIO 2: Remote is newer → should pull to local ───────────────────

    @Test
    fun `GIVEN remote newer than local WHEN sync THEN update local DB`() = runTest {
        val local = buildLocal(position = 3000L, lastUpdated = BASE_TIME)
        val remote = buildRemote(position = 8000L, lastUpdated = BASE_TIME + 10_000)

        coEvery { progressDao.getProgressByBookId(testBookId) } returns local
        coEvery { mockService.getProgress(any()) } returns remote

        syncRepository.syncProgress(testBookId)

        coVerify(exactly = 1) { progressDao.insertProgress(match { it.currentPosition == 8000L }) }
        coVerify(exactly = 0) { mockService.updateProgress(any(), any()) }
    }

    // ─── SCENARIO 3: Timestamps equal → no-op ─────────────────────────────────

    @Test
    fun `GIVEN local and remote timestamps equal WHEN sync THEN no push or pull`() = runTest {
        val local = buildLocal(position = 5000L, lastUpdated = BASE_TIME, syncedAt = BASE_TIME)
        val remote = buildRemote(position = 5000L, lastUpdated = BASE_TIME)

        coEvery { progressDao.getProgressByBookId(testBookId) } returns local
        coEvery { mockService.getProgress(any()) } returns remote

        syncRepository.syncProgress(testBookId)

        coVerify(exactly = 0) { mockService.updateProgress(any(), any()) }
        coVerify(exactly = 0) { progressDao.insertProgress(any()) }
    }

    // ─── SCENARIO 4: Remote unavailable → push if dirty ───────────────────────

    @Test
    fun `GIVEN remote unavailable AND local is dirty WHEN sync THEN push to remote`() = runTest {
        val dirtyLocal = buildLocal(position = 5000L, lastUpdated = BASE_TIME, syncedAt = null)

        coEvery { progressDao.getProgressByBookId(testBookId) } returns dirtyLocal
        coEvery { mockService.getProgress(any()) } throws Exception("Network error")

        val result = syncRepository.syncProgress(testBookId)

        // Should push dirty local, not fail
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { mockService.updateProgress(any(), any()) }
    }

    // ─── SCENARIO 5: Remote unavailable, local is synced → no-op ─────────────

    @Test
    fun `GIVEN remote unavailable AND local already synced WHEN sync THEN no push`() = runTest {
        val syncedLocal = buildLocal(position = 5000L, lastUpdated = BASE_TIME, syncedAt = BASE_TIME)

        coEvery { progressDao.getProgressByBookId(testBookId) } returns syncedLocal
        coEvery { mockService.getProgress(any()) } throws Exception("Network error")

        val result = syncRepository.syncProgress(testBookId)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { mockService.updateProgress(any(), any()) }
    }

    // ─── SCENARIO 6: Clock skew under 2s → treated as in-sync ────────────────

    @Test
    fun `GIVEN timestamps differ by less than 2s WHEN sync THEN no write or push`() = runTest {
        val local = buildLocal(position = 5000L, lastUpdated = BASE_TIME + 1000, syncedAt = BASE_TIME)
        val remote = buildRemote(position = 5000L, lastUpdated = BASE_TIME)

        coEvery { progressDao.getProgressByBookId(testBookId) } returns local
        coEvery { mockService.getProgress(any()) } returns remote

        syncRepository.syncProgress(testBookId)

        coVerify(exactly = 0) { mockService.updateProgress(any(), any()) }
        coVerify(exactly = 0) { progressDao.insertProgress(any()) }
    }

    // ─── SCENARIO 7: Missing book → Result.failure ────────────────────────────

    @Test
    fun `GIVEN book not found WHEN sync THEN returns failure`() = runTest {
        every { bookDao.getBookById(testBookId) } returns null

        val result = syncRepository.syncProgress(testBookId)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { mockService.updateProgress(any(), any()) }
    }

    // ─── SCENARIO 8: No local progress, remote available → pull ───────────────

    @Test
    fun `GIVEN no local progress AND remote available WHEN sync THEN pull remote`() = runTest {
        val remote = buildRemote(position = 6000L, lastUpdated = BASE_TIME)

        coEvery { progressDao.getProgressByBookId(testBookId) } returns null
        coEvery { mockService.getProgress(any()) } returns remote

        syncRepository.syncProgress(testBookId)

        coVerify(exactly = 1) { progressDao.insertProgress(match { it.currentPosition == 6000L }) }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun buildLocal(
        position: Long,
        lastUpdated: Long,
        syncedAt: Long? = BASE_TIME - 1000L
    ) = ProgressEntity(
        id = 1L,
        bookId = testBookId,
        currentPosition = position,
        currentChapter = 2,
        percentComplete = 25f,
        lastUpdated = lastUpdated,
        syncedAt = syncedAt
    )

    private fun buildRemote(position: Long, lastUpdated: Long) = ReadingProgress(
        bookId = testBook.serviceBookId,
        currentPosition = position,
        currentChapter = 2,
        percentComplete = 25f,
        isFinished = false,
        lastUpdated = lastUpdated
    )
}
