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
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withChild
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.HorizontalAlignment
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.VerticalAlignment
import com.luncher.domain.apps.AppCustomization
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeAppCustomizations
import com.luncher.domain.apps.FakeAppImages
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.FakePreparedTiles
import com.luncher.domain.apps.PreparedTileImages
import com.luncher.domain.apps.TileBackdrop
import com.luncher.domain.apps.TileDrawing
import com.luncher.domain.apps.TileFocusShape
import com.luncher.domain.apps.TileImage
import com.luncher.domain.apps.TileLook
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.FakePictures.Companion.picture
import com.luncher.domain.pictures.FakePreparedPictures
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.pictures.PictureImport
import com.luncher.domain.pictures.Pictures
import com.luncher.domain.pictures.PreparedPictures
import com.luncher.domain.wallpaper.FakeStoredWallpaper
import com.luncher.domain.wallpaper.Wallpaper
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.home.HomeActivity
import com.luncher.launcher.home.R as HomeR
import com.luncher.launcher.testing.FailureCapture
import com.luncher.launcher.testing.RetryWhenCovered
import com.luncher.launcher.testing.longPressOk
import com.luncher.launcher.testing.matchesNow
import com.luncher.launcher.testing.resolvedHome
import com.luncher.launcher.testing.waitForFocus
import com.luncher.launcher.testing.waitForHomeScreen
import com.luncher.launcher.testing.waitUntilDestroyed
import com.luncher.launcher.testing.waitUntilMatches
import com.luncher.launcher.ui.ListEntry
import com.luncher.launcher.ui.homeTileSize
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The tile banner picker on a real device, driven by real key events: opened from a tile's menu
 * on the home screen, a picture chosen and saved, and the tile showing it. Nothing of the device's
 * own is read or stored: the apps, the library and what's set for the apps are the test's, in
 * memory.
 */
@RunWith(AndroidJUnit4::class)
class TileBannerActivityTest {

    @get:Rule
    val retryWhenCovered = RetryWhenCovered()

    private val application = ApplicationProvider.getApplicationContext<LuncherApplication>()

    private val beach = picture("beach.png", added = 1)
    private val sunset = picture("sunset.jpg", added = 2)

    // See-through, and of a tile's shape: the look applies, the fit and the position don't. Last by name.
    private val logo = picture("zebra-logo.png", added = 3).copy(seeThrough = true)
    private val movies = app("movies")

    /** New at each launch: [RetryWhenCovered] runs a covered test again on the same instance. */
    private lateinit var customizations: FakeAppCustomizations
    private lateinit var tiles: FakeAppImages<Bitmap>
    private lateinit var prepared: FakePreparedTiles<Bitmap>

