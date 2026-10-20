package com.luncher.launcher.settings

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.VerticalAlignment
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.FakePictures.Companion.picture
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.pictures.PreparedPictures
import com.luncher.domain.pictures.FakePreparedPictures
import com.luncher.domain.pictures.PictureImport
import com.luncher.domain.pictures.Pictures
import com.luncher.domain.wallpaper.FakeStoredWallpaper
import com.luncher.domain.wallpaper.Wallpaper
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.testing.RetryWhenCovered
import com.luncher.launcher.testing.waitForFocus
import com.luncher.launcher.testing.waitForHomeScreen
import com.luncher.launcher.testing.waitUntilDestroyed
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
        // The rows at the top, the first of the vertical alignments, which the test steps from.
        settings = FakeAppearanceSettings(Appearance(TileGeometry(verticalAlignment = VerticalAlignment.TOP)))
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

    /** An option's row showing [value]; another option can show the same value. */
    private fun hasValue(value: Int) = withChild(allOf(withText(value), withId(R.id.settings_option_value)))

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

            waitUntil(option(R.string.settings_appearance_vertical_alignment), hasValue(R.string.settings_appearance_center))
            assertEquals(VerticalAlignment.CENTER, settings.appearance.tileGeometry.verticalAlignment)

            press(KeyEvent.KEYCODE_DPAD_LEFT)
            press(KeyEvent.KEYCODE_DPAD_LEFT)   // around, from the first value to the last

            waitUntil(option(R.string.settings_appearance_vertical_alignment), hasValue(R.string.settings_appearance_bottom))
            assertEquals(VerticalAlignment.BOTTOM, settings.appearance.tileGeometry.verticalAlignment)

            pressBack()
            waitUntilDestroyed(AppearanceActivity::class.java)

            // The panel, in front again, with the focus where it was.
            waitUntil(withText(R.string.settings_appearance), hasFocus())
            waitUntil(withText(R.string.settings_system), isDisplayed())
        }
    }

    @Test
    fun okOnTheBackground_opensTheBackgroundEditor_andSaveMakesTheChosenPictureTheWallpaper() {
        settings = FakeAppearanceSettings(Appearance(TileGeometry(verticalAlignment = VerticalAlignment.TOP)))
        val library = FakePictures<Uri>(picture("sunset.jpg"))
        application.graph = object : AppGraph(application) {
            override val appearanceSettings = this@AppearanceActivityTest.settings
            override val pictures: Pictures = library
            override val pictureImport: PictureImport<Uri> = library
            override val pictureImages: PictureImages<Bitmap> = plainImages
            override val thumbnails: PictureImages<Bitmap> = plainImages
            // In memory, every picture prepared: no window over the background editor as it opens.
            override val preparedPictures: PreparedPictures = FakePreparedPictures()
            // Not the device's kept wallpaper: one in memory, drawn as a plain color.
            override val wallpaper = Wallpaper(FakeStoredWallpaper(), plainImages, settings, jobs) { Handler(Looper.getMainLooper()).post(it) }
        }
        ActivityScenario.launch(AppearanceActivity::class.java).use {
            waitUntil(group(R.string.settings_appearance_tiles), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(apart(R.string.settings_appearance_background), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)

            // The background editor: No picture chosen and focused, then the pictures.
            waitUntil(withChild(withText(R.string.settings_pictures_no_picture)), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(withChild(withText("sunset.jpg")), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(allOf(withId(R.id.settings_background_chosen), isDisplayed()), isDisplayed())
            // Left, to the options, and down them to Save.
            press(KeyEvent.KEYCODE_DPAD_LEFT)
            repeat(5) { if (!hasFocus(withId(R.id.settings_background_save))) press(KeyEvent.KEYCODE_DPAD_DOWN) }
            waitUntil(withId(R.id.settings_background_save), hasFocus())
            assertEquals("nothing stored before Save", null, settings.appearance.background.picture)
            press(KeyEvent.KEYCODE_DPAD_CENTER)

            // Drawn, stored, and back in the appearance editor, which names it.
            val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
            while (settings.appearance.background.picture == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(POLL_MS)
            assertEquals(picture("sunset.jpg").id, settings.appearance.background.picture)
            // The editor's window is closing: keys and checks go to the appearance editor once it has the focus.
            waitForFocus(AppearanceActivity::class.java)
            waitUntil(allOf(withId(R.id.settings_apart_value), withText("sunset.jpg")), isDisplayed())
        }
    }

    /** Pictures drawn as a plain color. */
    private val plainImages = FakePictureImages { _, width, height, _ ->
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }
    }

    /** The editor's entry of the group titled [title], edited in a screen of its own. */
    private fun apart(title: Int) = allOf(withChild(withText(title)), withChild(withId(R.id.settings_apart_value)))

    /** Whether the view [view] matches has the focus now. */
    private fun hasFocus(view: Matcher<View>): Boolean =
        try {
            onView(view).check(matches(hasFocus()))
            true
        } catch (e: AssertionFailedError) {
            false
        } catch (e: NoMatchingViewException) {
            false
        }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 50L
    }
}
