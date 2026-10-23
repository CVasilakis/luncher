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
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withChild
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.VerticalAlignment
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.FakePictures.Companion.picture
import com.luncher.domain.pictures.FakePreparedPictures
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.pictures.PictureImport
import com.luncher.domain.pictures.Pictures
import com.luncher.domain.pictures.PreparedPictures
import com.luncher.domain.wallpaper.FakeStoredWallpaper
import com.luncher.domain.wallpaper.Wallpaper
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.testing.RetryWhenCovered
import com.luncher.launcher.testing.matchesNow
import com.luncher.launcher.testing.waitForFocus
import com.luncher.launcher.testing.waitForHomeScreen
import com.luncher.launcher.testing.waitUntilDestroyed
import com.luncher.launcher.testing.waitUntilMatches
import com.luncher.launcher.ui.ListEntry
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

    /** The editor's row of the option labeled [label]: its name, and its value in a chip. */
    private fun option(label: Int) = allOf(withChild(withText(label)), hasDescendant(withId(R.id.settings_option_value)))

    /** An option's row showing [value]; another option can show the same value. */
    private fun hasValue(value: Int) = hasDescendant(allOf(withText(value), withId(R.id.settings_option_value)))

    /** An entry of the settings panel named [title]. */
    private fun panelEntry(title: Int) = allOf(isAssignableFrom(ListEntry::class.java), hasDescendant(allOf(withId(R.id.settings_entry_title), withText(title))))

    /**
     * Checks [condition] on the view [view] matches, retrying while a window comes up. Espresso
     * checks a view only once its window has the focus, so a key pressed after this goes there.
     */
    private fun waitUntil(view: Matcher<View>, condition: Matcher<View>) = waitUntilMatches(view, condition, TIMEOUT_MS)

    @Test
    fun okOnAGroup_showsItsOptions_dpadStepsThemAround_andBackReturnsToThePanel() {
        launchThePanel().use {
            waitUntil(panelEntry(R.string.settings_hide_apps), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(panelEntry(R.string.settings_appearance), hasFocus())
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
            waitUntil(panelEntry(R.string.settings_appearance), hasFocus())
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
            // Left, to the buttons below the preview: Reset to defaults, then Background.
            repeat(3) { if (!hasFocus(withId(R.id.settings_appearance_background))) press(KeyEvent.KEYCODE_DPAD_LEFT) }
            waitUntil(withId(R.id.settings_appearance_background), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)

            // The background editor: No picture chosen and focused, then the pictures.
            // The background editor's rows, the list's children, which take the focus: their names are in a column inside them.
            waitUntil(allOf(withParent(withId(R.id.settings_background_list)), hasDescendant(withText(R.string.settings_pictures_no_picture))), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(allOf(withParent(withId(R.id.settings_background_list)), hasDescendant(withText("sunset.jpg"))), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(allOf(withId(R.id.settings_background_chosen), isDisplayed()), isDisplayed())
            // Left, to the fields, down them to the buttons, and Left from Delete all to Save.
            press(KeyEvent.KEYCODE_DPAD_LEFT)
            repeat(6) {
                when {
                    hasFocus(withId(R.id.settings_background_save)) -> {}
                    hasFocus(withId(R.id.settings_background_delete_all)) -> press(KeyEvent.KEYCODE_DPAD_LEFT)
                    else -> press(KeyEvent.KEYCODE_DPAD_DOWN)
                }
            }
            waitUntil(withId(R.id.settings_background_save), hasFocus())
            assertEquals("nothing stored before Save", null, settings.appearance.background.picture)
            press(KeyEvent.KEYCODE_DPAD_CENTER)

            // Drawn, stored, and back in the appearance editor, whose preview shows it.
            val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
            while (settings.appearance.background.picture == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(POLL_MS)
            assertEquals(picture("sunset.jpg").id, settings.appearance.background.picture)
            // The editor's window is closing: keys and checks go to the appearance editor once it has the focus.
            waitForFocus(AppearanceActivity::class.java)
            waitUntilDestroyed(BackgroundActivity::class.java)
            waitUntil(withId(R.id.settings_appearance_background), isDisplayed())
        }
    }

    /** Pictures drawn as a plain color. */
    private val plainImages = FakePictureImages { _, width, height, _ ->
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }
    }

    /** Whether the view [view] matches has the focus now. */
    private fun hasFocus(view: Matcher<View>): Boolean = matchesNow(view, hasFocus())

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 50L
    }
}
