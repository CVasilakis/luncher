package com.luncher.launcher.outside

import android.provider.OpenableColumns
import com.luncher.launcher.outside.OutsideCallEnded.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.FileDescriptor

@RunWith(RobolectricTestRunner::class)
class OutsideReaderTest {

    private val resolver = RuntimeEnvironment.getApplication().contentResolver
    private val app = AnotherApp.install()
    private val stop = Stop()
    private var time = 0L

    /** Each wait for data, how long it was asked to wait, in ms. */
    private val waits = mutableListOf<Int>()

    /** What a wait for data answers: whether some came. */
    private var ready: () -> Boolean = { true }

    /** On the test's clock: a wait for data passes the time it was given, and answers [ready]. Calls wait in real time. */
    private val calls = OutsideCalls(now = { time }, waitForData = { _: FileDescriptor, ms: Int ->
        waits += ms
        time += ms
        ready()
    })

    private fun reader(limitMs: Long = OutsideReader.LIMIT_MS) = OutsideReader(resolver, calls, stop, limitMs)

    private fun ended(reason: Reason, work: () -> Unit) {
        try {
            work()
            fail("ended normally")
        } catch (e: OutsideCallEnded) {
            assertEquals(reason, e.reason)
        }
    }

    private val bytes = ByteArray(300_000) { it.toByte() }

    @Test
    fun `reads what the app sends to its end, waiting for data before each read`() {
        val uri = app.sends("a.png", bytes)

        val read = reader().open(uri).use { it.readBytes() }

        assertTrue(read.contentEquals(bytes))
        assertTrue(waits.isNotEmpty())
    }

    @Test
    fun `asks the app's query`() {
        val name = reader().query(app.uri("a.png"), arrayOf(OpenableColumns.DISPLAY_NAME)) { row ->
            row!!.moveToFirst()
            row.getString(0)
        }

        assertEquals("a.png", name)
    }

    @Test
    fun `gives up when the app sends nothing for the time limit, looking at least every slice`() {
        ready = { false }
        val input = reader().open(app.sendsNothing("a.png"))

        ended(Reason.NO_ANSWER) { input.read(ByteArray(10)) }
        input.close()

        assertEquals(OutsideReader.LIMIT_MS, time)
        assertTrue(waits.all { it <= OutsideReader.SLICE_MS })
    }

    @Test
    fun `a transfer that goes on isn't cut, however long it takes`() {
        var looks = 0
        // Nothing for 14 s before each piece: minutes in all.
        ready = { ++looks % 56 == 0 }
        val input = reader().open(app.sends("slow.png", bytes))

        val read = input.use { it.readBytes() }

        assertTrue(read.contentEquals(bytes))
        assertTrue(time > OutsideReader.LIMIT_MS)
    }

    @Test
    fun `stops reading at once when stopped`() {
        var looks = 0
        ready = {
            if (++looks == 3) stop.stop()
            false
        }
        val input = reader().open(app.sendsNothing("a.png"))

        ended(Reason.STOPPED) { input.read(ByteArray(10)) }
        input.close()

        assertEquals(3, looks)
    }

    @Test
    fun `ends at the length the app declared, without waiting for more`() {
        val uri = app.sends("a.png", bytes, declaredLength = 5)
        val input = reader().open(uri)

        assertEquals(5, input.use { it.readBytes() }.size)
        assertEquals(1, waits.size)
    }

    @Test
    fun `gives up on a query the app doesn't answer, cancelling its signal`() {
        app.holdQueries = true
        val reader = reader(limitMs = 200)

        ended(Reason.NO_ANSWER) { reader.query(app.uri("a.png"), arrayOf(OpenableColumns.DISPLAY_NAME)) {} }

        waitUntil { app.cancelled == 1 }
    }

    @Test
    fun `gives up on an open the app doesn't answer when stopped, and closes what it answers late`() {
        app.holdOpens = true
        val reader = reader()
        Thread {
            Thread.sleep(100)
            stop.stop()
        }.start()

        ended(Reason.STOPPED) { reader.open(app.sendsNothing("a.png")) }
        app.letGo()

        waitUntil { app.openedLate != null && calls.aliveCount == 0 }
        assertFalse(app.openedLate!!.parcelFileDescriptor.fileDescriptor.valid())
    }

    @Test
    fun `gives up at once once stopped, asking nothing`() {
        stop.stop()

        ended(Reason.STOPPED) { reader().open(app.sends("a.png", bytes)) }
        assertEquals(0, calls.aliveCount)
    }

    private fun waitUntil(condition: () -> Boolean) {
        val end = System.nanoTime() + 5_000_000_000
        while (!condition()) {
            if (System.nanoTime() > end) fail("not within 5 s")
            Thread.sleep(5)
        }
    }
}
