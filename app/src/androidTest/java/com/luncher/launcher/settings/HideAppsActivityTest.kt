package com.luncher.launcher.settings

import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.widget.ListView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.R
import com.luncher.launcher.waitForHomeScreen
import junit.framework.AssertionFailedError
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The Hide apps list on a real device, driven by real key events. */
@RunWith(AndroidJUnit4::class)
class HideAppsActivityTest {

    private val application = ApplicationProvider.getApplicationContext<LuncherApplication>()
    private val arrangements = FakeAppArrangements()

    private fun launchWith(names: List<String>): ActivityScenario<HideAppsActivity> {
        application.graph = object : AppGraph(application) {
            override val installedApps = FakeInstalledApps(names.map { app(it) })
            override val appArrangements = this@HideAppsActivityTest.arrangements
        }
        return ActivityScenario.launch(HideAppsActivity::class.java)
    }

    /** Not while the home app is still starting, which can open windows over the list. */
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

    /** The "Hidden" mark in the row of the app labeled [label]. */
    private fun hiddenMark(label: String) = allOf(withId(R.id.settings_app_hidden), hasSibling(withText(label)))

    /** Checks [condition] on the view [view] matches, retrying while the list settles. */
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

    @Test
    fun ok_hidesTheSelectedApp_andOkAgainShowsIt() {
        launchWith(listOf("games", "movies", "music")).use {
            waitUntil(withText("Games"), isDisplayed())

            press(KeyEvent.KEYCODE_DPAD_DOWN)
            press(KeyEvent.KEYCODE_DPAD_CENTER)

            waitUntil(hiddenMark("Movies"), isDisplayed())
            assertEquals(listOf(app("movies").launchable), arrangements.arrangement.hidden)
            onView(hiddenMark("Games")).check(matches(not(isDisplayed())))

            press(KeyEvent.KEYCODE_DPAD_CENTER)

            waitUntil(hiddenMark("Movies"), not(isDisplayed()))
            assertEquals(emptyList<Any>(), arrangements.arrangement.hidden)
        }
    }

    @Test
    fun dpadDown_scrollsTheListToTheSelectedApp() {
        // Twelve apps, more than the list shows at once.
        val names = List(12) { "app%02d".format(it + 1) }
        launchWith(names).use { scenario ->
            waitUntil(withText("App01"), isDisplayed())

            repeat(11) { press(KeyEvent.KEYCODE_DPAD_DOWN) }

            waitUntil(withText("App12"), isCompletelyDisplayed())
            scenario.onActivity { assertEquals(11, it.findViewById<ListView>(R.id.settings_apps).selectedItemPosition) }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 50L
    }
}
