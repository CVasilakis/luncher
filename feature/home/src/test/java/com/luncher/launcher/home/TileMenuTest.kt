package com.luncher.launcher.home

import android.content.Intent
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.TileStyle
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import java.time.Duration

/** A tile's menu, through the home screen: apps a to g (a row of five, then f and g), and x hidden. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class TileMenuTest {

    private val installedApps = FakeInstalledApps(listOf("a", "b", "c", "d", "e", "f", "g", "x").map { app(it) })
    private val arrangements = FakeAppArrangements(AppArrangement(order = null, hidden = listOf(app("x").launchable)))

    @Before
    fun useFakes() {
        val application = RuntimeEnvironment.getApplication() as HomeTestApplication
        application.graph = object : TestHomeGraph(application) {
            override val installedApps = this@TileMenuTest.installedApps
            override val appArrangements = this@TileMenuTest.arrangements
        }
    }

    private fun start(): ActivityController<HomeActivity> = Robolectric.buildActivity(HomeActivity::class.java).setup()

    private fun HomeActivity.tiles(): List<AppTileView> {
        val tiles = findViewById<AppTilesView>(R.id.home_apps)
        return (0 until tiles.childCount).map { tiles.getChildAt(it) as AppTileView }
    }

    private fun HomeActivity.labels() = tiles().joinToString("") { it.app.label.lowercase() }

    private fun HomeActivity.tile(name: String) = tiles().single { it.app.label.equals(name, ignoreCase = true) }

    /** Holds OK on the tile of [name] until it counts as a long press, then lets go: the menu has the release. */
    private fun HomeActivity.longPress(name: String): TileMenu {
        tile(name).requestFocus()
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 50L))
        val menu = checkNotNull(shownMenu()) { "no menu" }
        menu.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        return menu
    }

    private fun TileMenu.press(keyCode: Int) {
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun TileMenu.choose(label: String) {
        entries().single { it.text == label }.requestFocus()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
    }

    private fun HomeActivity.moving() = findViewById<View>(R.id.home_move_title).visibility == View.VISIBLE

    @Test
    fun `a long press on a tile opens its menu, the first entry focused`() {
        val activity = start().get()

        val menu = activity.longPress("b")

        assertTrue(menu.isShowing)
        assertEquals(listOf("Move", "Hide"), menu.labels())
        assertTrue(menu.entries().first().isFocused)
        assertTrue("the tile keeps its focus", activity.tile("b").isFocused)
        assertFalse(activity.moving())
    }

    @Test
    fun `the long press, and its release in the menu, open nothing`() {
        val activity = start().get()

        val menu = activity.longPress("b")

        assertTrue(menu.isShowing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `with no other app shown, the menu offers no move`() {
        installedApps.apps = listOf(app("a"), app("x"))
        val activity = start().get()

        assertEquals(listOf("Hide"), activity.longPress("a").labels())
    }

    @Test
    fun `Back closes the menu, and changes nothing`() {
        val activity = start().get()
        val menu = activity.longPress("b")

        menu.press(KeyEvent.KEYCODE_BACK)

        assertFalse(menu.isShowing)
        assertTrue(activity.tile("b").isFocused)
        assertEquals(AppArrangement(order = null, hidden = listOf(app("x").launchable)), arrangements.arrangement)
    }

    @Test
    fun `Move closes the menu and holds the app`() {
        val activity = start().get()
        val menu = activity.longPress("b")

        menu.choose("Move")

        assertFalse(menu.isShowing)
        assertTrue(activity.moving())
        assertTrue(activity.tile("b").held)
    }

    @Test
    fun `Hide hides the app at once, first among the hidden apps, and keeps the order by name`() {
        val activity = start().get()

        activity.longPress("b").choose("Hide")

        assertNull(shownMenu())
        assertEquals("acdefg", activity.labels())
        assertEquals(AppArrangement(order = null, hidden = listOf(app("b"), app("x")).map { it.launchable }), arrangements.arrangement)
    }

    @Test
    fun `after Hide, the focus is on the tile now in its place`() {
        val activity = start().get()

        activity.longPress("b").choose("Hide")

        assertTrue(activity.tile("c").isFocused)
    }

    @Test
    fun `after hiding the last tile, the focus is on the tile before it`() {
        val activity = start().get()

        activity.longPress("g").choose("Hide")

        assertTrue(activity.tile("f").isFocused)
    }

    @Test
    fun `after hiding the only app shown, the focus is on the settings entry`() {
        installedApps.apps = listOf(app("a"), app("x"))
        val activity = start().get()

        activity.longPress("a").choose("Hide")

        assertEquals("", activity.labels())
        assertTrue(activity.findViewById<View>(R.id.home_settings).isFocused)
        assertEquals("All apps are hidden. Open Settings → Hide apps to show them again.", activity.findViewById<TextView>(R.id.home_empty).text.toString())
    }

    @Test
    fun `Home closes the menu`() {
        val controller = start()
        val menu = controller.get().longPress("b")

        controller.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

        assertFalse(menu.isShowing)
    }

    @Test
    fun `leaving the home screen closes the menu`() {
        val controller = start()
        val menu = controller.get().longPress("b")

        controller.pause().stop()

        assertFalse(menu.isShowing)
    }

    // Its tile may be gone, or the app changed (an update draws its tile anew).
    @Test
    fun `an app installed while the menu shows closes it`() {
        val activity = start().get()
        val menu = activity.longPress("b")

        installedApps.apps += app("h")

        assertFalse(menu.isShowing)
        assertEquals("abcdefgh", activity.labels())
    }

    // Of a piece with the tiles, up to a panel's corners; its entries' concentric with its own.
    @Test
    fun `the menu's corners follow the tiles', up to a panel's, its entries' inside them`() {
        val settings = (RuntimeEnvironment.getApplication() as HomeTestApplication).graph.appearanceSettings as FakeAppearanceSettings
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        // The tiles' corners; the menu's and its entries' in dp.
        val cases = listOf(
            Corners.Radius(0) to (0f to 0f),
            Corners.Radius(4) to (4f to 2f),
            Corners.Radius(8) to (8f to 2f),
            Corners.Radius(12) to (12f to 4f),
            Corners.Radius(16) to (16f to 6f),
            Corners.Radius(24) to (24f to 14f),
            Corners.Radius(32) to (28f to 18f),
            Corners.Pill to (28f to 18f),
        )
        for ((corners, expected) in cases) {
            settings.appearance = settings.appearance.copy(tileStyle = TileStyle(corners = corners))
            val controller = start()
            val menu = controller.get().longPress("a")

            val panel = checkNotNull(menu.window).decorView.background as android.graphics.drawable.GradientDrawable
            assertEquals("the menu's, with $corners", expected.first * density, panel.cornerRadius, 0.5f)
            for (entry in menu.entries()) assertEquals("an entry's, with $corners", expected.second * density, (entry as com.luncher.launcher.ui.EntryText).corner, 0.5f)
            menu.dismiss()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `the menu is made once`() {
        val activity = start().get()
        val first = activity.longPress("b")
        first.press(KeyEvent.KEYCODE_BACK)

        val second = activity.longPress("c")

        assertTrue(first === second)
    }

    // Placed by the domain's tileMenuPlacement: these check it gets the tile where it's drawn.

    @Test
    fun `the menu of a tile in the second row is above it, clear of its zoom`() {
        val activity = start().get()
        val menu = activity.longPress("f")

        val attributes = checkNotNull(menu.window).attributes
        val tile = activity.tile("f").focusedBounds()
        assertTrue("menu at ${attributes.y}, tile at ${tile.top}", attributes.y + menu.window!!.decorView.measuredHeight < tile.top)
        val tileOnScreen = IntArray(2).also { activity.tile("f").getLocationOnScreen(it) }
        assertTrue(tile.top < tileOnScreen[1] + activity.tile("f").height)
    }

    @Test
    fun `the menu of a tile in the first row is below it`() {
        val activity = start().get()
        val menu = activity.longPress("b")

        val y = checkNotNull(menu.window).attributes.y
        val tile = activity.tile("b").focusedBounds()
        assertTrue("menu at $y, tile ends at ${tile.bottom}", y > tile.bottom)
    }

    @Test
    fun `the menu of a row's first tile lines up with its left edge, and of its last with its right`() {
        val activity = start().get()

        val first = checkNotNull(activity.longPress("a").window)
        assertEquals(activity.tile("a").focusedBounds().left, first.attributes.x)
        shownMenu()!!.press(KeyEvent.KEYCODE_BACK)
        val last = checkNotNull(activity.longPress("e").window)
        assertEquals(activity.tile("e").focusedBounds().right, last.attributes.x + last.decorView.measuredWidth)
    }

    @Test
    fun `the menu is centered on its tile`() {
        val activity = start().get()
        val menu = activity.longPress("c")

        val window = checkNotNull(menu.window)
        val tile = activity.tile("c").focusedBounds()
        val center = window.attributes.x + window.decorView.measuredWidth / 2
        assertEquals((tile.left + tile.right) / 2.0, center.toDouble(), 1.0)
        assertNotNull(menu.entries().first().text)
    }
}
