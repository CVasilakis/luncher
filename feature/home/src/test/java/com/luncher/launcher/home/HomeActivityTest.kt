package com.luncher.launcher.home

import android.content.Intent
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.apps.LaunchableApp
import com.luncher.domain.clock.FakeClock
import com.luncher.launcher.settings.SettingsActivity
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TV_1080P)
class HomeActivityTest {

    private val installedApps = FakeInstalledApps(app("news"), app("movies"), app("music"))
    private val arrangements = FakeAppArrangements()
    private val clock = FakeClock()

    @Before
    fun useFakeApps() {
        // Robolectric creates a fresh application per test, so there's nothing to restore.
        val application = RuntimeEnvironment.getApplication() as HomeTestApplication
        application.graph = object : TestHomeGraph(application) {
            override val installedApps = this@HomeActivityTest.installedApps
            override val appArrangements = this@HomeActivityTest.arrangements
            override val clock = this@HomeActivityTest.clock
        }
    }

    private fun start() = Robolectric.buildActivity(HomeActivity::class.java).setup()

    private fun HomeActivity.tiles(): List<AppTileView> {
        val tiles = findViewById<AppTilesView>(R.id.home_apps)
        return (0 until tiles.childCount).map { tiles.getChildAt(it) as AppTileView }
    }

    private fun HomeActivity.labels() = tiles().map { it.app.label }

    // Not currentFocus: under Robolectric the window never gets focus, so that stays null.
    private fun HomeActivity.focusedLabel() = tiles().single { it.isFocused }.app.label

    private fun HomeActivity.settingsEntry() = findViewById<View>(R.id.home_settings)

