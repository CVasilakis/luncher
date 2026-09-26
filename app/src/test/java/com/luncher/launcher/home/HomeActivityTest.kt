package com.luncher.launcher.home

import android.content.Intent
import android.view.KeyEvent
import android.view.View
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.apps.LaunchableApp
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.R
import com.luncher.launcher.TV_1080P
import org.junit.Assert.assertEquals
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

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TV_1080P)
class HomeActivityTest {

    private val installedApps = FakeInstalledApps(app("news"), app("movies"), app("music"))

    @Before
    fun useFakeApps() {
        // Robolectric creates a fresh application per test, so there's nothing to restore.
        val application = RuntimeEnvironment.getApplication() as LuncherApplication
        application.graph = object : AppGraph(application) {
            override val installedApps = this@HomeActivityTest.installedApps
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
    }
}
