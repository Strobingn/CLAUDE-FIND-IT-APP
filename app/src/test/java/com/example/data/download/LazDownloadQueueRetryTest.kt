package com.example.data.download

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Retrying an individual failed tile is what keeps one bad file in a large mosaic from forcing the
 * whole area to be resolved and fetched again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LazDownloadQueueRetryTest {
    private val url = "https://example.org/tile.laz"

    @After
    fun tearDown() = LazDownloadQueue.clearFinished()

    @Test
    fun aFailedTaskStaysInTheQueueSoItCanBeRetried() {
        LazDownloadQueue.enqueue(url, "tile.laz")
        LazDownloadQueue.markRunning(url)
        LazDownloadQueue.markFailed(url, "connection reset")

        val task = LazDownloadQueue.taskFor(url)
        assertEquals(LazDownloadState.FAILED, task?.state)
        assertEquals("connection reset", task?.error)
    }

    @Test
    fun retryingAFailedTaskQueuesItAgainAndClearsTheError() {
        LazDownloadQueue.enqueue(url, "tile.laz")
        LazDownloadQueue.markFailed(url, "connection reset")

        assertTrue("a failed task must be re-enqueueable", LazDownloadQueue.enqueue(url, "tile.laz"))

        val task = LazDownloadQueue.taskFor(url)
        assertEquals(LazDownloadState.QUEUED, task?.state)
        assertNull(task?.error)
    }

    @Test
    fun retryingDoesNotDuplicateTheTask() {
        LazDownloadQueue.enqueue(url, "tile.laz")
        LazDownloadQueue.markFailed(url, "connection reset")
        LazDownloadQueue.enqueue(url, "tile.laz")

        assertEquals(1, LazDownloadQueue.tasks.value.count { it.url == url })
    }

    /** A transfer already running must not be restarted from the retry row. */
    @Test
    fun aRunningTaskIsNotReEnqueued() {
        LazDownloadQueue.enqueue(url, "tile.laz")
        LazDownloadQueue.markRunning(url)

        assertFalse(LazDownloadQueue.enqueue(url, "tile.laz"))
        assertEquals(LazDownloadState.RUNNING, LazDownloadQueue.taskFor(url)?.state)
    }

    @Test
    fun dismissingAFailedTaskRemovesIt() {
        LazDownloadQueue.enqueue(url, "tile.laz")
        LazDownloadQueue.markFailed(url, "connection reset")
        LazDownloadQueue.dismiss(url)

        assertNull(LazDownloadQueue.taskFor(url))
    }

    /** Cancelling is a user decision, not a failure, so it must not surface as retryable. */
    @Test
    fun aCancelledTaskIsNotReportedAsFailed() {
        LazDownloadQueue.enqueue(url, "tile.laz")
        LazDownloadQueue.markRunning(url)
        LazDownloadQueue.requestCancel(url)
        LazDownloadQueue.markFailed(url, "stopped")

        val task = LazDownloadQueue.taskFor(url)
        assertEquals(LazDownloadState.CANCELLED, task?.state)
        assertNull(task?.error)
    }

    @Test
    fun completionClearsAnEarlierFailure() {
        LazDownloadQueue.enqueue(url, "tile.laz")
        LazDownloadQueue.markFailed(url, "connection reset")
        LazDownloadQueue.enqueue(url, "tile.laz")
        LazDownloadQueue.markCompleted(url, File.createTempFile("tile", ".laz"))

        val task = LazDownloadQueue.taskFor(url)
        assertEquals(LazDownloadState.COMPLETED, task?.state)
        assertNull(task?.error)
    }
}