    private fun HomeActivity.press(keyCode: Int) {
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    @Test
    fun `shows a tile for each app, sorted by label`() {
        assertEquals(listOf("Movies", "Music", "News"), start().get().labels())
    }

    @Test
    fun `leaves out Luncher itself`() {
        val application = RuntimeEnvironment.getApplication()
        installedApps.apps += InstalledApp(
            LaunchableApp(application.packageName, HomeActivity::class.java.name),
            label = "Luncher",
            hasBanner = true,
            updated = 0,
        )

        assertEquals(listOf("Movies", "Music", "News"), start().get().labels())
    }

    @Test
    fun `starts with the first app focused`() {
        assertEquals("Movies", start().get().focusedLabel())
    }

    // From the theme in the manifest, which Android reads before any of Luncher's code runs.
    @Test
    fun `asks for no starting window, so the previous screen stays until it draws`() {
        val activity = start().get()

        val theme = activity.resources.newTheme()
        theme.applyStyle(activity.packageManager.getActivityInfo(activity.componentName, 0).themeResource, true)
        val values = theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowDisablePreview))
        val disabled = values.getBoolean(0, false)
        values.recycle()
        assertTrue(disabled)
    }

    // Where the clock, the settings entry and the tiles go is for settings to decide, not the
    // language: only the settings panels mirror (HideAppsActivityTest).
    @Test
    @Config(qualifiers = "ar-rEG-$TV_1080P")
    fun `stays left to right in right-to-left languages`() {
        val activity = start().get()

        assertEquals(View.LAYOUT_DIRECTION_RTL, activity.window.decorView.layoutDirection)   // the window is mirrored
        val clock = activity.findViewById<View>(R.id.home_clock)
        assertTrue("clock at ${clock.left}, settings at ${activity.settingsEntry().left}", clock.left < activity.settingsEntry().left)
        assertEquals(listOf("Movies", "Music", "News"), activity.tiles().sortedBy { it.left }.map { it.app.label })
    }

    // Text keeps its own direction, which would put the Arabic title at the right of its view,
    // against the key hint.
    @Test
    @Config(qualifiers = "ar-rEG-$TV_1080P")
    fun `puts a right-to-left arrange title at the left too`() {
        val activity = start().get()
        val title = activity.findViewById<TextView>(R.id.home_arrange_title)

        title.visibility = View.VISIBLE
        shadowOf(Looper.getMainLooper()).idle()   // the layout pass

        assertEquals(0f, title.layout.getLineLeft(0))
    }

    @Test
    fun `OK opens the focused app in a task of its own`() {
        val activity = start().get()

        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals("com.example.movies", intent.component?.packageName)
        assertEquals("com.example.movies.MainActivity", intent.component?.className)
        assertTrue(intent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `OK on the settings entry opens the settings panel`() {
        val activity = start().get()
        activity.settingsEntry().requestFocus()

        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertEquals(SettingsActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    @Test
    fun `the Menu key opens the settings panel`() {
        val activity = start().get()

        activity.press(KeyEvent.KEYCODE_MENU)

        assertEquals(SettingsActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    @Test
    fun `the end of a Menu press that started elsewhere opens nothing`() {
        val activity = start().get()

        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU))

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `reads the apps again when the home screen comes back`() {
        val controller = start()

        controller.pause().stop()                   // the user opened an app...
        installedApps.apps += app("games")          // ...and installed another one meanwhile
        controller.restart().start().resume()

        assertEquals(listOf("Games", "Movies", "Music", "News"), controller.get().labels())
    }

    @Test
    fun `keeps focus on the same app when the apps change`() {
        val controller = start()
        controller.get().tiles().single { it.app.label == "News" }.requestFocus()

        controller.pause().stop()
        installedApps.apps += app("games")
        controller.restart().start().resume()

        assertEquals("News", controller.get().focusedLabel())
    }

    @Test
    fun `keeps focus on the settings entry when the apps change`() {
        val controller = start()
        controller.get().settingsEntry().requestFocus()

        controller.pause().stop()                   // e.g. in the system settings...
        installedApps.apps += app("games")          // ...an app was installed
        controller.restart().start().resume()

        assertTrue(controller.get().settingsEntry().isFocused)
    }

    @Test
    fun `keeps the same tiles when no app changed`() {
        val controller = start()
        val before = controller.get().tiles()

        controller.pause().stop()
        controller.restart().start().resume()

        before.zip(controller.get().tiles()).forEach { (old, new) -> assertSame(old, new) }
    }

    @Test
    fun `keeps the tiles of apps that are still installed`() {
        val controller = start()
        val movies = controller.get().tiles().single { it.app.label == "Movies" }

        controller.pause().stop()
        installedApps.apps += app("games")
        controller.restart().start().resume()

        assertSame(movies, controller.get().tiles().single { it.app.label == "Movies" })
    }

    @Test
    fun `shows an app installed or removed while it's shown, at once`() {
        val activity = start().get()

        installedApps.apps = installedApps.apps.filter { it.label != "News" } + app("games")

        assertEquals(listOf("Games", "Movies", "Music"), activity.labels())
    }

    @Test
    fun `doesn't watch the apps while hidden, and reads them when it comes back`() {
        val controller = start()

        controller.pause().stop()
        installedApps.apps += app("games")
        assertEquals(listOf("Movies", "Music", "News"), controller.get().labels())
        controller.restart().start().resume()

        assertEquals(listOf("Games", "Movies", "Music", "News"), controller.get().labels())
    }

    @Test
    fun `makes a new tile for an app updated since, as its banner may have changed`() {
        val controller = start()
        val movies = controller.get().tiles().single { it.app.label == "Movies" }

        controller.pause().stop()
        installedApps.apps = installedApps.apps.map { if (it.label == "Movies") it.copy(updated = 1) else it }
        controller.restart().start().resume()

        assertNotSame(movies, controller.get().tiles().single { it.app.label == "Movies" })
    }

    @Test
    fun `says so when there are no apps`() {
        installedApps.apps = emptyList()

        val activity = start().get()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.home_empty).visibility)
        assertEquals(activity.getString(R.string.home_no_apps), activity.emptyText())
    }

    @Test
    fun `leaves out hidden apps`() {
        arrangements.arrangement = AppArrangement(order = null, hidden = listOf(app("music").launchable))

        assertEquals(listOf("Movies", "News"), start().get().labels())
    }

    @Test
    fun `shows the apps in the user's order`() {
        val order = listOf(app("news"), app("music"), app("movies")).map { it.launchable }
        arrangements.arrangement = AppArrangement(order, hidden = emptyList())

        assertEquals(listOf("News", "Music", "Movies"), start().get().labels())
    }

    @Test
    fun `hides apps hidden in the settings when it comes back`() {
        val controller = start()

        controller.pause()                          // the settings panel opened over it...
        arrangements.arrangement = AppArrangement(order = null, hidden = listOf(app("movies").launchable))
        controller.resume()                         // ...and closed

        assertEquals(listOf("Music", "News"), controller.get().labels())
    }

    // The settings panel and Hide apps float over the home screen, which stays started behind them.
    @Test
    fun `hides an app hidden in the settings at once, behind the panel`() {
        val controller = start()

        controller.pause()                          // the settings panel opened over it
        arrangements.save(AppArrangement(order = null, hidden = listOf(app("movies").launchable)))

        assertEquals(listOf("Music", "News"), controller.get().labels())
    }

    @Test
    fun `follows the arrangement from start to stop`() {
        val controller = start()
        assertEquals(1, arrangements.listenerCount)

        controller.pause().stop()
        assertEquals(0, arrangements.listenerCount)
        arrangements.save(AppArrangement(order = null, hidden = listOf(app("movies").launchable)))
        assertEquals(listOf("Movies", "Music", "News"), controller.get().labels())   // not while hidden

        controller.restart().resume()   // restart() starts it too, as Android does
        assertEquals(1, arrangements.listenerCount)
        assertEquals(listOf("Music", "News"), controller.get().labels())
    }

    @Test
    fun `says where to show apps again when all of them are hidden`() {
        arrangements.arrangement = AppArrangement(order = null, hidden = installedApps.apps.map { it.launchable })

        val activity = start().get()

        assertEquals(emptyList<String>(), activity.labels())
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.home_empty).visibility)
        assertEquals("All apps are hidden. Open Settings → Hide apps to show them again.", activity.emptyText())
    }

    @Test
    fun `the message follows when the last shown app is hidden while there were no tiles`() {
        installedApps.apps = emptyList()
        val controller = start()

        controller.pause().stop()
        installedApps.apps = listOf(app("movies"))  // installed...
        arrangements.arrangement = AppArrangement(order = null, hidden = listOf(app("movies").launchable))  // ...and hidden
        controller.restart().start().resume()

        assertEquals("All apps are hidden. Open Settings → Hide apps to show them again.", controller.get().emptyText())
    }

    @Test
    fun `shows five apps in a row on a 16 by 9 TV`() {
        installedApps.apps = (1..12).map { app("app%02d".format(it)) }

        assertEquals(5, start().get().firstRowSize())
    }

    // 1080p at 160 dpi, as some TV boxes are set: 1920 dp wide, so twice as many tiles of about the
    // same size, rather than five twice as large next to the same clock.
    @Test
    @Config(qualifiers = "+w1920dp-h1080dp-mdpi")
    fun `fits more apps in a row on a screen wider in dp, at about the same size`() {
        installedApps.apps = (1..12).map { app("app%02d".format(it)) }
        val activity = start().get()

        assertEquals(10, activity.firstRowSize())
        assertEquals(160, activity.tiles().first().width)   // dp, at 160 dpi; 153 dp at 1080p
    }

    private fun HomeActivity.firstRowSize() = tiles().count { it.top == tiles().first().top }

    // Nothing else in the bar takes the focus, and Android's own search would go on to a tile,
    // which ends a pixel left of the settings entry's right edge on a 1080p TV.
    @Test
    fun `Left and Right on the settings entry stay in the top bar`() {
        val settings = start().get().settingsEntry()

        assertSame(settings, settings.focusSearch(View.FOCUS_LEFT))
        assertSame(settings, settings.focusSearch(View.FOCUS_RIGHT))
    }

    @Test
    fun `Down from the settings entry goes to the tiles`() {
        val activity = start().get()

        assertTrue(activity.settingsEntry().focusSearch(View.FOCUS_DOWN) in activity.tiles())
    }

    // Android's own search goes to the nearest view that way anywhere: past the end of a row, to a
    // tile in another row, or to the settings entry, which on a 1080p TV ends a pixel further right
    // than the last tile, as the tiles' width rounds down.
    @Test
    fun `Left and Right stay in the row, and do nothing at its ends`() {
        installedApps.apps = (1..7).map { app("app%02d".format(it)) }   // five, then two
        val tiles = start().get().tiles()

        assertSame(tiles[1], tiles[0].focusSearch(View.FOCUS_RIGHT))
        assertSame(tiles[3], tiles[4].focusSearch(View.FOCUS_LEFT))
        assertSame(tiles[4], tiles[4].focusSearch(View.FOCUS_RIGHT))   // not the settings entry
        assertSame(tiles[6], tiles[6].focusSearch(View.FOCUS_RIGHT))   // not up to the first row
        assertSame(tiles[5], tiles[5].focusSearch(View.FOCUS_LEFT))
        assertSame(tiles[0], tiles[0].focusSearch(View.FOCUS_LEFT))
    }

    // A TV switched from 1080p to 720p (HDMI), or a new language, recreates the activity.
    @Test
    fun `after a configuration change, shows the same apps at the new screen's size`() {
        val controller = start()
        val before = controller.get().tiles().first().width

        RuntimeEnvironment.setQualifiers("+tvdpi")   // 960x540 dp at 720p
        controller.configurationChange().visible()   // Robolectric shows the new window only when told

        val activity = controller.get()
        assertEquals(listOf("Movies", "Music", "News"), activity.labels())
        assertEquals("Movies", activity.focusedLabel())
        val after = activity.tiles().first().width
        assertEquals(before * 213 / 320.0, after.toDouble(), 1.0)   // the density's share
        assertEquals("the old screen's clock stopped, the new one's started", 1, clock.listenerCount)
    }

    @Test
    fun `with many apps, focus reaches the last row, scrolled into view, and comes back to the top`() {
        installedApps.apps = (1..150).map { app("app%03d".format(it)) }
        val activity = start().get()
        val grid = activity.findViewById<AppTilesView>(R.id.home_apps)
        assertEquals(150, activity.tiles().size)

        repeat(29) { activity.moveFocus(View.FOCUS_DOWN) }
        assertEquals("App146", activity.focusedLabel())   // the first of the 30th row
        assertInView(grid, activity.tiles()[145])

        repeat(29) { activity.moveFocus(View.FOCUS_UP) }
        assertEquals("App001", activity.focusedLabel())
        assertEquals(0, grid.scrollY)
    }

    @Test
    fun `with apps installed since, focus still reaches the last row, scrolled into view`() {
        val controller = start()   // three apps, one row
        controller.pause().stop()
        installedApps.apps = (1..150).map { app("app%03d".format(it)) }
        controller.restart().start().resume()
        shadowOf(Looper.getMainLooper()).idle()   // the layout pass for the new tiles
        val activity = controller.get()

        repeat(29) { activity.moveFocus(View.FOCUS_DOWN) }

        assertEquals("App146", activity.focusedLabel())
        assertInView(activity.findViewById(R.id.home_apps), activity.tiles()[145])
    }

    // The tiles' layout is kept while nothing it's made from changes; a new width is one such change.
    @Test
    fun `lays the tiles out again for a new width`() {
        installedApps.apps = (1..12).map { app("app%02d".format(it)) }
        val activity = start().get()
        val grid = activity.findViewById<AppTilesView>(R.id.home_apps)
        val width = grid.width / 2

        grid.measure(exactly(width), exactly(grid.height))
        grid.layout(0, 0, width, grid.height)

        assertTrue(activity.firstRowSize() < 5)
        val right = activity.tiles().maxOf { it.right }
        assertTrue("tiles reach $right, the space ends at ${width - grid.paddingRight}", right <= width - grid.paddingRight)
    }

    @Test
    fun `lays the tiles out again for a new tile geometry`() {
        installedApps.apps = (1..12).map { app("app%02d".format(it)) }
        val activity = start().get()
        val grid = activity.findViewById<AppTilesView>(R.id.home_apps)
        val width = activity.tiles().first().width

        grid.tileGeometry = TileGeometry(tileWidthDp = 300)
        shadowOf(Looper.getMainLooper()).idle()   // the layout pass

        assertEquals(3, activity.firstRowSize())
        assertTrue(activity.tiles().first().width > width)
    }

    // Compared by value: an equal geometry, as each read of the appearance gives, costs no layout pass.
    @Test
    fun `keeps the tiles' layout for an equal tile geometry`() {
        val activity = start().get()
        val grid = activity.findViewById<AppTilesView>(R.id.home_apps)

        grid.tileGeometry = TileGeometry()

        assertFalse(grid.isLayoutRequested)
    }

    private fun exactly(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    // Robolectric moves focus only when told, and draws no frames, so the scroll animation is run to
    // its end here: the time it takes, then computeScroll as a frame would.
    private fun HomeActivity.moveFocus(direction: Int) {
        val focused = tiles().single { it.isFocused }
        assertTrue(focused.focusSearch(direction)!!.requestFocus())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        findViewById<AppTilesView>(R.id.home_apps).computeScroll()
    }

    private fun assertInView(grid: AppTilesView, tile: View) {
        val top = tile.top - grid.scrollY
        val bottom = tile.bottom - grid.scrollY
        assertTrue("tile at $top to $bottom, view 0 to ${grid.height}", top >= 0 && bottom <= grid.height - grid.paddingBottom)
    }

    private fun HomeActivity.emptyText() = findViewById<TextView>(R.id.home_empty).text.toString()
}