    /**
     * The test's own apps (movies and music), library ([beach], [sunset] and [logo], every one prepared)
     * and what's set for the apps ([stored]), in memory; the tiles' own images drawn as a plain color,
     * each one counted; their pictures prepared and kept in memory ([prepared]), as plain as the pictures.
     */
    private fun useTheTestsGraph(stored: Map<com.luncher.domain.apps.LaunchableApp, AppCustomization> = emptyMap()) {
        customizations = FakeAppCustomizations(stored)
        tiles = FakeAppImages { _, _, width, height -> Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(TILE) } }
        prepared = FakePreparedTiles { Bitmap.createBitmap(it.size.width, it.size.height, Bitmap.Config.ARGB_8888).apply { eraseColor(PICTURE) } }
        val library = FakePictures<Uri>(beach, sunset, logo)
        application.graph = object : AppGraph(application) {
            override val installedApps = FakeInstalledApps(listOf(movies, app("music")))
            override val appArrangements = FakeAppArrangements()
            override val appCustomizations = this@TileBannerActivityTest.customizations
            override val appImages = this@TileBannerActivityTest.tiles
            override val appearanceSettings = FakeAppearanceSettings(Appearance(topLeftTiles))
            override val pictures: Pictures = library
            override val pictureImport: PictureImport<Uri> = library
            override val pictureImages: PictureImages<Bitmap> = FakePictureImages { _, width, height, _ ->
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(PICTURE) }
            }
            override val thumbnails: PictureImages<Bitmap> get() = pictureImages
            override val preparedPictures: PreparedPictures = FakePreparedPictures()
            // Not the device's kept wallpaper: none, kept in memory.
            override val wallpaper = Wallpaper(FakeStoredWallpaper(), pictureImages, appearanceSettings, jobs) { Handler(Looper.getMainLooper()).post(it) }
            override val preparedTileImages = PreparedTileImages(preparedPictures, this@TileBannerActivityTest.prepared, jobs) { Handler(Looper.getMainLooper()).post(it) }
        }
    }

    /** Closing the home screen at the end of a test can hang while Luncher is the device's home app (HomeActivityTest). */
    @Before
    fun checkLuncherIsNotTheHomeApp() {
        assertNotEquals(
            "Luncher is the device's home app; re-enable the stock launcher first (docs/INSTRUMENTED-TESTS.md)",
            application.packageName,
            resolvedHome(),
        )
    }

    /** Not while the home app is still starting, which can open windows over the screen under test. */
    @Before
    fun startFromTheHomeScreen() {
        waitForHomeScreen()
    }

    @After
    fun restore() {
        // The process outlives the test, unlike under Robolectric.
        application.graph = AppGraph(application)
    }

    private fun press(keyCode: Int) {
        onView(isRoot()).perform(pressKey(keyCode))
    }

    private fun pressInDialog(keyCode: Int) {
        onView(isRoot()).inRoot(isDialog()).perform(pressKey(keyCode))
    }

    private fun pressInList(keyCode: Int) {
        onView(isRoot()).inRoot(isPlatformPopup()).perform(pressKey(keyCode))
    }

    /** In a dialog ([inDialog]) or an option's list ([inList]), retrying while a window comes up. */
    private fun waitUntil(view: Matcher<View>, condition: Matcher<View>, inDialog: Boolean = false, inList: Boolean = false) =
        waitUntilMatches(view, condition, TIMEOUT_MS, root = if (inDialog) isDialog() else if (inList) isPlatformPopup() else null)

    /** The tile image's option named [label], with [value]: a field of the grid. */
    private fun option(label: Int, value: Int) = allOf(
        isAssignableFrom(ListEntry::class.java),
        hasDescendant(allOf(withId(R.id.settings_option_label), withText(label))),
        hasDescendant(allOf(withId(R.id.settings_option_value), withText(value))),
    )

    /** A value of an option's list, named [value]. */
    private fun listed(value: Int) = withChild(allOf(withId(R.id.settings_option_list_value), withText(value)))

    private fun waitUntilMenuEntryFocused(label: Int) =
        waitUntil(allOf(isAssignableFrom(ListEntry::class.java), hasDescendant(allOf(withId(HomeR.id.home_tile_menu_label), withText(label)))), hasFocus(), inDialog = true)

    /** The picker's list's row named [name]. */
    private fun row(name: String) = allOf(withParent(withId(R.id.settings_pictures_list)), hasDescendant(allOf(withId(R.id.settings_pictures_name), withText(name))))

    private fun row(name: Int) = allOf(withParent(withId(R.id.settings_pictures_list)), hasDescendant(allOf(withId(R.id.settings_pictures_name), withText(name))))

    /** OK on the row named [name], once it has the focus: it's chosen. */
    private fun choose(name: String) {
        waitUntil(row(name), hasFocus())
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitUntil(allOf(withId(R.id.settings_pictures_chosen), isDescendantOfA(row(name))), isDisplayed())
    }

    /** From a picture of the list, the focus to Save: Left to the fields, down them to the buttons, Left from Delete all. */
    private fun focusSave() {
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        repeat(4) {
            when {
                matchesNow(withId(R.id.settings_picture_editor_save), hasFocus()) -> return
                matchesNow(withId(R.id.settings_picture_editor_delete_all), hasFocus()) -> press(KeyEvent.KEYCODE_DPAD_LEFT)
                else -> press(KeyEvent.KEYCODE_DPAD_DOWN)
            }
        }
        waitUntil(withId(R.id.settings_picture_editor_save), hasFocus())
    }

    /** From a dialog's first button to the one labeled [label]: Right, or Down where they're one below the other. */
    private fun pressToButton(label: Int) {
        pressInDialog(KeyEvent.KEYCODE_DPAD_RIGHT)
        if (!matchesNow(withText(label), hasFocus(), isDialog())) pressInDialog(KeyEvent.KEYCODE_DPAD_DOWN)
        waitUntil(withText(label), hasFocus(), inDialog = true)
    }

    /**
     * Waits until no picker is left that isn't finishing. Not until it's destroyed: with nothing of
     * Luncher's under it, Android may destroy it only after its idle timeout, 10 s (as the
     * background editor's test).
     */
    private fun waitUntilFinishing() {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (true) {
            var open = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val monitor = ActivityLifecycleMonitorRegistry.getInstance()
                open = Stage.values().filter { it != Stage.DESTROYED }.any { stage ->
                    monitor.getActivitiesInStage(stage).any { it is TileBannerActivity && !it.isFinishing }
                }
            }
            if (!open) return
            if (SystemClock.uptimeMillis() > deadline) {
                throw AssertionError("the picker wasn't closed in ${TIMEOUT_MS / 1000} s").also(FailureCapture::dump)
            }
            SystemClock.sleep(POLL_MS)
        }
    }

    /** The tiles' pictures prepared so far, each for which app and from what: read on the main thread, as the home screen reads them. */
    private fun preparedTiles(): List<Pair<com.luncher.domain.apps.LaunchableApp, TileDrawing>> {
        var tiles = emptyList<Pair<com.luncher.domain.apps.LaunchableApp, TileDrawing>>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { tiles = prepared.prepared.toList() }
        return tiles
    }

    @Test
    fun tileBannerInATilesMenu_choosingAPictureAndSave_putsItOnTheTile() {
        useTheTestsGraph()
        ActivityScenario.launch(HomeActivity::class.java).use {
            waitUntil(withContentDescription("Movies"), hasFocus())
            longPressOk()
            for (entry in listOf(HomeR.string.home_menu_move, HomeR.string.home_menu_hide, HomeR.string.home_menu_rename)) {
                waitUntilMenuEntryFocused(entry)
                pressInDialog(KeyEvent.KEYCODE_DPAD_DOWN)
            }
            waitUntilMenuEntryFocused(HomeR.string.home_menu_tile_banner)
            pressInDialog(KeyEvent.KEYCODE_DPAD_CENTER)

            // The picker, on Original banner, as the tile has no picture.
            waitUntil(row(R.string.settings_tile_banner_original), hasFocus())
            waitUntil(withId(R.id.settings_tile_banner_name), withText("Movies"))
            waitForFocus(TileBannerActivity::class.java)
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            choose("beach.png")
            focusSave()
            press(KeyEvent.KEYCODE_DPAD_CENTER)

            waitUntilDestroyed(TileBannerActivity::class.java)
            waitUntil(withContentDescription("Movies"), hasFocus())
            assertEquals(mapOf(movies.launchable to AppCustomization(image = TileImage(beach.id))), customizations.customizations)
            // Prepared at Save, at the home screen's tiles' size, which reads it.
            val size = application.homeTileSize(topLeftTiles)
            assertEquals(listOf(movies.launchable to TileDrawing(beach.id, Framing(), TileFocusShape.TILE, size)), preparedTiles())
        }
    }

    // The look's fields, below Fit and Position, which a picture of a tile's shape dims: Left from the
    // list reaches Focus, beside it, and Left again Backdrop; Down from Backdrop, Save.
    @Test
    fun aSeeThroughPicture_theLooksFieldsTakeTheKeys_andSaveStoresTheLook() {
        useTheTestsGraph()
        ActivityScenario.launch<TileBannerActivity>(TileBannerActivity.intent(application, movies.launchable)).use {
            waitUntil(row(R.string.settings_tile_banner_original), hasFocus())
            waitForFocus(TileBannerActivity::class.java)
            for (name in listOf("beach.png", "sunset.jpg", "zebra-logo.png")) {
                press(KeyEvent.KEYCODE_DPAD_DOWN)
                waitUntil(row(name), hasFocus())
            }
            choose("zebra-logo.png")

            press(KeyEvent.KEYCODE_DPAD_LEFT)
            waitUntil(option(R.string.settings_tile_banner_focus, R.string.settings_tile_banner_tile), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(listed(R.string.settings_tile_banner_tile), hasFocus(), inList = true)
            pressInList(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(listed(R.string.settings_tile_banner_shape), hasFocus(), inList = true)
            pressInList(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(option(R.string.settings_tile_banner_focus, R.string.settings_tile_banner_shape), hasFocus())

            press(KeyEvent.KEYCODE_DPAD_LEFT)
            waitUntil(option(R.string.settings_tile_banner_backdrop, R.string.settings_tile_banner_light_glass), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(listed(R.string.settings_tile_banner_light_glass), hasFocus(), inList = true)
            pressInList(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(listed(R.string.settings_tile_banner_dark_glass), hasFocus(), inList = true)
            pressInList(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(listed(R.string.settings_tile_banner_frosted), hasFocus(), inList = true)
            pressInList(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(option(R.string.settings_tile_banner_backdrop, R.string.settings_tile_banner_frosted), hasFocus())

            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(withId(R.id.settings_picture_editor_save), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntilFinishing()
            assertEquals(
                mapOf(movies.launchable to AppCustomization(image = TileImage(logo.id, look = TileLook(TileBackdrop.FROSTED, TileFocusShape.SHAPE)))),
                customizations.customizations,
            )
        }
    }

    // Back reaches the picker as Android 16 sends it too (OnBackInvokedCallback), not only onBackPressed.
    @Test
    fun backWithTheDraftChanged_asksFirst_cancelStays_discardLeavesTheTileAsItWas() {
        val stored = mapOf(movies.launchable to AppCustomization(image = TileImage(beach.id)))
        useTheTestsGraph(stored)
        ActivityScenario.launch<TileBannerActivity>(TileBannerActivity.intent(application, movies.launchable)).use {
            waitUntil(row("beach.png"), hasFocus())
            waitForFocus(TileBannerActivity::class.java)
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            choose("sunset.jpg")

            press(KeyEvent.KEYCODE_BACK)
            waitUntil(withText(R.string.settings_picture_editor_discard_question), isDisplayed(), inDialog = true)
            waitUntil(withText(R.string.settings_pictures_cancel), hasFocus(), inDialog = true)
            pressInDialog(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(row("sunset.jpg"), hasFocus())

            press(KeyEvent.KEYCODE_BACK)
            waitUntil(withText(R.string.settings_pictures_cancel), hasFocus(), inDialog = true)
            pressToButton(R.string.settings_picture_editor_discard)
            pressInDialog(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntilFinishing()
            assertEquals("nothing stored", stored, customizations.customizations)
        }
    }

    private companion object {
        const val TILE = 0xFF224466.toInt()
        const val PICTURE = 0xFF336699.toInt()
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 50L

        /** In rows from the top left, as the home screen's tests have them. */
        val topLeftTiles = TileGeometry(
            verticalAlignment = VerticalAlignment.TOP,
            horizontalAlignment = HorizontalAlignment.LEFT,
            fullBottomRow = false,
        )
    }
}
