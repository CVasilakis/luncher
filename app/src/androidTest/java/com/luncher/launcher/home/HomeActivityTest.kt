package com.luncher.launcher.home

import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.R
import com.luncher.launcher.resolvedHome
import junit.framework.AssertionFailedError
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The home screen on a real device, driven by real key events. */
@RunWith(AndroidJUnit4::class)
class HomeActivityTest {

    private val application = ApplicationProvider.getApplicationContext<LuncherApplication>()

    private fun launchWith(vararg names: String): ActivityScenario<HomeActivity> {
        application.graph = object : AppGraph(application) {
            override val installedApps = FakeInstalledApps(names.map { app(it) })
        }
        return ActivityScenario.launch(HomeActivity::class.java)
    }

    /**
     * Closing the home screen at the end of a test can hang while Luncher is the device's home app:
     * Android starts it again at once, and the closing activity may stay paused. Fail at once with
     * the fix instead of a timeout that looks like Luncher's fault; see docs/TESTING.md.
     */
    @Before
    fun checkLuncherIsNotTheHomeApp() {
        assertNotEquals(
            "Luncher is the device's home app; re-enable the stock launcher first (docs/TESTING.md)",
            application.packageName,
            resolvedHome(),
        )
    }

    @After
    fun restoreRealApps() {
        // The process outlives the test, unlike under Robolectric.
        application.graph = AppGraph(application)
    }

    private fun press(keyCode: Int) {
        onView(isRoot()).perform(pressKey(keyCode))
    }

    /** Checks [condition] on the tile labeled [label], retrying while an animation settles. */
    private fun waitUntilTile(label: String, condition: Matcher<View>) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (true) {
            try {
                onView(withContentDescription(label)).check(matches(condition))
                return
            } catch (e: AssertionFailedError) {
                if (SystemClock.uptimeMillis() > deadline) throw e
                SystemClock.sleep(POLL_MS)
            }
        }
    }

    // Only testable here: on the real home screen, Android restarts a closed home activity at once,
    // so from the outside (UI Automator) it would look as if Back had done nothing.
    @Test
    fun backKey_doesNotCloseTheHomeScreen() {
        launchWith("movies", "music").use { scenario ->
            pressBackUnconditionally()

            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            onView(withId(R.id.home_apps)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun dpadRight_movesFocusToTheNextApp() {
        launchWith("games", "movies", "music").use {
            waitUntilTile("Games", hasFocus())

            press(KeyEvent.KEYCODE_DPAD_RIGHT)

            waitUntilTile("Movies", hasFocus())
        }
    }

    @Test
    fun dpadDown_movesFocusToTheAppBelow() {
        // Five per row: app06 is below app01.
        launchWith(*appNames(7)).use {
            waitUntilTile("App01", hasFocus())

            press(KeyEvent.KEYCODE_DPAD_DOWN)

            waitUntilTile("App06", hasFocus())
        }
    }

    @Test
    fun dpadDown_scrollsTheFocusedAppIntoView() {
        // Ten rows of five, more than the screen holds.
        launchWith(*appNames(50)).use {
            waitUntilTile("App01", hasFocus())

            repeat(9) { press(KeyEvent.KEYCODE_DPAD_DOWN) }

            waitUntilTile("App46", allOf(hasFocus(), isCompletelyDisplayed()))
        }
    }

    /** "app01" to "app<count>", zero-padded so label order is number order. */
    private fun appNames(count: Int) = Array(count) { "app%02d".format(it + 1) }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 50L
    }
}
