package com.luncher.launcher.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.HorizontalAlignment
import com.luncher.domain.appearance.Side
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.TileStyle
import com.luncher.domain.appearance.TopBarAppearance
import com.luncher.domain.appearance.VerticalAlignment
import com.luncher.domain.appearance.appearanceChanges
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
        appearance.appearance = Appearance(TileGeometry(verticalAlignment = VerticalAlignment.BOTTOM, horizontalAlignment = HorizontalAlignment.RIGHT, fullBottomRow = false))

        val activity = start().get()

        val grid = activity.grid()
        val tiles = activity.tiles()
        assertEquals(grid.height - grid.paddingBottom, tiles.last().bottom)   // at the bottom
        assertEquals(tiles[4].left, tiles[6].left)                            // the short row at the right
    }

    @Test
    fun `centers the tiles as the appearance says`() {
        appearance.appearance = Appearance(TileGeometry(verticalAlignment = VerticalAlignment.CENTER, horizontalAlignment = HorizontalAlignment.CENTER, fullBottomRow = false))

        val activity = start().get()

        val grid = activity.grid()
        val tiles = activity.tiles()
        val above = tiles.first().top - grid.paddingTop
        val below = grid.height - grid.paddingBottom - tiles.last().bottom
        assertTrue("$above above, $below below", above - below in 0..1)
        val leftOfRow = tiles[5].left - tiles[0].left
        val rightOfRow = tiles[4].right - tiles[6].right
        assertTrue("$leftOfRow left of the short row, $rightOfRow right of it", leftOfRow - rightOfRow in -1..1)
    }

    // Two tiles centered under five are half a column off: Up and Down take the nearest, the left
    // one of two as near, and the opposite key goes back where the focus came from.
    @Test
    fun `with a centered row, Up and Down go where the domain's rule says`() {
        appearance.appearance = Appearance(topLeftTiles.copy(horizontalAlignment = HorizontalAlignment.CENTER))
        val activity = start().get()
        val tiles = activity.tiles()

        assertSame(tiles[5], tiles[2].focusSearch(View.FOCUS_DOWN))   // 5 and 6 as near
        assertSame(tiles[6], tiles[4].focusSearch(View.FOCUS_DOWN))
        assertSame(tiles[4], tiles[6].focusSearch(View.FOCUS_UP))     // back, not to 2, the nearest
        assertSame(tiles[1], tiles[5].focusSearch(View.FOCUS_UP))     // 1 and 2 as near
        assertSame(activity.findViewById<View>(R.id.home_settings), tiles[1].focusSearch(View.FOCUS_UP))   // Android's
    }

    // The full bottom row's last tile ends a pixel left of the settings entry, and the short row
    // centered above it starts and ends between columns: Left and Right stay in the row.
    @Test
    fun `centered with a full bottom row, Left and Right stay in the row`() {
        appearance.appearance = Appearance(
            TileGeometry(
                verticalAlignment = VerticalAlignment.CENTER,
                horizontalAlignment = HorizontalAlignment.CENTER,
                fullBottomRow = true,
            ),
        )
        val tiles = start().get().tiles()   // two centered, then five

        assertSame(tiles[6], tiles[6].focusSearch(View.FOCUS_RIGHT))   // not the settings entry
        assertSame(tiles[2], tiles[2].focusSearch(View.FOCUS_LEFT))
        assertSame(tiles[1], tiles[1].focusSearch(View.FOCUS_RIGHT))   // not down to the full row
        assertSame(tiles[0], tiles[0].focusSearch(View.FOCUS_LEFT))
        assertSame(tiles[1], tiles[0].focusSearch(View.FOCUS_RIGHT))
    }

    // The clock on the right puts the settings entry at the left, above the first tile.
    @Test
    fun `with the settings entry at the left, Left and Right on it stay in the top bar`() {
        appearance.appearance = Appearance(topBar = TopBarAppearance(clockSide = Side.RIGHT))
        val settings = start().get().findViewById<View>(R.id.home_settings)

        assertSame(settings, settings.focusSearch(View.FOCUS_RIGHT))
        assertSame(settings, settings.focusSearch(View.FOCUS_LEFT))
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

        appearance.write(Appearance(tileStyle = TileStyle(Corners.Radius(16))))

        assertFalse(activity.grid().isLayoutRequested)
        assertFalse(activity.topBar().isLayoutRequested)
        assertEquals(Corners.Radius(16), activity.grid().paints.corners)
    }

    // A screen that changes the appearance covers the home screen whole, so it changes once, after.
    @Test
    fun `follows the appearance from start to stop, and reads it again when it comes back`() {
        val controller = start()
        assertEquals(1, appearance.listenerCount)

        controller.pause().stop()
        assertEquals(0, appearance.listenerCount)
        appearance.write(Appearance(tileStyle = TileStyle(Corners.Radius(4))))
        assertEquals(Corners.Radius(12), controller.get().grid().paints.corners)   // the default still

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
        appearance.write(Appearance(tileStyle = TileStyle(Corners.Radius(16))))
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
