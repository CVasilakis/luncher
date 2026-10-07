package com.luncher.launcher.home

import android.graphics.RectF
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.clock.FakeClock
import com.luncher.launcher.testing.TV_SCREENS
import com.luncher.launcher.testing.TvScreen
import com.luncher.launcher.testing.assertApart
import com.luncher.launcher.testing.assertInside
import com.luncher.launcher.testing.assertWhole
import com.luncher.launcher.testing.bounds
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * The home screen's layout on every screen of [TV_SCREENS]: everything inside the TV's overscan
 * margin, nothing overlapping, no text cut, and tiles of about the size they're meant to be.
 * With the longest time and date English has, 12 apps shown and 3 hidden.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // real text widths; without it a character is 1 px wide
@Config(sdk = [33, Config.TARGET_SDK])    // 33 for API 22 to 33, which scale large text more (TvDevice.kt)
class HomeLayoutTest(private val screen: TvScreen) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens() = TV_SCREENS.map { arrayOf<Any>(it) }

        /** A held tile's zoom (AppTileView), the largest a tile gets. */
        const val HELD_ZOOM = 1.15f
    }

    private val shown = listOf("an app name far too long for its tile") + (1..11).map { "app%02d".format(it) }
    private val hidden = listOf("x1", "x2", "x3")

    @Before
    fun useScreenAndFakes() {
        RuntimeEnvironment.setFontScale(screen.fontScale)
        RuntimeEnvironment.setQualifiers("en-rUS-${screen.qualifiers}")
        val application = RuntimeEnvironment.getApplication() as HomeTestApplication
        application.graph = object : TestHomeGraph(application) {
            override val installedApps = FakeInstalledApps((shown + hidden).map(::app))
            override val appArrangements = FakeAppArrangements(AppArrangement(order = null, hidden = hidden.map { app(it).launchable }))
            override val clock = FakeClock(FakeClock.at(2026, 9, 30, 23, 55, uses24Hour = false))   // "11:55 PM", "Wednesday, September 30"
        }
    }

    private fun start() = Robolectric.buildActivity(HomeActivity::class.java).setup().get()

    private fun HomeActivity.tiles(): List<AppTileView> {
        val tiles = findViewById<AppTilesView>(R.id.home_apps)
        return (0 until tiles.childCount).map { tiles.getChildAt(it) as AppTileView }
    }

    /** The screen less the overscan margin, where everything of the home screen must be. */
    private fun HomeActivity.safeArea(): RectF {
        val metrics = resources.displayMetrics
        val horizontal = resources.getDimension(R.dimen.home_padding_horizontal)
        val vertical = resources.getDimension(R.dimen.home_padding_vertical)
        return RectF(horizontal, vertical, metrics.widthPixels - horizontal, metrics.heightPixels - vertical)
    }

    private fun HomeActivity.name(view: View) = resources.getResourceEntryName(view.id)

    /** The tiles on screen without scrolling. */
    private fun HomeActivity.visibleTiles(): List<AppTileView> {
        val screenHeight = resources.displayMetrics.heightPixels
        return tiles().filter { bounds(it).top < screenHeight }
    }

    private fun HomeActivity.checkTiles() {
        val tiles = visibleTiles()
        val topBar = bounds(findViewById(R.id.home_top_bar))
        val width = tiles.first().width
        // About the width they're meant to have: the nearest whole number of them fills a row.
        val meant = resources.getDimension(R.dimen.home_tile_width)
        assertTrue("tiles $width px wide on $screen, meant $meant", width in (meant * 0.8f).toInt()..(meant * 1.25f).toInt())
        for (tile in tiles) {
            val zoomed = bounds(tile, HELD_ZOOM)
            assertTrue("${tile.app.label} zoomed at $zoomed, under the top bar at $topBar on $screen", zoomed.top >= topBar.bottom)
            val area = safeArea()
            assertTrue("${tile.app.label} at ${bounds(tile)}, outside $area on $screen", bounds(tile).left >= area.left && bounds(tile).right <= area.right)
        }
        // Zoomed (one at a time), each still clear of the others.
        for (a in tiles) for (b in tiles - a) {
            assertApart(screen, "${a.app.label} zoomed and ${b.app.label}", bounds(a, HELD_ZOOM), bounds(b))
        }
    }

    @Test
    fun `the home screen fits`() {
        val activity = start()
        val time = activity.findViewById<TextView>(R.id.home_time)
        val date = activity.findViewById<TextView>(R.id.home_date)
        val settings = activity.findViewById<View>(R.id.home_settings)

        for (view in listOf(time, date, settings)) assertInside(screen, activity.safeArea(), activity.name(view), bounds(view))
        assertWhole(screen, time)
        assertWhole(screen, date)
        activity.checkTiles()
    }

    @Test
    fun `arrange mode fits`() {
        val activity = start()
        val tile = activity.tiles().first()
        tile.requestFocus()
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 50L))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))   // the next frames: layout, zoom
        val title = activity.findViewById<TextView>(R.id.home_arrange_title)
        val hint = activity.findViewById<TextView>(R.id.home_arrange_hint)
        assertTrue(tile.held)

        for (view in listOf(title, hint)) assertInside(screen, activity.safeArea(), activity.name(view), bounds(view))
        assertWhole(screen, title)
        assertWhole(screen, hint)
        activity.checkTiles()
    }
}
