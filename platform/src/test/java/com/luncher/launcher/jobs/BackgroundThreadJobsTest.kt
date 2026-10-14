package com.luncher.launcher.jobs

import android.os.Process
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class BackgroundThreadJobsTest {

    private val jobs = BackgroundThreadJobs()

    /** Holds the thread in a first job until [release], so that the jobs queued meanwhile wait together. */
    private val gate = CountDownLatch(1)
    private val held = CountDownLatch(1)

    private fun holdTheThread() {
        jobs.run(100) {
            held.countDown()
            gate.await(10, TimeUnit.SECONDS)
        }
        assertTrue(held.await(10, TimeUnit.SECONDS))
    }

    private fun release() = gate.countDown()

    private fun CountDownLatch.awaitDone() = assertTrue(await(10, TimeUnit.SECONDS))

    @Test
    fun `runs a job on a thread of its own, at background priority`() {
        val done = CountDownLatch(1)
        var thread: Thread? = null
        var priority = 0
        jobs.run(0) {
            thread = Thread.currentThread()
            priority = Process.getThreadPriority(Process.myTid())
            done.countDown()
        }

        done.awaitDone()
        assertNotEquals(Thread.currentThread(), thread)
        assertEquals(Process.THREAD_PRIORITY_BACKGROUND, priority)
    }

    @Test
    fun `runs the highest priority first, and in the order they came among equals`() {
        val ran = Collections.synchronizedList(mutableListOf<String>())
        val done = CountDownLatch(4)
        holdTheThread()
        for ((priority, name) in listOf(0 to "low 1", 5 to "high 1", 0 to "low 2", 5 to "high 2")) {
            jobs.run(priority) {
                ran += name
                done.countDown()
            }
        }
        release()

        done.awaitDone()
        assertEquals(listOf("high 1", "high 2", "low 1", "low 2"), ran)
    }

    @Test
    fun `doesn't run a job cancelled before it started`() {
        val ran = Collections.synchronizedList(mutableListOf<String>())
        val done = CountDownLatch(1)
        holdTheThread()
        val cancelled = jobs.run(0) { ran += "cancelled" }
        jobs.run(0) {
            ran += "kept"
            done.countDown()
        }
        cancelled.cancel()
        release()

        done.awaitDone()
        assertEquals(listOf("kept"), ran)
    }
}
