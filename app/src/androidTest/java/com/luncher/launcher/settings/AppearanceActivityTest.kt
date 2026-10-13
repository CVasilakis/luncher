package com.luncher.launcher.settings

import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withChild
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.VerticalAlignment
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.testing.RetryWhenCovered
import com.luncher.launcher.testing.waitForHomeScreen
import junit.framework.AssertionFailedError
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The appearance editor on a real device, opened from the settings panel and driven by real key events. */
@RunWith(AndroidJUnit4::class)
class AppearanceActivityTest {

    @get:Rule
    val retryWhenCovered = RetryWhenCovered()

    private val application = ApplicationProvider.getApplicationContext<LuncherApplication>()

    /** New at each launch: [RetryWhenCovered] runs a covered test again on the same instance. */
    private lateinit var settings: FakeAppearanceSettings

    private fun launchThePanel(): ActivityScenario<SettingsActivity> {
        settings = FakeAppearanceSettings()
        application.graph = object : AppGraph(application) {
            override val appearanceSettings = this@AppearanceActivityTest.settings
        }
        return ActivityScenario.launch(SettingsActivity::class.java)
    }

    /** Not while the home app is still starting, which can open windows over the panel. */
    @Before
    fun startFromTheHomeScreen() {
        waitForHomeScreen()
    }

    @After
    fun restoreRealPorts() {
        // The process outlives the test, unlike under Robolectric.
        application.graph = AppGraph(application)
    }

    private fun press(keyCode: Int) {
        onView(isRoot()).perform(pressKey(keyCode))
    }

    /** The editor's entry of the group titled [title]. */
    private fun group(title: Int) = allOf(withChild(withText(title)), withChild(withId(R.id.settings_group_arrow)))

    /** The editor's row of the option labeled [label]. */
    private fun option(label: Int) = allOf(withChild(withText(label)), withChild(withId(R.id.settings_option_value)))

    /**
     * Checks [condition] on the view [view] matches, retrying while a window comes up. Espresso
     * checks a view only once its window has the focus, so a key pressed after this goes there.
     */
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
                // Not there yet, e.g. the editor whose window isn't in front yet.
                if (SystemClock.uptimeMillis() > deadline) throw e
                SystemClock.sleep(POLL_MS)
            }
        }
    }

    @Test
    fun okOnAGroup_showsItsOptions_dpadStepsThemAround_andBackReturnsToThePanel() {
        launchThePanel().use {
            waitUntil(withText(R.string.settings_hide_apps), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(withText(R.string.settings_appearance), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(group(R.string.settings_appearance_tiles), hasFocus())

            press(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(option(R.string.settings_appearance_vertical_alignment), isDisplayed())
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(option(R.string.settings_appearance_vertical_alignment), hasFocus())

            press(KeyEvent.KEYCODE_DPAD_RIGHT)

            waitUntil(allOf(withText(R.string.settings_appearance_center), withId(R.id.settings_option_value)), isDisplayed())
            assertEquals(VerticalAlignment.CENTER, settings.appearance.tileGeometry.verticalAlignment)

            press(KeyEvent.KEYCODE_DPAD_LEFT)
            press(KeyEvent.KEYCODE_DPAD_LEFT)   // around, from the first value to the last

            waitUntil(allOf(withText(R.string.settings_appearance_bottom), withId(R.id.settings_option_value)), isDisplayed())
            assertEquals(VerticalAlignment.BOTTOM, settings.appearance.tileGeometry.verticalAlignment)

            pressBack()

            // The panel, in front again, with the focus where it was.
            waitUntil(withText(R.string.settings_appearance), hasFocus())
            waitUntil(withText(R.string.settings_system), isDisplayed())
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 50L
    }
}
