package com.luncher.launcher.home

import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.R
import com.luncher.launcher.longPressOk
import com.luncher.launcher.resolvedHome
import com.luncher.launcher.waitForHomeScreen
import junit.framework.AssertionFailedError
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
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
            // Not the device's stored arrangement: every test starts with all apps shown, by name.
            override val appArrangements = FakeAppArrangements()
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

    /** Not while the home app is still starting, which can open windows over the home screen under test. */
    @Before
    fun startFromTheHomeScreen() {
        waitForHomeScreen()
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
    private fun waitUntilTile(label: String, condition: Matcher<View>) = waitUntil(withContentDescription(label), condition)

    /** Checks [condition] on the view [view] matches, retrying while an animation settles or a window comes up. */
    private fun waitUntil(view: Matcher<View>, condition: Matcher<View>) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (true) {
            try {
                onView(view).check(matches(condition))
                return
            } catch (e: AssertionFailedError) {
                if (SystemClock.uptimeMillis() > deadline) throw e
                SystemClock.sleep(POLL_MS)
            } catch (e: NoMatchingViewException) {
                // Not there yet, e.g. a panel whose window isn't in front yet.
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

    @Test
    fun dpadUp_fromTheFirstRow_movesFocusToTheSettingsEntry() {
        launchWith("games", "movies", "music").use {
            waitUntilTile("Games", hasFocus())

            press(KeyEvent.KEYCODE_DPAD_UP)

            waitUntil(withId(R.id.home_settings), hasFocus())
        }
    }

    @Test
    fun okOnTheSettingsEntry_opensTheSettingsPanel() {
        launchWith("games", "movies").use {
            waitUntilTile("Games", hasFocus())

            press(KeyEvent.KEYCODE_DPAD_UP)
            press(KeyEvent.KEYCODE_DPAD_CENTER)

            waitUntil(withText(R.string.settings_hide_apps), allOf(isDisplayed(), hasFocus()))
        }
    }

    @Test
    fun hidingAnAppInTheSettings_removesItsTile() {
        launchWith("games", "movies", "music").use {
            waitUntilTile("Games", hasFocus())

            press(KeyEvent.KEYCODE_MENU)                    // the settings panel
            waitUntil(withText(R.string.settings_hide_apps), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)             // Hide apps, with Games selected
            waitUntil(withId(R.id.settings_apps), isDisplayed())
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            press(KeyEvent.KEYCODE_DPAD_CENTER)             // hides Movies
            waitUntil(allOf(withId(R.id.settings_app_hidden), hasSibling(withText("Movies"))), isDisplayed())
            pressBack()
            pressBack()

            waitUntilTile("Games", hasFocus())
            onView(withContentDescription("Movies")).check(doesNotExist())
            onView(withContentDescription("Music")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun menuKey_opensTheSettingsPanel() {
        launchWith("games", "movies").use {
            waitUntilTile("Games", hasFocus())

            press(KeyEvent.KEYCODE_MENU)

            waitUntil(withText(R.string.settings_system), isDisplayed())
        }
    }

    @Test
    fun backKey_closesTheSettingsPanel_withFocusBackOnTheSettingsEntry() {
        launchWith("games", "movies").use { scenario ->
            waitUntilTile("Games", hasFocus())
            press(KeyEvent.KEYCODE_DPAD_UP)
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(withText(R.string.settings_system), isDisplayed())

            pressBack()

            waitUntil(withId(R.id.home_settings), hasFocus())
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    /** The labels of the tiles, in order: the shown apps, then while arranging the hidden ones. */
    private fun ActivityScenario<HomeActivity>.labels(): List<String> {
        var labels = emptyList<String>()
        onActivity { activity ->
            val tiles = activity.findViewById<AppTilesView>(R.id.home_apps)
            labels = (0 until tiles.childCount).map { (tiles.getChildAt(it) as AppTileView).app.label }
        }
        return labels
    }

    @Test
    fun longPressOk_startsArranging_andArrowsMoveTheHeldApp() {
        launchWith("games", "movies", "music").use { scenario ->
            waitUntilTile("Games", hasFocus())

            longPressOk()
            waitUntil(withId(R.id.home_arrange_title), isDisplayed())
            onView(withText(R.string.home_arrange_hint_holding)).check(matches(isDisplayed()))

            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            press(KeyEvent.KEYCODE_DPAD_CENTER)   // put it down

            waitUntil(withText(R.string.home_arrange_hint_browsing), isDisplayed())
            assertEquals(listOf("Movies", "Games", "Music"), scenario.labels())
            waitUntilTile("Games", hasFocus())
        }
    }

    @Test
    fun movingAnAppBelowTheLastRow_hidesIt_andBackEndsArranging() {
        launchWith("games", "movies", "music").use { scenario ->
            waitUntilTile("Games", hasFocus())
            longPressOk()
            waitUntil(withId(R.id.home_arrange_title), isDisplayed())

            press(KeyEvent.KEYCODE_DPAD_DOWN)     // onto the shelf
            pressBack()

            waitUntil(withId(R.id.home_arrange_title), not(isDisplayed()))
            waitUntil(withId(R.id.home_settings), isDisplayed())
            assertEquals(listOf("Movies", "Music"), scenario.labels())
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun okOnAHiddenApp_picksItUp_andUpShowsItAgain() {
        launchWith("games", "movies", "music").use { scenario ->
            waitUntilTile("Games", hasFocus())
            longPressOk()
            waitUntil(withId(R.id.home_arrange_title), isDisplayed())
            press(KeyEvent.KEYCODE_DPAD_DOWN)     // Games onto the shelf
            press(KeyEvent.KEYCODE_DPAD_CENTER)   // put it down there
            waitUntil(withText(R.string.home_arrange_hint_browsing), isDisplayed())

            press(KeyEvent.KEYCODE_DPAD_CENTER)   // pick it up again
            press(KeyEvent.KEYCODE_DPAD_UP)       // back among the shown apps
            pressBack()

            waitUntil(withId(R.id.home_settings), isDisplayed())
            assertEquals(listOf("Games", "Movies", "Music"), scenario.labels())
        }
    }

    /** "app01" to "app<count>", zero-padded so label order is number order. */
    private fun appNames(count: Int) = Array(count) { "app%02d".format(it + 1) }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 50L
    }
}
