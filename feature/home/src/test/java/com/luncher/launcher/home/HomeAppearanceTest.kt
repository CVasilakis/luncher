package com.luncher.launcher.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.Side
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.TilePlacement
import com.luncher.domain.appearance.TileStyle
import com.luncher.domain.appearance.TopBarAppearance
import com.luncher.domain.appearance.appearanceChanges
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.management.ManagementFactory
import java.time.Duration

/**
 * The home screen following the user's appearance (docs/ARCHITECTURE.md#appearance): each part
 * reaches the view that draws it, at the cost its kind of change has, while the screen is shown.
 * Seven apps: a full row of five, and two in the second.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class HomeAppearanceTest {

    private val appearance = FakeAppearanceSettings()

    @Before
    fun useFakes() {
        val application = RuntimeEnvironment.getApplication() as HomeTestApplication
        application.graph = object : TestHomeGraph(application) {
            override val installedApps = FakeInstalledApps(listOf("a", "b", "c", "d", "e", "f", "g").map(::app))
            override val appearanceSettings = this@HomeAppearanceTest.appearance
        }
    }

    private fun start(): ActivityController<HomeActivity> = Robolectric.buildActivity(HomeActivity::class.java).setup()

    private fun HomeActivity.grid() = findViewById<AppTilesView>(R.id.home_apps)

    private fun HomeActivity.tiles() = grid().let { grid -> (0 until grid.childCount).map { grid.getChildAt(it) as AppTileView } }

    private fun HomeActivity.topBar() = findViewById<TopBarView>(R.id.home_top_bar)

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()   // the layout pass

    @Test
    fun `places the tiles where the appearance says`() {
        appearance.appearance = Appearance(TileGeometry(placement = TilePlacement.BOTTOM_RIGHT))

        val activity = start().get()

        val grid = activity.grid()
        val tiles = activity.tiles()
        assertEquals(grid.height - grid.paddingBottom, tiles.last().bottom)   // at the bottom
        assertEquals(tiles[4].left, tiles[6].left)                            // the short row at the right
    }

    @Test
    fun `rounds the tiles' ends into half circles as the appearance says`() {
        appearance.appearance = Appearance(tileStyle = TileStyle(Corners.Pill))

        val activity = start().get()

        val tile = activity.tiles().first()
        assertEquals(Corners.Pill, activity.grid().paints.corners)
        assertEquals(tile.height / 2f, activity.grid().paints.cornerRadius(tile.height))
    }

    @Test
    fun `arranges the top bar as the appearance says`() {
        appearance.appearance = Appearance(topBar = TopBarAppearance(clockSide = Side.RIGHT, timeSide = Side.RIGHT))

        val activity = start().get()

        val settings = activity.findViewById<View>(R.id.home_settings)
        val time = activity.findViewById<View>(R.id.home_time)
        val date = activity.findViewById<View>(R.id.home_date)
        assertTrue("settings at ${settings.left}, date at ${date.left}", settings.right <= location(date))
        assertTrue("date at ${location(date)}, time at ${location(time)}", location(date) < location(time))
        val bar = activity.topBar()
        assertEquals(bar.width - bar.paddingRight, location(time) + time.width)   // the time at the edge
    }

    @Test
    fun `hides the time or the date as the appearance says, and the tiles stay where they are`() {
        val activity = start().get()
        idle()
        val time = activity.findViewById<View>(R.id.home_time)
        val date = activity.findViewById<View>(R.id.home_date)
        val tilesTop = activity.grid().top
        val bar = activity.topBar()

        appearance.write(Appearance(topBar = TopBarAppearance(showTime = false)))
        idle()
        assertEquals(View.INVISIBLE, time.visibility)
        assertEquals(View.VISIBLE, date.visibility)
        assertEquals(bar.paddingLeft, location(date))   // in the time's place
        assertEquals(tilesTop, activity.grid().top)

        appearance.write(Appearance(topBar = TopBarAppearance(clockSide = Side.RIGHT, showDate = false)))
        idle()
        assertEquals(View.VISIBLE, time.visibility)
        assertEquals(View.INVISIBLE, date.visibility)
        assertEquals(bar.width - bar.paddingRight, location(time) + time.width)
        assertEquals(tilesTop, activity.grid().top)
    }

    // At the right, wherever the clock's side says: the side changes nothing then.
    @Test
    fun `with the time and the date hidden, the settings entry is at the right`() {
        appearance.appearance = Appearance(topBar = TopBarAppearance(clockSide = Side.RIGHT, showTime = false, showDate = false))

        val activity = start().get()

        val bar = activity.topBar()
        val settings = activity.findViewById<View>(R.id.home_settings)
        assertEquals(bar.width - bar.paddingRight, settings.right)
        assertEquals(View.INVISIBLE, activity.findViewById<View>(R.id.home_time).visibility)
        assertEquals(View.INVISIBLE, activity.findViewById<View>(R.id.home_date).visibility)
    }

    /** [view]'s left edge in the top bar. */
    private fun location(view: View) = (view.parent as View).left + view.left

    @Test
    fun `follows a change while it's shown`() {
        val activity = start().get()

        appearance.write(Appearance(topBar = TopBarAppearance(clockSide = Side.RIGHT)))
        idle()

        val settings = activity.findViewById<View>(R.id.home_settings)
        assertTrue(settings.left < activity.findViewById<View>(R.id.home_clock).left)
    }

    // Each read gives an equal appearance, as on every return to the home screen.
    @Test
    fun `an equal appearance lays nothing out again`() {
        val activity = start().get()
        idle()

        appearance.write(Appearance())

        assertFalse(activity.grid().isLayoutRequested)
        assertFalse(activity.topBar().isLayoutRequested)
    }

    @Test
    fun `a new tile style lays nothing out again`() {
        val activity = start().get()
        idle()

        appearance.write(Appearance(tileStyle = TileStyle(Corners.Radius(12))))

        assertFalse(activity.grid().isLayoutRequested)
        assertFalse(activity.topBar().isLayoutRequested)
        assertEquals(Corners.Radius(12), activity.grid().paints.corners)
    }

    // A screen that changes the appearance covers the home screen whole, so it changes once, after.
    @Test
    fun `follows the appearance from start to stop, and reads it again when it comes back`() {
        val controller = start()
        assertEquals(1, appearance.listenerCount)

        controller.pause().stop()
        assertEquals(0, appearance.listenerCount)
        appearance.write(Appearance(tileStyle = TileStyle(Corners.Radius(4))))
        assertEquals(Corners.Radius(8), controller.get().grid().paints.corners)   // the default still

        controller.restart().resume()   // restart() starts it too, as Android does
        assertEquals(1, appearance.listenerCount)
        assertEquals(Corners.Radius(4), controller.get().grid().paints.corners)
    }

    @Test
    fun `stops following the appearance when it's gone`() {
        start().pause().stop().destroy()

        assertEquals(0, appearance.listenerCount)
    }

    // Rule 5 (docs/ARCHITECTURE.md): drawing allocates nothing, with rounded corners too. Counts
    // what this thread allocates drawing the tiles, the focused one's frame included, after a new
    // style, which itself may allocate (a key that changes a stored value). Native graphics, since
    // Robolectric's own canvas records each drawing call as text.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `drawing the tiles allocates nothing after a new tile style`() {
        val activity = start().get()
        idle()
        appearance.write(Appearance(tileStyle = TileStyle(Corners.Radius(12))))
        val grid = activity.grid()
        val canvas = Canvas(Bitmap.createBitmap(grid.width, grid.height, Bitmap.Config.ARGB_8888))
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        fun draw(): Long {
            val start = threads.currentThreadAllocatedBytes
            grid.draw(canvas)
            return threads.currentThreadAllocatedBytes - start
        }
        repeat(1000) { draw() }   // classes loaded, code compiled
        val draws = 1000

        val perDraw = (0 until draws).sumOf { draw() }.toDouble() / draws

        assertTrue(activity.tiles().first().isFocused)
        // Below the smallest object, so a one-off allocation of the JVM's own doesn't count.
        assertTrue("$perDraw bytes per draw", perDraw < 16)
    }

    // The catalog-wide check (docs/ARCHITECTURE.md#appearance): an option that doesn't reach the
    // home screen, or a value of it that looks like another, fails here.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `every value of every option changes what the home screen draws`() {
        val activity = start().get()

        for (change in appearanceChanges()) {
            val base = draw(activity, change.base)
            val changed = draw(activity, change.changed)
            assertFalse("$change draws as ${change.option} at its default", base.sameAs(changed))
        }
    }

    private fun draw(activity: HomeActivity, appearance: Appearance): Bitmap {
        this.appearance.write(appearance)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))   // layout, and the focused tile's zoom
        val window = activity.window.decorView
        return Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888).also { window.draw(Canvas(it)) }
    }
}
