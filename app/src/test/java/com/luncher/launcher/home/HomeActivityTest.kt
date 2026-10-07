package com.luncher.launcher.home

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.apps.LaunchableApp
import com.luncher.domain.clock.FakeClock
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.R
import com.luncher.launcher.TV_1080P
import com.luncher.launcher.settings.SettingsActivity
import org.junit.Assert.assertEquals
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
        val application = RuntimeEnvironment.getApplication() as LuncherApplication
        application.graph = object : AppGraph(application) {
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
        )

        assertEquals(listOf("Movies", "Music", "News"), start().get().labels())
    }

    @Test
    fun `starts with the first app focused`() {
        assertEquals("Movies", start().get().focusedLabel())
    }

    @Test
    fun `starts on the launch screen's theme, then draws on the plain background`() {
        val activity = start().get()

        assertEquals(R.style.Theme_Luncher_Launch, activity.packageManager.getActivityInfo(activity.componentName, 0).theme)
        val background = activity.window.decorView.background
        assertTrue("window background: $background", background is ColorDrawable)
        assertEquals(activity.getColor(R.color.background), (background as ColorDrawable).color)
    }

    // Right-to-left isn't supported yet: the tiles fill from the left, and arrange mode's Left and
    // Right go along their order. So in an RTL language everything stays left to right, rather
    // than a top bar mirrored over tiles that aren't.
    @Test
    @Config(qualifiers = "ar-rEG-ldrtl-$TV_1080P")
    fun `lays out left to right in right-to-left languages too`() {
        val activity = start().get()

        assertEquals(View.LAYOUT_DIRECTION_LTR, activity.window.decorView.layoutDirection)
        val clock = activity.findViewById<View>(R.id.home_clock)
        assertTrue("clock at ${clock.left}, settings at ${activity.settingsEntry().left}", clock.left < activity.settingsEntry().left)
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

    @Test
    fun `says where to show apps again when all of them are hidden`() {
        arrangements.arrangement = AppArrangement(order = null, hidden = installedApps.apps.map { it.launchable })

        val activity = start().get()

        assertEquals(emptyList<String>(), activity.labels())
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.home_empty).visibility)
        assertEquals(activity.getString(R.string.home_all_hidden), activity.emptyText())
    }

    @Test
    fun `the message follows when the last shown app is hidden while there were no tiles`() {
        installedApps.apps = emptyList()
        val controller = start()

        controller.pause().stop()
        installedApps.apps = listOf(app("movies"))  // installed...
        arrangements.arrangement = AppArrangement(order = null, hidden = listOf(app("movies").launchable))  // ...and hidden
        controller.restart().start().resume()

        assertEquals(controller.get().getString(R.string.home_all_hidden), controller.get().emptyText())
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
