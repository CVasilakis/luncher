package com.luncher.launcher.outside

import com.luncher.launcher.outside.OutsideCallEnded.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class OutsideCallsTest {

    private val calls = OutsideCalls(most = 2)
    private val stop = Stop()

    /** Released at the end of each test, so no call it holds outlives it. */
    private val answer = CountDownLatch(1)

    private fun ended(reason: Reason, work: () -> Unit) {
        try {
            work()
            fail("ended normally")
        } catch (e: OutsideCallEnded) {
            assertEquals(reason, e.reason)
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition()) {
            if (System.nanoTime() > end) fail("not within 5 s")
            Thread.sleep(5)
        }
    }

    /** A call that answers "late" once [answer] is released. */
    private fun hanging(): String {
        answer.await(10, TimeUnit.SECONDS)
        return "late"
    }

    @Test
    fun `answers what the call answers, on a thread of its own that then ends`() {
        var thread: Thread? = null

        val answered = calls.call(stop, 5_000, close = {}) {
            thread = Thread.currentThread()
            "answer"
        }

        val called = thread!!
        assertEquals("answer", answered)
        assertTrue(called.name.startsWith("luncher-outside-"))
        assertTrue(called.isDaemon)
        assertFalse(called === Thread.currentThread())
        waitUntil { calls.aliveCount == 0 }
    }

    @Test
    fun `throws what the call throws`() {
        try {
            calls.call(stop, 5_000, close = {}) { throw SecurityException("no access") }
            fail("didn't throw")
        } catch (e: SecurityException) {
            assertEquals("no access", e.message)
        }
    }

    @Test
    fun `stops waiting at once when stopped, and closes the answer that comes late`() {
        val closed = CopyOnWriteArrayList<String>()
        Thread {
            Thread.sleep(50)
            stop.stop()
        }.start()
        val started = System.nanoTime()

        ended(Reason.STOPPED) { calls.call<String>(stop, 60_000, close = { closed.add(it) }) { hanging() } }
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(2))
        assertEquals(1, calls.aliveCount)
        answer.countDown()

        waitUntil { calls.aliveCount == 0 }
        assertEquals(listOf("late"), closed.toList())
    }

    @Test
    fun `stops waiting after the time it was given`() {
        val closed = CopyOnWriteArrayList<String>()

        ended(Reason.NO_ANSWER) { calls.call<String>(stop, 50, close = { closed.add(it) }) { hanging() } }
        answer.countDown()

        waitUntil { closed.toList() == listOf("late") }
    }

    @Test
    fun `starts no thread once stopped`() {
        var ran = false
        stop.stop()

        ended(Reason.STOPPED) { calls.call(stop, 5_000, close = {}) { ran = true } }

        Thread.sleep(50)
        assertFalse(ran)
        assertEquals(0, calls.aliveCount)
    }

    @Test
    fun `starts no more threads than its bound, whoever asks, until one ends`() {
        repeat(2) { ended(Reason.NO_ANSWER) { calls.call(Stop(), 10, close = {}) { hanging() } } }
        var ran = false

        ended(Reason.TOO_MANY) { calls.call(Stop(), 5_000, close = {}) { ran = true } }
        assertFalse(ran)
        assertEquals(2, calls.aliveCount)
        answer.countDown()
        waitUntil { calls.aliveCount == 0 }

        assertEquals("again", calls.call(stop, 5_000, close = {}) { "again" })
    }

    @Test
    fun `its bound is 10`() {
        assertEquals(10, OutsideCalls.MOST)
    }
}
