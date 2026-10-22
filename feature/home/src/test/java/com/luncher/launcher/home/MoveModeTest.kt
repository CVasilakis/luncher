package com.luncher.launcher.home

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

/** Moving an app, through the home screen: five apps a to e (one row of five), and x hidden. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class MoveModeTest {

    private val installedApps = FakeInstalledApps(listOf("a", "b", "c", "d", "e", "x").map { app(it) })
    private val arrangements = FakeAppArrangements(AppArrangement(order = null, hidden = listOf(app("x").launchable)))

    @Before
    fun useFakes() {
        val application = RuntimeEnvironment.getApplication() as HomeTestApplication
        application.graph = object : TestHomeGraph(application) {
            override val installedApps = this@MoveModeTest.installedApps
            override val appArrangements = this@MoveModeTest.arrangements
        }
    }

    private fun start(): ActivityController<HomeActivity> = Robolectric.buildActivity(HomeActivity::class.java).setup()

    private fun HomeActivity.tiles(): List<AppTileView> {
        val tiles = findViewById<AppTilesView>(R.id.home_apps)
        return (0 until tiles.childCount).map { tiles.getChildAt(it) as AppTileView }
    }

    /** The tiles in order, lower case, the held one's in capitals. */
    private fun HomeActivity.state() = tiles().joinToString("") {
        if (it.held) it.app.label.uppercase() else it.app.label.lowercase()
    }

    private fun HomeActivity.tile(name: String) = tiles().single { it.app.label.equals(name, ignoreCase = true) }

    private fun HomeActivity.moving() = findViewById<View>(R.id.home_move_title).visibility == View.VISIBLE

    private fun HomeActivity.move(name: String) = tile(name).startMoving()

    private fun HomeActivity.press(keyCode: Int) {
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    @Test
    fun `Move in a tile's menu holds its app, without the hidden apps`() {
        val activity = start().get()

        activity.move("b")

        assertTrue(activity.moving())
        assertEquals("aBcde", activity.state())
        assertTrue(activity.tile("b").isFocused)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.home_settings).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.home_clock).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.home_move_hint).visibility)
        assertNull(shownMenu())
    }

    @Test
    fun `arrows move the held app`() {
        val activity = start().get()
        activity.move("b")

        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)

        assertEquals("acdBe", activity.state())
        assertTrue(activity.tile("b").isFocused)
    }

    // Moves come from the same TileLayouts as the tiles' places, so they follow the geometry too.
    @Test
    fun `arrows move the held app along the rows the tiles are shown in`() {
        val activity = start().get()
        // 300 dp tiles: three in a row, a b c above d e.
        activity.findViewById<AppTilesView>(R.id.home_apps).tileGeometry = topLeftTiles.copy(tileWidthDp = 300)
        shadowOf(Looper.getMainLooper()).idle()   // the layout pass
        activity.move("a")

        activity.press(KeyEvent.KEYCODE_DPAD_DOWN)

        assertEquals("bcdAe", activity.state())
    }

    @Test
    fun `down from the last row does nothing`() {
        val activity = start().get()
        activity.move("b")

        activity.press(KeyEvent.KEYCODE_DPAD_DOWN)

        assertEquals("aBcde", activity.state())
        assertTrue(activity.moving())
    }

    @Test
    fun `OK puts the app down, stores the order and ends moving`() {
        val activity = start().get()
        activity.move("b")
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)

        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertFalse(activity.moving())
        assertEquals("acbde", activity.state())
        assertTrue(activity.tile("b").isFocused)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.home_settings).visibility)
        assertEquals(listOf("a", "c", "b", "d", "e").map { app(it).launchable }, arrangements.arrangement.order)
        assertEquals(listOf(app("x").launchable), arrangements.arrangement.hidden)
    }

    @Test
    fun `Back puts the app down where it is, stores the order and ends moving`() {
        val activity = start().get()
        activity.move("b")
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)

        @Suppress("DEPRECATION")
        activity.onBackPressed()

        assertFalse(activity.moving())
        assertEquals("acbde", activity.state())
        assertEquals(listOf("a", "c", "b", "d", "e").map { app(it).launchable }, arrangements.arrangement.order)
    }

    @Test
    fun `putting an app down where it was stores nothing`() {
        val activity = start().get()
        activity.move("b")

        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertFalse(activity.moving())
        assertNull(arrangements.arrangement.order)
    }

    // The OK that chose Move went to the menu's window; a release without its press here, too.
    @Test
    fun `only an OK pressed while moving puts the app down`() {
        val activity = start().get()
        activity.move("b")

        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))

        assertTrue(activity.moving())
    }

    @Test
    fun `OK opens no app while moving`() {
        val activity = start().get()
        activity.move("b")

        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `the Menu key opens no settings while moving`() {
        val activity = start().get()
        activity.move("b")

        activity.press(KeyEvent.KEYCODE_MENU)

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `Home puts the app down and ends moving`() {
        val controller = start()
        controller.get().move("b")
        controller.get().press(KeyEvent.KEYCODE_DPAD_RIGHT)

        controller.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

        assertFalse(controller.get().moving())
        assertEquals("acbde", controller.get().state())
        assertEquals(listOf("a", "c", "b", "d", "e").map { app(it).launchable }, arrangements.arrangement.order)
    }

    @Test
    fun `leaving the home screen ends moving`() {
        val controller = start()
        controller.get().move("b")

        controller.pause().stop()

        assertFalse(controller.get().moving())
        assertEquals("abcde", controller.get().state())
    }

    @Test
    fun `apps aren't read again while moving`() {
        val controller = start()
        controller.get().move("b")

        controller.pause()                      // e.g. a system dialog...
        installedApps.apps += app("f")          // ...while an app was installed
        controller.resume()

        assertEquals("aBcde", controller.get().state())
    }

    @Test
    fun `an app installed while moving shows once moving ends`() {
        val activity = start().get()
        activity.move("b")

        installedApps.apps += app("f")          // the tiles stay the user's work in progress
        assertEquals("aBcde", activity.state())
        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertEquals("abcdef", activity.state())
    }

    @Test
    fun `the new order stays after moving`() {
        val controller = start()
        controller.get().move("e")
        controller.get().press(KeyEvent.KEYCODE_DPAD_LEFT)
        controller.get().press(KeyEvent.KEYCODE_DPAD_CENTER)

        controller.pause().stop()
        installedApps.apps += app("f")          // installed since: after the user's order
        controller.restart().start().resume()

        assertEquals("abcedf", controller.get().state())
    }

    // A TV switched from 1080p to 720p (HDMI), or a new language, recreates the home screen.
    @Test
    fun `a configuration change while moving puts the app down, stores it and ends moving`() {
        val controller = start()
        controller.get().move("b")
        controller.get().press(KeyEvent.KEYCODE_DPAD_RIGHT)

        RuntimeEnvironment.setQualifiers("+tvdpi")
        controller.configurationChange().visible()   // Robolectric shows the new window only when told

        assertFalse(controller.get().moving())
        assertEquals("acbde", controller.get().state())
        assertEquals(listOf("a", "c", "b", "d", "e").map { app(it).launchable }, arrangements.arrangement.order)
    }

    @Test
    fun `with many apps, the held app is scrolled into view as it moves down`() {
        installedApps.apps = (1..150).map { app("app%03d".format(it)) } + app("x")
        val activity = start().get()
        activity.move("app001")

        repeat(29) { activity.press(KeyEvent.KEYCODE_DPAD_DOWN) }   // to the last row
        shadowOf(Looper.getMainLooper()).idle()                    // the layout pass, which scrolls

        val tile = activity.tile("app001")
        assertEquals(145, activity.tiles().indexOf(tile))           // the last row's first
        val grid = activity.findViewById<AppTilesView>(R.id.home_apps)
        val top = tile.top - grid.scrollY
        val bottom = tile.bottom - grid.scrollY
        assertTrue("tile at $top to $bottom, view 0 to ${grid.height}", top >= 0 && bottom <= grid.height - grid.paddingBottom)
    }

    // Rule 5 (docs/ARCHITECTURE.md): a key press allocates nothing. Counts what this thread allocates
    // measuring the tiles after each move, which makes their layout, and drawing them, as a frame
    // would. Not the key itself or onLayout: Robolectric's stand-ins for View's scrolling methods
    // allocate on each call, where Android's don't. Native graphics, since Robolectric's own canvas
    // records each drawing call as text.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `moving the held app allocates nothing to measure and draw the tiles`() {
        val activity = start().get()
        activity.move("b")
        val tiles = activity.findViewById<AppTilesView>(R.id.home_apps)
        val width = View.MeasureSpec.makeMeasureSpec(tiles.width, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(tiles.height, View.MeasureSpec.EXACTLY)
        val canvas = Canvas(Bitmap.createBitmap(tiles.width, tiles.height, Bitmap.Config.ARGB_8888))
        val keys = listOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT)
            .map { listOf(KeyEvent(KeyEvent.ACTION_DOWN, it), KeyEvent(KeyEvent.ACTION_UP, it)) }
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        /** Moves the held app and lays the tiles out again; returns the bytes measuring and drawing them allocated. */
        fun move(index: Int): Long {
            for (event in keys[index % 2]) activity.dispatchKeyEvent(event)
            var start = threads.currentThreadAllocatedBytes
            tiles.measure(width, height)
            var allocated = threads.currentThreadAllocatedBytes - start
            tiles.layout(tiles.left, tiles.top, tiles.right, tiles.bottom)
            start = threads.currentThreadAllocatedBytes
            tiles.draw(canvas)
            allocated += threads.currentThreadAllocatedBytes - start
            return allocated
        }
        repeat(1000) { move(it) }   // classes loaded, code compiled
        val moves = 1000

        val perMove = (0 until moves).sumOf { move(it) }.toDouble() / moves

        assertEquals("aBcde", activity.state())   // back where it started
        // Below the smallest object, so a one-off allocation of the JVM's own doesn't count.
        assertTrue("$perMove bytes per move", perMove < 16)
    }
}
