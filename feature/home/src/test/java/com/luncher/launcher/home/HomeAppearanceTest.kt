package com.luncher.launcher.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import com.luncher.domain.appearance.AppName
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
import com.luncher.domain.jobs.ManualJobs
import com.luncher.launcher.testing.TV_1080P
import com.luncher.launcher.ui.R as UiR
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
    fun `lifts the focused tile without a frame as the appearance says, the others staying down`() {
        appearance.appearance = Appearance(tileStyle = TileStyle(focusFrame = false))

        val activity = start().get()
        idle()   // the focus animator's frames

        val tiles = activity.tiles()
        val lift = activity.resources.getDimension(UiR.dimen.tile_lift)
        assertEquals(lift, tiles[0].translationZ)
        assertEquals(AppTileView.FOCUSED_ZOOM, tiles[0].scaleX)
        assertEquals(0f, tiles[1].translationZ)
        assertEquals(1f, tiles[1].scaleX)
        assertSame(activity.grid().paints.tileOutline, tiles[0].outlineProvider)
    }

    // The view sets the paints' names only when its geometry changes: before that, they must agree.
    @Test
    fun `the tiles show names as their first geometry says, before any change`() {
        val tiles = AppTilesView(RuntimeEnvironment.getApplication(), null)

        assertEquals(tiles.tileGeometry.appName, tiles.paints.names)
        assertEquals(tiles.tileGeometry.appName != AppName.OFF, tiles.paints.showsNames)
    }

    // Every name whole, the focused one's too, at once: none fades with the focus.
    @Test
    fun `with every app name shown, each tile draws its name whole, focused or not`() {
        appearance.appearance = Appearance(topLeftTiles.copy(appName = AppName.ALWAYS))
        val activity = start().get()
        idle()

        val tiles = activity.tiles()
        assertTrue(tiles[0].isFocused)
        for (tile in tiles) assertEquals("${tile.app.name}'s name", TilePaints.OPAQUE, nameAlpha(tile))
        tiles[1].requestFocus()   // no frame of a fade: the names stay whole
        for (tile in tiles) assertEquals("${tile.app.name}'s name", TilePaints.OPAQUE, nameAlpha(tile))
    }

    // Only the focused tile's name shows; the others draw none.
    @Test
    fun `with the focused tile's name shown, the others draw none`() {
        appearance.appearance = Appearance(topLeftTiles.copy(appName = AppName.WHEN_FOCUSED))
        val activity = start().get()
        idle()

        val tiles = activity.tiles()
        assertEquals(TilePaints.OPAQUE, nameAlpha(tiles[0]))
        assertEquals(-1, nameAlpha(tiles[1]))
    }

    /** The alpha [tile] draws its name with: the last text it draws, its outline before it. */
    private fun nameAlpha(tile: AppTileView): Int {
        var alpha = -1
        val canvas = object : Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)) {
            override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
                alpha = paint.alpha
            }
        }
        tile.draw(canvas)
        return alpha
    }

    // The shadow is cast by the outline: the tile's shape, its round ends too, a pixel inside its
    // edge (TilePainter.SHADOW_INSET), so Android's shadow reaches under the tile's soft edge.
    @Test
    fun `the lifted tile's shadow has the tiles' corners`() {
        appearance.appearance = Appearance(tileStyle = TileStyle(Corners.Pill, focusFrame = false))

        val activity = start().get()

        val tile = activity.tiles().first()
        val outline = Outline()
        tile.outlineProvider.getOutline(tile, outline)
        assertEquals((tile.height - 2) / 2f, outline.radius)
        val bounds = Rect()
        assertTrue(outline.getRect(bounds))
        assertEquals(Rect(1, 1, tile.width - 1, tile.height - 1), bounds)
    }

    // Framed, the focused tile is as big as lifted: its frame takes no room outside it.
    @Test
    fun `framed, the focused tile zooms as much as lifted, its frame inside its edge, and stays down`() {
        val activity = start().get()
        idle()   // the focus animator's frames

        val tile = activity.tiles().first()
        assertEquals(AppTileView.FOCUSED_ZOOM, tile.scaleX)
        assertEquals(0f, tile.translationZ)
        val focusFrame = activity.grid().paints.painter.focusFrame
        val frame = RectF()
        val canvas = object : Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)) {
            override fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) {
                if (paint === focusFrame) frame.set(rect)
            }
        }
        tile.draw(canvas)
        val half = focusFrame.strokeWidth / 2
        assertEquals(RectF(half, half, tile.width - half, tile.height - half), frame)
    }

    // OK held: the press shows at once, before the app opens, or the menu after the hold (350 ms).
    @Test
    fun `while OK is held, the framed tile dips, and zooms back once it's let go`() {
        val activity = start().get()
        idle()
        val tile = activity.tiles().first()

        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))   // the dip's frames, short of the hold

        assertEquals(DIPPED_ZOOM, tile.scaleX)
        assertEquals(DIPPED_ZOOM, tile.scaleY)
        assertEquals(0f, tile.translationZ)
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertEquals(AppTileView.FOCUSED_ZOOM, tile.scaleX)
        assertEquals(AppTileView.FOCUSED_ZOOM, tile.scaleY)
    }

    @Test
    fun `while OK is held, the lifted tile dips and comes down to half its lift, and rises back once it's let go`() {
        appearance.appearance = Appearance(tileStyle = TileStyle(focusFrame = false))
        val activity = start().get()
        idle()
        val tile = activity.tiles().first()
        val lift = activity.resources.getDimension(UiR.dimen.tile_lift)

        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))

        assertEquals(DIPPED_ZOOM, tile.scaleX)
        assertEquals(lift / 2, tile.translationZ)
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertEquals(AppTileView.FOCUSED_ZOOM, tile.scaleX)
        assertEquals(lift, tile.translationZ)
    }

    // The gear in the top bar answers the press too, as the tiles do: the panel opens as OK is let go.
    @Test
    fun `while OK is held, the settings gear dips, and comes back once it's let go`() {
        val activity = start().get()
        idle()
        val gear = activity.findViewById<View>(R.id.home_settings)
        gear.requestFocus()
        idle()
        assertEquals(1f, gear.scaleX)

        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))

        assertEquals(DIPPED_GEAR, gear.scaleX)
        assertEquals(DIPPED_GEAR, gear.scaleY)
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertEquals(1f, gear.scaleX)
        assertEquals(1f, gear.scaleY)
    }

    // The frame turned on only swaps the tiles' animators: the tile is framed and down again at once.
    @Test
    fun `the focus frame turned on lays nothing out again, and puts the focused tile down`() {
        appearance.appearance = Appearance(tileStyle = TileStyle(focusFrame = false))
        val activity = start().get()
        idle()

        appearance.write(Appearance())

        val tile = activity.tiles().first()
        assertFalse(activity.grid().isLayoutRequested)
        assertTrue(activity.grid().paints.framed)
        assertEquals(0f, tile.translationZ)
        assertEquals(AppTileView.FOCUSED_ZOOM, tile.scaleX)
    }

    // The held tile keeps its white frame and its zoom, lifted too: the frame tells it from one only focused.
    @Test
    fun `lifted, a held tile still has its zoom`() {
        appearance.appearance = Appearance(tileStyle = TileStyle(focusFrame = false))
        val activity = start().get()
        val tile = activity.tiles().first()

        tile.startMoving()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        assertTrue(tile.held)
        assertEquals(AppTileView.HELD_ZOOM, tile.scaleX)
    }

    // The same room for every name as for the focused one's: the focused one, zoomed, takes the most.
    @Test
    fun `with the app names shown, the rows leave room below each for the focused tile's name`() {
        appearance.appearance = Appearance(topLeftTiles)
        val activity = start().get()
        idle()
        val tiles = activity.tiles()
        val rowsApart = tiles[5].top - tiles[0].top

        for (names in listOf(AppName.WHEN_FOCUSED, AppName.ALWAYS)) {
            appearance.write(Appearance(topLeftTiles.copy(appName = names)))
            idle()

            val paints = activity.grid().paints
            assertTrue(paints.showsNames)
            assertTrue("${paints.nameHeight} px for a name", paints.nameHeight > 0)
            assertEquals("$names", rowsApart + paints.nameHeight, tiles[5].top - tiles[0].top)
            assertSame(tiles[0], tiles[0].focusSearch(View.FOCUS_LEFT))   // the moves the same
            assertSame(tiles[5], tiles[0].focusSearch(View.FOCUS_DOWN))
        }
    }

    // At the bottom, the last row is the room for a name higher, so the name stays on the screen.
    @Test
    fun `with the app names shown at the bottom, the last row leaves room for its name below it`() {
        appearance.appearance = Appearance(TileGeometry(verticalAlignment = VerticalAlignment.BOTTOM, appName = AppName.WHEN_FOCUSED))

        val activity = start().get()

        val grid = activity.grid()
        assertEquals(grid.height - grid.paddingBottom - grid.paints.nameHeight, activity.tiles().last().bottom)
    }

    // Rows that don't fit scroll: a tile focused at the bottom scrolls up until its name shows too.
    @Test
    fun `focusing a tile at the bottom scrolls its name into sight`() {
        val application = RuntimeEnvironment.getApplication() as HomeTestApplication
        application.graph = object : TestHomeGraph(application) {
            override val installedApps = FakeInstalledApps((1..25).map { app("app%02d".format(it)) })
            override val appearanceSettings = FakeAppearanceSettings(Appearance(topLeftTiles.copy(appName = AppName.WHEN_FOCUSED)))
        }
        val activity = start().get()
        idle()
        val grid = activity.grid()
        val last = activity.tiles().last()

        last.requestFocus()
        // A layout pass scrolls to the focused tile at once, where the focus move's scroll is
        // animated by drawn frames, which Robolectric doesn't draw.
        grid.requestLayout()
        idle()

        assertTrue(grid.scrollY > 0)
        assertEquals(grid.height - grid.paddingBottom, last.bottom + grid.paints.nameHeight - grid.scrollY)
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
        assertEquals(Corners.Radius(16), controller.get().grid().paints.corners)   // the default still

        controller.restart().resume()   // restart() starts it too, as Android does
        assertEquals(1, appearance.listenerCount)
        assertEquals(Corners.Radius(4), controller.get().grid().paints.corners)
    }

    @Test
    fun `stops following the appearance when it's gone`() {
        start().pause().stop().destroy()

        assertEquals(0, appearance.listenerCount)
    }

    // The focused tile lifted, its name below it: neither allocates either.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `drawing the tiles allocates nothing lifted, with the focused tile's name`() {
        drawingTheTilesAllocatesNothing(Appearance(TileGeometry(appName = AppName.WHEN_FOCUSED), TileStyle(Corners.Radius(16), focusFrame = false)))
    }

    // Every tile's name drawn, and none fading.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `drawing the tiles allocates nothing with every tile's name`() {
        drawingTheTilesAllocatesNothing(Appearance(TileGeometry(appName = AppName.ALWAYS)))
    }

    // Rule 5 (docs/ARCHITECTURE.md): drawing allocates nothing, with rounded corners too. Counts
    // what this thread allocates drawing the tiles, the focused one's frame included, after a new
    // style, which itself may allocate (a key that changes a stored value). Native graphics, since
    // Robolectric's own canvas records each drawing call as text.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `drawing the tiles allocates nothing after a new tile style`() {
        drawingTheTilesAllocatesNothing(Appearance(tileStyle = TileStyle(Corners.Radius(16))))
    }

    /** Draws the tiles many times after [changed] is written, and fails if a draw allocates. */
    private fun drawingTheTilesAllocatesNothing(changed: Appearance) {
        val activity = start().get()
        idle()
        appearance.write(changed)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))   // a new layout, the animators
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

        // Every option that shows there: the theme's mode is the menus' and settings' only (SettingsThemeTest).
        for (change in appearanceChanges().filter { it.option.showsOnHome }) {
            val base = draw(activity, change.base)
            val changed = draw(activity, change.changed)
            assertFalse("$change draws as ${change.option} at its default", base.sameAs(changed))
        }
    }

    private fun draw(activity: HomeActivity, appearance: Appearance): Bitmap {
        this.appearance.write(appearance)
        ((RuntimeEnvironment.getApplication() as HomeTestApplication).graph as TestHomeGraph).jobs.runAll()   // a wallpaper's
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))   // layout, and the focused tile's zoom
        val window = activity.window.decorView
        return Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888).also { window.draw(Canvas(it)) }
    }

    private companion object {
        /** The focused tile's zoom while OK is held on it: home_tile_focus.xml's and home_tile_lift.xml's pressed item. */
        const val DIPPED_ZOOM = 1.1f
        const val DIPPED_GEAR = 0.88f
    }
}
