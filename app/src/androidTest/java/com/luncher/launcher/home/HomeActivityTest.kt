package com.luncher.launcher.home

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.HorizontalAlignment
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.VerticalAlignment
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeAppCustomizations
import com.luncher.domain.apps.FakeAppCustomizations.Companion.named
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.wallpaper.FakeStoredWallpaper
import com.luncher.domain.wallpaper.Wallpaper
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.settings.HideAppsActivity
import com.luncher.launcher.settings.R as SettingsR
import com.luncher.launcher.settings.SettingsActivity
import com.luncher.launcher.testing.RetryWhenCovered
import com.luncher.launcher.testing.focus
import com.luncher.launcher.testing.longPressOk
import com.luncher.launcher.testing.matchesNow
import com.luncher.launcher.testing.resolvedHome
import com.luncher.launcher.testing.waitForHomeScreen
import com.luncher.launcher.testing.waitUntilDestroyed
import com.luncher.launcher.testing.waitUntilMatches
import com.luncher.launcher.ui.ListEntry
import com.luncher.launcher.wallpaper.BlurredWallpaperFrost
import com.luncher.launcher.ui.R as UiR
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The home screen on a real device, driven by real key events. */
@RunWith(AndroidJUnit4::class)
class HomeActivityTest {

    @get:Rule
    val retryWhenCovered = RetryWhenCovered()

    private val application = ApplicationProvider.getApplicationContext<LuncherApplication>()

    /** What the user named the apps in the test: not the device's, which a rename would change for the tests after it. */
    private var customizations = FakeAppCustomizations()

