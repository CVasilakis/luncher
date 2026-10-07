package com.luncher.launcher.home

import android.widget.TextView
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.clock.FakeClock
import com.luncher.domain.clock.FakeClock.Companion.at
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** The clock on the home screen, in US English, where it runs. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class ClockViewTest {

    private val clock = FakeClock(at(2026, 9, 27, 14, 5))

    private fun start(): ActivityController<HomeActivity> {
        val application = RuntimeEnvironment.getApplication() as HomeTestApplication
        application.graph = object : TestHomeGraph(application) {
            override val installedApps = FakeInstalledApps(app("movies"))
            override val clock = this@ClockViewTest.clock
        }
        return Robolectric.buildActivity(HomeActivity::class.java).setup()
    }

    private fun ActivityController<HomeActivity>.time() = get().findViewById<TextView>(R.id.home_time).text.toString()

    private fun ActivityController<HomeActivity>.date() = get().findViewById<TextView>(R.id.home_date).text.toString()

    @Test
    fun `shows the time and the date`() {
        val home = start()

        assertEquals("14:05", home.time())
        assertEquals("Sunday, September 27", home.date())
    }

    @Test
    fun `shows 12-hour time when the device uses it`() {
        clock.set(at(2026, 9, 27, 14, 5, uses24Hour = false))

        val time = start().time()

        // Between the time and "PM", newer Android versions put a narrow space instead of a space.
        assertTrue(time, time.matches(Regex("""2:05\sPM""")))
    }

    @Test
    fun `shows the time and the date in the device's time zone`() {
        // 23:30 in UTC, the next day in Kiribati (UTC+14): not a zone a host running the test uses.
        clock.set(at(2026, 9, 28, 13, 30, timeZone = "Pacific/Kiritimati"))

        val home = start()

        assertEquals("13:30", home.time())
        assertEquals("Monday, September 28", home.date())
    }

    @Test
    fun `follows the clock while the home screen shows`() {
        val home = start()

        clock.set(at(2026, 9, 27, 23, 59))
        clock.set(at(2026, 9, 28, 0, 0))

        assertEquals("00:00", home.time())
        assertEquals("Monday, September 28", home.date())
    }

    @Test
    fun `stops listening to the clock while the home screen is hidden`() {
        val home = start()
        assertEquals(1, clock.listenerCount)

        home.pause().stop()   // an app was opened

        assertEquals(0, clock.listenerCount)
    }

    @Test
    fun `shows the current time when the home screen comes back`() {
        val home = start()

        home.pause().stop()
        clock.set(at(2026, 9, 27, 16, 40))
        home.restart().start().resume()

        assertEquals("16:40", home.time())
        assertEquals(1, clock.listenerCount)
    }

    @Test
    fun `stops listening to the clock when the home screen closes`() {
        start().pause().stop().destroy()

        assertEquals(0, clock.listenerCount)
    }
}
