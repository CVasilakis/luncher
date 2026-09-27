package com.luncher.launcher.clock

import android.content.Intent
import android.os.Looper
import android.provider.Settings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class AndroidClockTest {

    private val context = RuntimeEnvironment.getApplication()
    private val clock = AndroidClock(context)
    private val hostTimeZone: TimeZone = TimeZone.getDefault()

    private var changes = 0
    private val listener: () -> Unit = { changes++ }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(hostTimeZone)
    }

    /** Sends [action] as Android would, and delivers it. */
    private fun broadcast(action: String) {
        context.sendBroadcast(Intent(action))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun receivers() = shadowOf(context).registeredReceivers.size

    @Test
    fun `reads the current time`() {
        val before = System.currentTimeMillis()
        val read = clock.read().epochMillis
        val after = System.currentTimeMillis()

        assertTrue(read in before..after)
    }

    @Test
    fun `reads the device's time zone`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))

        assertEquals("Pacific/Kiritimati", clock.read().timeZone)
    }

    @Test
    fun `reads the device's hour format`() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
        assertTrue(clock.read().uses24Hour)

        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
        assertFalse(clock.read().uses24Hour)
    }

    @Test
    fun `tells its listeners at each new minute`() {
        clock.addListener(listener)

        broadcast(Intent.ACTION_TIME_TICK)

        assertEquals(1, changes)
    }

    @Test
    fun `tells its listeners when the time, the time zone or the hour format is set`() {
        clock.addListener(listener)

        broadcast(Intent.ACTION_TIME_CHANGED)   // also sent for the hour format
        broadcast(Intent.ACTION_TIMEZONE_CHANGED)

        assertEquals(2, changes)
    }

    @Test
    fun `watches the device only while it has listeners`() {
        val other: () -> Unit = {}
        assertEquals(0, receivers())

        clock.addListener(listener)
        clock.addListener(other)
        assertEquals(1, receivers())

        clock.removeListener(listener)
        assertEquals(1, receivers())

        clock.removeListener(other)
        assertEquals(0, receivers())
    }

    @Test
    fun `stops telling a listener once it's removed`() {
        clock.addListener(listener)
        clock.removeListener(listener)

        broadcast(Intent.ACTION_TIME_TICK)

        assertEquals(0, changes)
    }
}