    private fun launchWith(vararg names: String, appearance: Appearance = Appearance(topLeftTiles)): ActivityScenario<HomeActivity> {
        application.graph = object : AppGraph(application) {
            override val installedApps = FakeInstalledApps(names.map { app(it) })
            // Not the device's stored arrangement, names and appearance: every test starts with
            // all apps shown, by name, where the test says.
            override val appArrangements = FakeAppArrangements()
            override val appCustomizations = this@HomeActivityTest.customizations
            override val appearanceSettings = FakeAppearanceSettings(appearance)
            // Not the device's library and kept wallpaper either: a wallpaper is drawn as a plain
            // color, and kept in memory.
            override val wallpaper = Wallpaper(
                FakeStoredWallpaper(),
                FakePictureImages { _, width, height, _ ->
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(WALLPAPER) }
                },
                BlurredWallpaperFrost(),
                appearanceSettings,
                jobs,
            ) { Handler(Looper.getMainLooper()).post(it) }
        }
        return ActivityScenario.launch(HomeActivity::class.java)
    }

    /**
     * Closing the home screen at the end of a test can hang while Luncher is the device's home app:
     * Android starts it again at once, and the closing activity may stay paused. Fail at once with
     * the fix instead of a timeout that looks like Luncher's fault; see docs/INSTRUMENTED-TESTS.md.
     */
    @Before
    fun checkLuncherIsNotTheHomeApp() {
        assertNotEquals(
            "Luncher is the device's home app; re-enable the stock launcher first (docs/INSTRUMENTED-TESTS.md)",
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

    /** Sends a key to a tile's menu, or the dialog it opened, a window of its own over the home screen. */
    private fun pressInMenu(keyCode: Int) {
        onView(isRoot()).inRoot(isDialog()).perform(pressKey(keyCode))
    }

    /**
     * Waits until the entry labeled [label] of a tile's menu has the focus: the menu is up and gets
     * the keys. The entry has the focus, not its label.
     */
    private fun waitUntilMenuEntryFocused(label: Int) =
        waitUntilMatches(allOf(isAssignableFrom(ListEntry::class.java), hasDescendant(allOf(withId(R.id.home_tile_menu_label), withText(label)))), hasFocus(), TIMEOUT_MS, root = isDialog())

    /** Checks [condition] on the tile labeled [label], retrying while an animation settles. */
    private fun waitUntilTile(label: String, condition: Matcher<View>) = waitUntil(withContentDescription(label), condition)

    /** Checks [condition] on the view [view] matches, retrying while an animation settles or a window comes up. */
    private fun waitUntil(view: Matcher<View>, condition: Matcher<View>) = waitUntilMatches(view, condition, TIMEOUT_MS)

    /** The settings panel's entry named [title]: the entry has the focus, not its name. */
    private fun settingsEntry(title: Int) =
        allOf(isAssignableFrom(ListEntry::class.java), hasDescendant(allOf(withId(SettingsR.id.settings_entry_title), withText(title))))

    /**
     * Waits until the settings panel is in front again with the input focus, on its Hide apps
     * entry, after Back has closed the Hide apps list over it, so that the next key goes to the
     * panel. Espresso's Back waits only briefly for the list's activity to pause (150 ms, then
     * 600 ms for the activities' transitions) and then returns anyway; on a slow device the next
     * Back can then still find the list resumed, its window focused, and be lost on it, leaving the
     * panel open. Espresso checks a view only once its window has the focus, which from API 30 on
     * the app learns from the input dispatcher, where keys go, so it isn't ahead of it. Fails after
     * [TIMEOUT_MS], saying what has the focus.
     */
    private fun waitForTheSettingsPanel() {
        try {
            waitUntil(settingsEntry(SettingsR.string.settings_hide_apps), hasFocus())
        } catch (e: Throwable) {
            throw AssertionError("The settings panel didn't get the focus back in ${TIMEOUT_MS / 1000} s: ${focus()}", e)
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

    // Android's own search would go on to the settings entry, which on a 1080p TV ends a pixel
    // right of the last app, or from the short row's end up to the first row.
    @Test
    fun dpadRight_atTheEndOfARow_keepsTheFocusThere() {
        launchWith(*appNames(7)).use {
            waitUntilTile("App01", hasFocus())
            repeat(4) { press(KeyEvent.KEYCODE_DPAD_RIGHT) }
            waitUntilTile("App05", hasFocus())

            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            waitUntilTile("App05", hasFocus())
            waitUntil(withId(R.id.home_settings), not(hasFocus()))

            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntilTile("App07", hasFocus())
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            waitUntilTile("App07", hasFocus())
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

    // Two apps centered under five are half a column off. Down takes the nearest app, the left one
    // of two as near, and Up goes back where the focus came from, though another is nearer.
    @Test
    fun dpadDownAndUp_withACenteredRow_takeTheNearestApp_andGoBack() {
        val centered = Appearance(topLeftTiles.copy(horizontalAlignment = HorizontalAlignment.CENTER))
        launchWith(*appNames(7), appearance = centered).use {
            waitUntilTile("App01", hasFocus())
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            waitUntilTile("App03", hasFocus())

            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntilTile("App06", hasFocus())   // App06 and App07 as near
            press(KeyEvent.KEYCODE_DPAD_UP)
            waitUntilTile("App03", hasFocus())

            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            waitUntilTile("App05", hasFocus())
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntilTile("App07", hasFocus())
            press(KeyEvent.KEYCODE_DPAD_UP)
            waitUntilTile("App05", hasFocus())   // not App03, the nearest
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

    // Android's own search would go on to the tile below the settings entry.
    @Test
    fun dpadLeftAndRight_onTheSettingsEntry_keepTheFocusThere() {
        launchWith("games", "movies", "music").use {
            waitUntilTile("Games", hasFocus())
            press(KeyEvent.KEYCODE_DPAD_UP)
            waitUntil(withId(R.id.home_settings), hasFocus())

            press(KeyEvent.KEYCODE_DPAD_LEFT)
            waitUntil(withId(R.id.home_settings), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            waitUntil(withId(R.id.home_settings), hasFocus())

            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(withId(R.id.home_settings), not(hasFocus()))
        }
    }

    @Test
    fun okOnTheSettingsEntry_opensTheSettingsPanel() {
        launchWith("games", "movies").use {
            waitUntilTile("Games", hasFocus())

            press(KeyEvent.KEYCODE_DPAD_UP)
            press(KeyEvent.KEYCODE_DPAD_CENTER)

            waitUntil(settingsEntry(SettingsR.string.settings_hide_apps), allOf(isDisplayed(), hasFocus()))
        }
    }

    @Test
    fun hidingAnAppInTheSettings_removesItsTile() {
        launchWith("games", "movies", "music").use {
            waitUntilTile("Games", hasFocus())

            press(KeyEvent.KEYCODE_MENU)                    // the settings panel
            waitUntil(settingsEntry(SettingsR.string.settings_hide_apps), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)             // Hide apps, with Games selected
            waitUntil(withId(SettingsR.id.settings_apps), isDisplayed())
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            press(KeyEvent.KEYCODE_DPAD_CENTER)             // hides Movies
            waitUntil(allOf(withId(SettingsR.id.settings_app_hidden), hasSibling(hasDescendant(withText("Movies")))), isDisplayed())
            pressBack()                                     // closes Hide apps
            waitUntilDestroyed(HideAppsActivity::class.java)
            waitForTheSettingsPanel()                       // so the next Back goes there
            pressBack()                                     // closes the panel
            waitUntilDestroyed(SettingsActivity::class.java)

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

            waitUntil(withText(SettingsR.string.settings_system), isDisplayed())
        }
    }

    @Test
    fun backKey_closesTheSettingsPanel_withFocusBackOnTheSettingsEntry() {
        launchWith("games", "movies").use { scenario ->
            waitUntilTile("Games", hasFocus())
            press(KeyEvent.KEYCODE_DPAD_UP)
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(withText(SettingsR.string.settings_system), isDisplayed())

            pressBack()
            waitUntilDestroyed(SettingsActivity::class.java)

            waitUntil(withId(R.id.home_settings), hasFocus())
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    /** The labels of the tiles, in order. */
    private fun ActivityScenario<HomeActivity>.labels(): List<String> {
        var labels = emptyList<String>()
        onActivity { activity ->
            // Each tile's description is its app's label; the tiles' views are the home module's own.
            val tiles = activity.findViewById<ViewGroup>(R.id.home_apps)
            labels = (0 until tiles.childCount).map { tiles.getChildAt(it).contentDescription.toString() }
        }
        return labels
    }

    @Test
    fun longPressOk_opensTheTilesMenu_andBackClosesIt() {
        launchWith("games", "movies", "music").use { scenario ->
            waitUntilTile("Games", hasFocus())

            longPressOk()
            waitUntilMenuEntryFocused(R.string.home_menu_move)
            onView(withText(R.string.home_menu_hide)).inRoot(isDialog()).check(matches(isDisplayed()))
            pressInMenu(KeyEvent.KEYCODE_BACK)

            waitUntilTile("Games", hasFocus())
            assertEquals(listOf("Games", "Movies", "Music"), scenario.labels())
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun moveInTheTilesMenu_holdsTheApp_arrowsMoveIt_andOkPutsItDown() {
        launchWith("games", "movies", "music").use { scenario ->
            waitUntilTile("Games", hasFocus())
            longPressOk()
            waitUntilMenuEntryFocused(R.string.home_menu_move)

            pressInMenu(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(withId(R.id.home_move_title), isDisplayed())
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            press(KeyEvent.KEYCODE_DPAD_CENTER)   // put it down

            waitUntil(withId(R.id.home_settings), isDisplayed())
            assertEquals(listOf("Movies", "Games", "Music"), scenario.labels())
            waitUntilTile("Games", hasFocus())
        }
    }

    @Test
    fun backKey_whileMovingAnApp_putsItDown() {
        launchWith("games", "movies", "music").use { scenario ->
            waitUntilTile("Games", hasFocus())
            longPressOk()
            waitUntilMenuEntryFocused(R.string.home_menu_move)
            pressInMenu(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(withId(R.id.home_move_title), isDisplayed())

            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            pressBack()

            waitUntil(withId(R.id.home_settings), isDisplayed())
            assertEquals(listOf("Movies", "Games", "Music"), scenario.labels())
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun hideInTheTilesMenu_removesTheTile() {
        launchWith("games", "movies", "music").use { scenario ->
            waitUntilTile("Games", hasFocus())
            longPressOk()
            waitUntilMenuEntryFocused(R.string.home_menu_move)

            pressInMenu(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntilMenuEntryFocused(R.string.home_menu_hide)
            pressInMenu(KeyEvent.KEYCODE_DPAD_CENTER)

            waitUntilTile("Movies", hasFocus())   // the tile now in its place
            assertEquals(listOf("Movies", "Music"), scenario.labels())
        }
    }

    /** Rename in the menu of the focused tile, Move first in it: waits until the dialog's field has the focus. */
    private fun openRename() {
        longPressOk()
        waitUntilMenuEntryFocused(R.string.home_menu_move)
        pressInMenu(KeyEvent.KEYCODE_DPAD_DOWN)
        waitUntilMenuEntryFocused(R.string.home_menu_hide)
        pressInMenu(KeyEvent.KEYCODE_DPAD_DOWN)
        waitUntilMenuEntryFocused(R.string.home_menu_rename)
        pressInMenu(KeyEvent.KEYCODE_DPAD_CENTER)
        waitUntilMatches(withId(UiR.id.dialog_field), hasFocus(), TIMEOUT_MS, root = isDialog())
    }

    @Test
    fun renameInTheTilesMenu_typedAndDone_namesTheTile() {
        launchWith("games", "movies", "music").use { scenario ->
            waitUntilTile("Games", hasFocus())
            openRename()

            // Typed as the keyboard would, and its Done.
            onView(withId(UiR.id.dialog_field)).inRoot(isDialog()).perform(replaceText("Zulu"), pressImeActionButton())

            waitUntilTile("Zulu", hasFocus())   // moved to its place by name, the focus with it
            assertEquals(listOf("Movies", "Music", "Zulu"), scenario.labels())
            assertEquals(mapOf(named(app("games"), "Zulu")), customizations.customizations)
        }
    }

    // As a USB or Bluetooth keyboard sends it: before Android 11 the field moved the focus on.
    @Test
    fun enterKey_inTheRenameDialog_namesTheTile() {
        launchWith("games", "movies", "music").use { scenario ->
            waitUntilTile("Games", hasFocus())
            openRename()
            onView(withId(UiR.id.dialog_field)).inRoot(isDialog()).perform(replaceText("Zulu"), closeSoftKeyboard())
            pressInMenu(KeyEvent.KEYCODE_MOVE_END)

            pressInMenu(KeyEvent.KEYCODE_ENTER)

            waitUntilTile("Zulu", hasFocus())
            assertEquals(listOf("Movies", "Music", "Zulu"), scenario.labels())
            assertEquals(mapOf(named(app("games"), "Zulu")), customizations.customizations)
        }
    }

    // The keyboard closed first: up, Back would close it, not the dialog.
    @Test
    fun backKey_inTheRenameDialog_closesIt_andChangesNothing() {
        launchWith("games", "movies", "music").use { scenario ->
            waitUntilTile("Games", hasFocus())
            openRename()
            onView(withId(UiR.id.dialog_field)).inRoot(isDialog()).perform(replaceText("Zulu"), closeSoftKeyboard())

            pressInMenu(KeyEvent.KEYCODE_BACK)

            waitUntilTile("Games", hasFocus())
            assertEquals(listOf("Games", "Movies", "Music"), scenario.labels())
            assertEquals(emptyMap<Any, Any>(), customizations.customizations)
        }
    }

    @Test
    fun resetInTheRenameDialog_givesTheAppItsOriginalName() {
        customizations = FakeAppCustomizations(mapOf(named(app("games"), "Zulu")))
        launchWith("games", "movies", "music").use { scenario ->
            waitUntilTile("Movies", hasFocus())
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            waitUntilTile("Zulu", hasFocus())
            openRename()
            onView(withId(UiR.id.dialog_field)).inRoot(isDialog()).perform(closeSoftKeyboard())

            // Below the field, Cancel, Reset and Rename side by side: Reset in the middle.
            pressInMenu(KeyEvent.KEYCODE_DPAD_DOWN)
            when {
                matchesNow(withText(R.string.home_rename_cancel), hasFocus(), isDialog()) -> pressInMenu(KeyEvent.KEYCODE_DPAD_RIGHT)
                matchesNow(withText(R.string.home_rename_confirm), hasFocus(), isDialog()) -> pressInMenu(KeyEvent.KEYCODE_DPAD_LEFT)
            }
            waitUntilMatches(withText(R.string.home_rename_reset), hasFocus(), TIMEOUT_MS, root = isDialog())
            pressInMenu(KeyEvent.KEYCODE_DPAD_CENTER)

            waitUntilTile("Games", hasFocus())
            assertEquals(listOf("Games", "Movies", "Music"), scenario.labels())
            assertEquals(emptyMap<Any, Any>(), customizations.customizations)
        }
    }

    /** "app01" to "app<count>", zero-padded so label order is number order. */
    private fun appNames(count: Int) = Array(count) { "app%02d".format(it + 1) }

    @Test
    fun aWallpaper_isTheHomeScreensBackground() {
        val wallpaper = Appearance(topLeftTiles, background = Background(picture = PictureId("sunset")))
        launchWith("movies", appearance = wallpaper).use { scenario ->
            val deadline = SystemClock.uptimeMillis() + 10_000
            var color = 0
            while (SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { color = ((it.window.decorView.background as? BitmapDrawable)?.bitmap?.getPixel(10, 10)) ?: 0 }
                if (color == WALLPAPER) break
                SystemClock.sleep(50)
            }
            assertEquals("the wallpaper drawn, as the window's background", WALLPAPER, color)
        }
    }

    private companion object {
        /** The color the tests' wallpapers are drawn in. */
        const val WALLPAPER = 0xFF336699.toInt()

        /**
         * The default tile size, in rows from the top left with the short row last, so a tile's
         * index tells its row and column: what these tests of keys and focus are written for.
         */
        val topLeftTiles = TileGeometry(
            verticalAlignment = VerticalAlignment.TOP,
            horizontalAlignment = HorizontalAlignment.LEFT,
            fullBottomRow = false,
        )

        const val TIMEOUT_MS = 5_000L
    }
}
