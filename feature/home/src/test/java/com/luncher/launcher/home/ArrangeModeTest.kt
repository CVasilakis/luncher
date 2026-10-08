package com.luncher.launcher.home

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import com.luncher.domain.appearance.TileGeometry
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
import java.time.Duration

/** Arrange mode, through the home screen: five apps a to e (one row of five), and x hidden. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class ArrangeModeTest {

    private val installedApps = FakeInstalledApps(listOf("a", "b", "c", "d", "e", "x").map { app(it) })
    private val arrangements = FakeAppArrangements(AppArrangement(order = null, hidden = listOf(app("x").launchable)))

    @Before
    fun useFakes() {
        val application = RuntimeEnvironment.getApplication() as HomeTestApplication
        application.graph = object : TestHomeGraph(application) {
            override val installedApps = this@ArrangeModeTest.installedApps
            override val appArrangements = this@ArrangeModeTest.arrangements
        }
    }

    private fun start(): ActivityController<HomeActivity> = Robolectric.buildActivity(HomeActivity::class.java).setup()

    private fun HomeActivity.tiles(): List<AppTileView> {
        val tiles = findViewById<AppTilesView>(R.id.home_apps)
        return (0 until tiles.childCount).map { tiles.getChildAt(it) as AppTileView }
    }

    /** The tiles in order, lower case, a hidden app's in brackets and the held one's in capitals. */
    private fun HomeActivity.state() = tiles().joinToString("") {
        val name = if (it.held) it.app.label.uppercase() else it.app.label.lowercase()
        if (it.hidden) "[$name]" else name
    }

    private fun HomeActivity.tile(name: String) = tiles().single { it.app.label.equals(name, ignoreCase = true) }

    private fun HomeActivity.hint() = findViewById<TextView>(R.id.home_arrange_hint)

    private fun HomeActivity.arranging() = findViewById<View>(R.id.home_arrange_title).visibility == View.VISIBLE

    private fun HomeActivity.press(keyCode: Int) {
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    /** Holds OK on the tile of [name] until it counts as a long press, then lets go. */
    private fun HomeActivity.longPress(name: String) {
        tile(name).requestFocus()
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 50L))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun `a long press on a tile starts arranging, holding its app, with the hidden apps on a shelf`() {
        val activity = start().get()

        activity.longPress("b")

        assertTrue(activity.arranging())
        assertEquals("aBcde[x]", activity.state())
        assertEquals(View.GONE, activity.findViewById<View>(R.id.home_settings).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.home_clock).visibility)
        assertEquals(activity.getString(R.string.home_arrange_hint_holding), activity.hint().text.toString())
    }

    @Test
    fun `the long press opens nothing`() {
        val activity = start().get()

        activity.longPress("b")

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `arrows move the held app`() {
        val activity = start().get()
        activity.longPress("b")

        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)

        assertEquals("acdBe[x]", activity.state())
        assertTrue(activity.tile("b").isFocused)
    }

    // Moves come from the same TileLayouts as the tiles' places, so they follow the geometry too.
    @Test
    fun `arrows move the held app along the rows the tiles are shown in`() {
        val activity = start().get()
        // 300 dp tiles: three in a row, a b c above d e.
        activity.findViewById<AppTilesView>(R.id.home_apps).tileGeometry = TileGeometry(tileWidthDp = 300)
        shadowOf(Looper.getMainLooper()).idle()   // the layout pass
        activity.longPress("a")

        activity.press(KeyEvent.KEYCODE_DPAD_DOWN)

        assertEquals("bcdAe[x]", activity.state())
    }

    @Test
    fun `down from the last row hides the held app, and up shows it again`() {
        val activity = start().get()
        activity.longPress("b")

        activity.press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("acde[x][B]", activity.state())   // column 1 of the shelf

        activity.press(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals("aBcde[x]", activity.state())
    }

    @Test
    fun `OK puts the app down and stores the arrangement`() {
        val activity = start().get()
        activity.longPress("b")
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)

        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertEquals("acbde[x]", activity.state())
        assertEquals(activity.getString(R.string.home_arrange_hint_browsing), activity.hint().text.toString())
        assertEquals(listOf("a", "c", "b", "d", "e").map { app(it).launchable }, arrangements.arrangement.order)
        assertEquals(listOf(app("x").launchable), arrangements.arrangement.hidden)
    }

    @Test
    fun `putting an app down where it was stores nothing`() {
        val activity = start().get()
        activity.longPress("b")

        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertNull(arrangements.arrangement.order)
    }

    @Test
    fun `OK on the focused app picks it up, hidden ones too`() {
        val activity = start().get()
        activity.longPress("b")
        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        activity.tile("x").requestFocus()
        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertEquals("abcde[X]", activity.state())
        assertEquals(activity.getString(R.string.home_arrange_hint_holding), activity.hint().text.toString())
    }

    @Test
    fun `OK opens no app while arranging`() {
        val activity = start().get()
        activity.longPress("b")
        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `the Menu key opens no settings while arranging`() {
        val activity = start().get()
        activity.longPress("b")

        activity.press(KeyEvent.KEYCODE_MENU)

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `Back puts the app down, stores it and ends arranging`() {
        val activity = start().get()
        activity.longPress("b")
        activity.press(KeyEvent.KEYCODE_DPAD_DOWN)

        @Suppress("DEPRECATION")
        activity.onBackPressed()

        assertFalse(activity.arranging())
        assertEquals("acde", activity.state())
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.home_settings).visibility)
        assertEquals(listOf("x", "b").map { app(it).launchable }, arrangements.arrangement.hidden)
    }

    @Test
    fun `focus on a hidden app goes to the last shown one when arranging ends`() {
        val activity = start().get()
        activity.longPress("b")
        activity.press(KeyEvent.KEYCODE_DPAD_DOWN)

        @Suppress("DEPRECATION")
        activity.onBackPressed()

        assertTrue(activity.tile("e").isFocused)
    }

    @Test
    fun `focus goes to the settings entry when arranging ends with every app hidden`() {
        installedApps.apps = listOf(app("a"), app("x"))
        val activity = start().get()
        activity.longPress("a")
        activity.press(KeyEvent.KEYCODE_DPAD_DOWN)

        @Suppress("DEPRECATION")
        activity.onBackPressed()

        assertEquals("", activity.state())
        assertTrue(activity.findViewById<View>(R.id.home_settings).isFocused)
        assertEquals("All apps are hidden. Open Settings → Hide apps to show them again.", activity.findViewById<TextView>(R.id.home_empty).text.toString())
    }

    @Test
    fun `Home ends arranging`() {
        val controller = start()
        controller.get().longPress("b")
        controller.get().press(KeyEvent.KEYCODE_DPAD_RIGHT)

        controller.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

        assertFalse(controller.get().arranging())
        assertEquals("acbde", controller.get().state())
        assertEquals(listOf("a", "c", "b", "d", "e").map { app(it).launchable }, arrangements.arrangement.order)
    }

    @Test
    fun `leaving the home screen ends arranging`() {
        val controller = start()
        controller.get().longPress("b")

        controller.pause().stop()

        assertFalse(controller.get().arranging())
        assertEquals("abcde", controller.get().state())
    }

    @Test
    fun `apps aren't read again while arranging`() {
        val controller = start()
        controller.get().longPress("b")

        controller.pause()                      // e.g. a system dialog...
        installedApps.apps += app("f")          // ...while an app was installed
        controller.resume()

        assertEquals("aBcde[x]", controller.get().state())
    }

    @Test
    fun `the new order stays after arranging`() {
        val controller = start()
        controller.get().longPress("e")
        controller.get().press(KeyEvent.KEYCODE_DPAD_LEFT)
        @Suppress("DEPRECATION")
        controller.get().onBackPressed()

        controller.pause().stop()
        installedApps.apps += app("f")          // installed since: after the user's order
        controller.restart().start().resume()

        assertEquals("abcedf", controller.get().state())
    }

    // A TV switched from 1080p to 720p (HDMI), or a new language, recreates the home screen.
    @Test
    fun `a configuration change while arranging puts the app down, stores it and ends arranging`() {
        val controller = start()
        controller.get().longPress("b")
        controller.get().press(KeyEvent.KEYCODE_DPAD_RIGHT)

        RuntimeEnvironment.setQualifiers("+tvdpi")
        controller.configurationChange().visible()   // Robolectric shows the new window only when told

        assertFalse(controller.get().arranging())
        assertEquals("acbde", controller.get().state())
        assertEquals(listOf("a", "c", "b", "d", "e").map { app(it).launchable }, arrangements.arrangement.order)
    }

    @Test
    fun `with many apps, down from the last row puts the held app on the shelf, scrolled into view`() {
        installedApps.apps = (1..150).map { app("app%03d".format(it)) } + app("x")
        val activity = start().get()
        activity.longPress("app001")

        repeat(30) { activity.press(KeyEvent.KEYCODE_DPAD_DOWN) }   // 29 rows down, then onto the shelf
        shadowOf(Looper.getMainLooper()).idle()                    // the layout pass, which scrolls

        val tile = activity.tile("app001")
        assertTrue(tile.hidden)
        assertEquals(149, activity.tiles().indexOf(tile))           // after the 149 shown, first of the hidden
        val grid = activity.findViewById<AppTilesView>(R.id.home_apps)
        val top = tile.top - grid.scrollY
        val bottom = tile.bottom - grid.scrollY
        assertTrue("tile at $top to $bottom, view 0 to ${grid.height}", top >= 0 && bottom <= grid.height - grid.paddingBottom)
    }

    @Test
    fun `hiding the only app of the last row moves the shelf up a row`() {
        installedApps.apps = listOf("a", "b", "c", "d", "e", "f", "x").map { app(it) }   // f alone in the second row
        val activity = start().get()
        activity.longPress("f")
        shadowOf(Looper.getMainLooper()).idle()
        val row = activity.tile("f").top - activity.tile("a").top
        val shelfTop = activity.tile("x").top

        activity.press(KeyEvent.KEYCODE_DPAD_DOWN)
        shadowOf(Looper.getMainLooper()).idle()   // the layout pass

        assertEquals("abcde[F][x]", activity.state())
        assertEquals(shelfTop - row, activity.tile("x").top)
        assertEquals(activity.tile("x").top, activity.tile("f").top)   // on the shelf, next to x
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
        activity.longPress("b")
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

        assertEquals("aBcde[x]", activity.state())   // back where it started
        // Below the smallest object, so a one-off allocation of the JVM's own doesn't count.
        assertTrue("$perMove bytes per move", perMove < 16)
    }
}
