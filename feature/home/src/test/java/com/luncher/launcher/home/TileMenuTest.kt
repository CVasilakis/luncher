package com.luncher.launcher.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.TileStyle
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.AppCustomization
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeAppCustomizations
import com.luncher.domain.apps.FakeAppCustomizations.Companion.named
import com.luncher.domain.apps.FakeAppInfoScreen
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.TileImage
import com.luncher.domain.layout.TilePlace
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import com.luncher.launcher.settings.TileBannerActivity
import com.luncher.launcher.testing.TV_1080P
import com.luncher.launcher.ui.WindowFocus
import java.time.Duration
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
import org.robolectric.shadows.ShadowToast

/** A tile's menu, through the home screen: apps a to g (a row of five, then f and g), and x hidden. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class TileMenuTest {

    private val installedApps = FakeInstalledApps(listOf("a", "b", "c", "d", "e", "f", "g", "x").map { app(it) })
    private val arrangements = FakeAppArrangements(AppArrangement(order = null, hidden = listOf(app("x").launchable)))
    private val appInfoScreen = FakeAppInfoScreen()
    private val customizations = FakeAppCustomizations()

    @Before
    fun useFakes() {
        val application = RuntimeEnvironment.getApplication() as HomeTestApplication
        application.graph = object : TestHomeGraph(application) {
            override val installedApps = this@TileMenuTest.installedApps
            override val appArrangements = this@TileMenuTest.arrangements
            override val appInfoScreen = this@TileMenuTest.appInfoScreen
            override val appCustomizations = this@TileMenuTest.customizations
        }
    }

    private fun start(): ActivityController<HomeActivity> = Robolectric.buildActivity(HomeActivity::class.java).setup()

    private fun HomeActivity.tiles(): List<AppTileView> {
        val tiles = findViewById<AppTilesView>(R.id.home_apps)
        return (0 until tiles.childCount).map { tiles.getChildAt(it) as AppTileView }
    }

    private fun HomeActivity.labels() = tiles().joinToString("") { it.app.name.lowercase() }

    private fun HomeActivity.tile(name: String) = tiles().single { it.app.name.equals(name, ignoreCase = true) }

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
        entries().single { it.label.text == label }.requestFocus()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
    }

    private fun HomeActivity.moving() = findViewById<View>(R.id.home_move_title).visibility == View.VISIBLE

    @Test
    fun `a long press on a tile opens its menu, the first entry focused`() {
        val activity = start().get()

        val menu = activity.longPress("b")

        assertTrue(menu.isShowing)
        assertEquals(listOf("Move", "Hide", "Rename", "Tile banner", "App info"), menu.labels())
        assertTrue(menu.entries().first().isFocused)
        assertTrue("the tile keeps its focus", activity.tile("b").isFocused)
        assertFalse(activity.moving())
    }

    // No title shows: the tile, focused beside the menu, says which app it's for.
    @Test
    fun `the menu tells a screen reader the name the app was given`() {
        customizations.customizations = mapOf(named(app("b"), "Bravo"))
        val activity = start().get()

        val menu = activity.longPress("bravo")

        assertEquals("Bravo", menu.findViewById<View>(R.id.home_tile_menu_frame).accessibilityPaneTitle)
    }

    @Test
    fun `the long press, and its release in the menu, open nothing`() {
        val activity = start().get()

        val menu = activity.longPress("b")

        assertTrue(menu.isShowing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    /** Puts OK down on the tile of [name] and holds it for [ms], not letting go. */
    private fun HomeActivity.holdOk(name: String, ms: Long) {
        tile(name).requestFocus()
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }

    private fun HomeActivity.releaseOk() = dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))

    // Luncher's own hold is 350 ms (menuHold), Robolectric's long-press timeout Android's 500.
    @Test
    fun `OK held a little less than the hold opens no menu, and released opens the app`() {
        val activity = start().get()

        activity.holdOk("b", 340)

        assertNull(shownMenu())
        assertNull(shadowOf(activity).nextStartedActivity)
        activity.releaseOk()
        assertEquals("com.example.b", shadowOf(activity).nextStartedActivity.component?.packageName)
    }

    @Test
    fun `OK held for the hold opens the menu, before Android's long press`() {
        val activity = start().get()
        assertTrue(ViewConfiguration.getLongPressTimeout() > 360)

        activity.holdOk("b", 360)

        assertNotNull(shownMenu())
        assertEquals(listOf("Move", "Hide", "Rename", "Tile banner", "App info"), shownMenu()!!.labels())
    }

    // On Android 12 and later, OK let go before the menu's first frame comes to the tile, whose
    // window still has the focus: Robolectric never moves it to the menu, so it always comes there.
    @Test
    fun `OK released on the tile after the hold opened the menu opens nothing`() {
        val activity = start().get()
        activity.holdOk("b", 360)

        activity.releaseOk()

        assertNull(shadowOf(activity).nextStartedActivity)
        assertFalse(activity.tile("b").isPressed)
        assertTrue(shownMenu()!!.isShowing)
    }

    // Android's own long press, still timed as OK went down, would open the menu again, at its
    // first entry.
    @Test
    fun `the menu the hold opened isn't opened again by Android's long press`() {
        val activity = start().get()
        activity.holdOk("b", 360)
        val menu = checkNotNull(shownMenu())
        menu.press(KeyEvent.KEYCODE_DPAD_DOWN)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout().toLong()))

        assertTrue("Hide, where Down took the focus, keeps it", menu.entries()[1].isFocused)
    }

    // A timeout longer than Android's defaults is the user's, from the accessibility settings.
    @Test
    @Config(shadows = [LongerLongPressTimeout::class])
    fun `with a longer long-press timeout than Android's, OK held for the hold opens nothing`() {
        assertEquals(1000, ViewConfiguration.getLongPressTimeout())
        val activity = start().get()

        activity.holdOk("b", 360)

        assertNull(shownMenu())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700))
        assertNotNull("Android's long press opens it", shownMenu())
    }

    @Test
    fun `with no other app shown, the menu offers no move`() {
        installedApps.apps = listOf(app("a"), app("x"))
        val activity = start().get()

        assertEquals(listOf("Hide", "Rename", "Tile banner", "App info"), activity.longPress("a").labels())
    }

    @Test
    fun `App info opens the app's page in the device's settings, in a task of its own`() {
        val activity = start().get()
        val menu = activity.longPress("b")

        menu.choose("App info")

        assertTrue("the menu stays until the page has come", menu.isShowing)
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals(Uri.parse("package:${app("b").launchable.packageName}"), intent.data)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `Tile banner opens the picker for the app's tile, over the home screen in its task, saying where the tile shows`() {
        val activity = start().get()
        val menu = activity.longPress("b")

        menu.choose("Tile banner")

        assertTrue("the menu stays until the picker has come", menu.isShowing)
        val intent = shadowOf(activity).nextStartedActivity
        // Where the tile is in the window, which covers the screen, scrolled as it shows, before its zoom.
        val tile = activity.tile("b")
        val tiles = tile.parent as View
        val window = IntArray(2).also(tiles::getLocationInWindow)
        val place = TilePlace(window[0] + tile.left - tiles.scrollX, window[1] + tile.top - tiles.scrollY, 1920, 1080)
        val expected = TileBannerActivity.intent(activity, app("b").launchable, place)
        assertEquals(expected.component, intent.component)
        assertEquals(expected.extras!!.values(), intent.extras!!.values())
        assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // The picker covers the home screen whole, which is stopped meanwhile and shows the result as it starts.
    @Test
    fun `the picture the picker saved for a tile is on it once the home screen is back`() {
        val controller = start()
        val activity = controller.get()
        activity.longPress("b").choose("Tile banner")
        controller.pause().stop()

        val image = TileImage(PictureId("beach"), Framing(Fit.STRETCH))
        customizations.save(mapOf(app("b").launchable to AppCustomization(image = image)))
        controller.restart().resume()

        assertEquals(image, activity.tile("b").app.customization.image)
        assertTrue(activity.tile("b").isFocused)
    }

    /** Each extra, arrays as lists, so two bundles compare by what they hold. */
    @Suppress("DEPRECATION")
    private fun Bundle.values(): Map<String, Any?> = keySet().associateWith { get(it).let { value -> if (value is IntArray) value.toList() else value } }

    // The settings app disabled since the menu opened.
    @Test
    fun `App info says so when nothing opens the app's page`() {
        val activity = start().get()
        // Nothing handles the intent here, so starting it now throws, as on such a device.
        shadowOf(activity.application).checkActivities(true)

        activity.longPress("b").choose("App info")

        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals("App info isn't available on this device", ShadowToast.getTextOfLatestToast())
    }

    // Nothing comes over the home screen: nothing to wait for.
    @Test
    fun `App info closes the menu at once when nothing opens the app's page`() {
        val activity = start().get()
        shadowOf(activity.application).checkActivities(true)
        val menu = activity.longPress("b")

        menu.choose("App info")

        assertFalse(menu.isShowing)
    }

    @Test
    fun `where the device has no page for the app, App info is dimmed, and the focus passes it by`() {
        appInfoScreen.exists = false
        val activity = start().get()
        val menu = activity.longPress("b")
        val (move, _, _, image, info) = menu.entries()

        assertEquals(listOf("Move", "Hide", "Rename", "Tile banner", "App info"), menu.labels())
        assertTrue(info.alpha < 1f)
        assertEquals(1f, image.alpha)
        assertFalse(info.isFocusable)
        assertTrue(move.isFocused)
        // Down from the entry above it goes nowhere: Android's search finds no focusable view.
        image.requestFocus()
        assertNull(image.focusSearch(View.FOCUS_DOWN))
    }

    @Test
    fun `OK on a dimmed App info does nothing`() {
        appInfoScreen.exists = false
        val activity = start().get()
        val menu = activity.longPress("b")

        menu.entries().last().performClick()

        assertTrue(menu.isShowing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    // Asked at each opening: a settings app can be disabled or enabled meanwhile.
    @Test
    fun `whether App info is dimmed is asked each time the menu opens`() {
        val activity = start().get()
        activity.longPress("b").press(KeyEvent.KEYCODE_BACK)
        appInfoScreen.exists = false

        val menu = activity.longPress("b")

        assertFalse(menu.entries().last().isFocusable)
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
            for (entry in menu.entries()) assertEquals("an entry's, with $corners", expected.second * density, entry.corner, 0.5f)
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

    // A fourth row, p and q: the second's and third's tiles have room neither above nor below for the menu.
    @Test
    fun `the menu of a tile in the fourth row is above it, clear of its zoom`() {
        installedApps.apps = ('a'..'q').map { app(it.toString()) } + app("x")
        val activity = start().get()
        val menu = activity.longPress("p")

        val attributes = checkNotNull(menu.window).attributes
        val tile = activity.tile("p").focusedBounds()
        assertTrue("menu at ${attributes.y}, tile at ${tile.top}", attributes.y + menu.window!!.decorView.measuredHeight < tile.top)
        val tileOnScreen = IntArray(2).also { activity.tile("p").getLocationOnScreen(it) }
        assertTrue(tile.top < tileOnScreen[1] + activity.tile("p").height)
    }

    // 720p at 320 dpi, 640x360 dp: a second-row tile has room neither above nor below for it.
    @Test
    @Config(qualifiers = "en-rUS-w640dp-h360dp-land-television-xhdpi-notouch-dpad")
    fun `with room neither above nor below, the menu is beside its tile, clear of it`() {
        val activity = start().get()
        val tile = activity.tiles().first { it.top > activity.tiles().first().top }   // the second row's first
        val menu = activity.longPress(tile.app.name.lowercase())

        val window = checkNotNull(menu.window)
        val x = window.attributes.x
        val y = window.attributes.y
        val width = window.decorView.measuredWidth
        val height = window.decorView.measuredHeight
        val drawn = tile.focusedBounds()
        assertTrue("menu at x $x to ${x + width}, tile at ${drawn.left} to ${drawn.right}", x >= drawn.right || x + width <= drawn.left)
        assertTrue("menu at y $y to ${y + height}, tile at ${drawn.top} to ${drawn.bottom}", y < drawn.bottom && y + height > drawn.top)
    }

    // 720p at 320 dpi, 640x360 dp, three tiles a row: the second row's middle tile has room
    // neither beside it nor for the whole menu above or below it.
    @Test
    @Config(qualifiers = "en-rUS-w640dp-h360dp-land-television-xhdpi-notouch-dpad")
    fun `with room for the whole menu nowhere, it scrolls in the most room there is, clear of its tile`() {
        val activity = start().get()
        val secondRow = activity.tiles().filter { it.top > activity.tiles().first().top }
        val tile = secondRow[1]
        val menu = activity.longPress(tile.app.name.lowercase())
        shadowOf(Looper.getMainLooper()).idle()   // the window's layout
        val window = checkNotNull(menu.window)
        val scroll = menu.findViewById<View>(R.id.home_tile_menu_scroll)
        val entries = menu.entries()

        val y = window.attributes.y
        val height = window.attributes.height
        val drawn = tile.focusedBounds()
        assertTrue("menu at y $y to ${y + height}, tile at ${drawn.top} to ${drawn.bottom}", y + height <= drawn.top || y >= drawn.bottom)
        assertTrue("the list doesn't scroll", scroll.canScrollVertically(1))
        // Whole rows, one height for all, in the window, less its margin.
        val padding = activity.resources.getDimensionPixelSize(R.dimen.home_menu_padding)
        val row = entries.first().height
        assertTrue(entries.all { it.height == row })
        assertEquals("window $height px, rows ${entries.map { it.height }} px", 0, (height - 2 * padding) % row)
    }

    @Test
    @Config(qualifiers = "en-rUS-w640dp-h360dp-land-television-xhdpi-notouch-dpad")
    fun `Down scrolls the menu to its last entry`() {
        val activity = start().get()
        val tile = activity.tiles().filter { it.top > activity.tiles().first().top }[1]
        val menu = activity.longPress(tile.app.name.lowercase())
        val scroll = menu.findViewById<View>(R.id.home_tile_menu_scroll)

        repeat(menu.entries().size - 1) {
            menu.press(KeyEvent.KEYCODE_DPAD_DOWN)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))   // the slide
        }

        assertTrue(menu.entries().last().isFocused)
        assertTrue(scroll.scrollY > 0)
        assertFalse("the list scrolls past its last entry", scroll.canScrollVertically(1))
    }

    @Test
    @Config(qualifiers = "en-rUS-w640dp-h360dp-land-television-xhdpi-notouch-dpad")
    fun `a menu opened again starts at its first entry`() {
        val activity = start().get()
        val tile = activity.tiles().filter { it.top > activity.tiles().first().top }[1]
        val first = activity.longPress(tile.app.name.lowercase())
        repeat(2) {
            first.press(KeyEvent.KEYCODE_DPAD_DOWN)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        }
        first.press(KeyEvent.KEYCODE_BACK)

        val menu = activity.longPress(tile.app.name.lowercase())

        assertEquals(0, menu.findViewById<View>(R.id.home_tile_menu_scroll).scrollY)
        assertTrue(menu.entries().first().isFocused)
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
        assertNotNull(menu.entries().first().label.text)
    }

    private fun TileMenu.windowAnimations() = checkNotNull(window).attributes.windowAnimations

    // Its own animation, which grows out of the middle of its edge facing the tile.
    @Test
    fun `the menu comes out of its tile, below it and above it`() {
        installedApps.apps = ('a'..'q').map { app(it.toString()) } + app("x")
        val activity = start().get()

        assertEquals(R.style.Animation_Luncher_Home_Menu_Below, activity.longPress("b").windowAnimations())
        shownMenu()!!.press(KeyEvent.KEYCODE_BACK)
        assertEquals(R.style.Animation_Luncher_Home_Menu_Above, activity.longPress("p").windowAnimations())
    }

    // Three tiles a row: the second row's first tile has more room on its right, its last on its left.
    @Test
    @Config(qualifiers = "en-rUS-w640dp-h360dp-land-television-xhdpi-notouch-dpad")
    fun `the menu comes out of its tile, beside it on either side`() {
        val activity = start().get()
        val secondTop = activity.tiles().first { it.top > activity.tiles().first().top }.top
        val secondRow = activity.tiles().filter { it.top == secondTop }

        assertEquals(R.style.Animation_Luncher_Home_Menu_Right, activity.longPress(secondRow.first().app.name.lowercase()).windowAnimations())
        shownMenu()!!.press(KeyEvent.KEYCODE_BACK)
        assertEquals(R.style.Animation_Luncher_Home_Menu_Left, activity.longPress(secondRow.last().app.name.lowercase()).windowAnimations())
    }

    // Android 5.1 and 6.0 play no app dialog's window entrance: the menu plays it in its window,
    // starting as it's first drawn, and the window only fades as it goes.
    @Test
    @Config(sdk = [23])
    fun `where Android plays no dialog's entrance, the menu plays it in its window`() {
        val activity = start().get()
        activity.tile("b").requestFocus()
        activity.tile("b").performLongClick()
        val menu = checkNotNull(shownMenu()) { "no menu" }
        val decor = checkNotNull(menu.window).decorView

        assertEquals(R.style.Animation_Luncher_Home_Menu_ExitOnly, menu.windowAnimations())
        assertEquals("not drawn yet", 0f, decor.alpha)
        assertEquals(0.92f, decor.scaleX)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(1f, decor.alpha)
        assertEquals(1f, decor.scaleX)
        assertEquals(1f, decor.scaleY)
        // Below its tile: out of the middle of its top edge.
        assertEquals(decor.width / 2f, decor.pivotX)
        assertEquals(0f, decor.pivotY)
    }

    // The menu stays under the rename dialog until it has drawn: the first one of a process is made
    // then, and the screen would show bare meanwhile.
    @Test
    fun `Rename shows its dialog first, and the menu closes at once, with no key and no fade, once it has drawn`() {
        val activity = start().get()
        val menu = activity.longPress("a")

        menu.choose("Rename")

        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        org.junit.Assert.assertNotSame(menu, dialog)
        assertTrue("the dialog shows", dialog.isShowing)
        assertTrue("the menu stays", menu.isShowing)
        assertEquals("with no exit animation", 0, menu.windowAnimations())
        assertTrue("and takes no key", menu.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)))

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        assertFalse("closed once the dialog has drawn", menu.isShowing)
        assertTrue(dialog.isShowing)
    }

    @Test
    fun `an entry that opens no window closes the menu with its fade, as before`() {
        val activity = start().get()
        val menu = activity.longPress("a")
        val fade = menu.windowAnimations()

        menu.choose("Hide")

        assertFalse(menu.isShowing)
        assertEquals(fade, menu.windowAnimations())
    }

    // Closed at once, it would leave the home screen bare for the time the picker takes to start.
    @Test
    fun `Tile banner keeps the menu up, taking no key but Back, until the picker covers the home screen`() {
        val controller = start()
        val activity = controller.get()
        val menu = activity.longPress("b")
        val tile = menu.entries().single { it.label.text == "Tile banner" }

        menu.choose("Tile banner")
        menu.press(KeyEvent.KEYCODE_DPAD_UP)
        controller.pause()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))

        assertTrue("up while the picker comes", menu.isShowing)
        assertTrue("its keys go nowhere", tile.isFocused)
        controller.stop()
        assertFalse("gone once the home screen is covered", menu.isShowing)
    }

    @Test
    fun `Back closes the menu while the picker starts`() {
        val activity = start().get()
        val menu = activity.longPress("b")
        menu.choose("Tile banner")

        menu.press(KeyEvent.KEYCODE_BACK)

        assertFalse(menu.isShowing)
    }

    // Android TV's App info is a side panel: the home screen shows under it, paused only.
    @Test
    fun `App info over part of the home screen leaves the menu as it was for when the user comes back`() {
        val controller = start()
        val activity = controller.get()
        val menu = activity.longPress("b")
        val info = menu.entries().last()

        menu.choose("App info")
        controller.pause()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        controller.resume()

        assertTrue(menu.isShowing)
        assertTrue(info.isFocused)
        menu.press(KeyEvent.KEYCODE_DPAD_UP)
        assertTrue("its keys are its own again", menu.entries()[3].isFocused)
    }

    // Where App info is a screen of its own, covering the home screen whole.
    @Test
    fun `App info covering the home screen whole closes the menu, gone when the user comes back`() {
        val controller = start()
        val activity = controller.get()
        val menu = activity.longPress("b")

        menu.choose("App info")
        controller.pause().stop()
        controller.restart().resume()

        assertFalse(menu.isShowing)
    }

    // The menu is made once and shown again for each tile: after a handover it takes its keys again.
    @Test
    fun `after Rename, the next menu takes its keys`() {
        val activity = start().get()
        activity.longPress("a").choose("Rename")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        org.robolectric.shadows.ShadowDialog.getLatestDialog().dismiss()

        val menu = activity.longPress("b")
        menu.press(KeyEvent.KEYCODE_DPAD_DOWN)

        assertTrue(menu.entries()[1].isFocused)
    }

    // Android 9 can leave a menu whose window got a key as it was added (OK released just as it
    // opens) without its window focus, deaf to every key, Back too: it asks Android for it again.
    @Test
    @Config(sdk = [28])
    fun `a menu left without the window focus asks Android for it again`() {
        val home = start().get()
        val menu = home.longPress("b")
        val window = checkNotNull(menu.window)
        var madeUnfocusable = false
        val callback = checkNotNull(window.callback)
        window.callback = object : Window.Callback by callback {
            override fun onWindowAttributesChanged(attrs: WindowManager.LayoutParams) {
                callback.onWindowAttributesChanged(attrs)
                if (attrs.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0) madeUnfocusable = true
            }
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WindowFocus.CHECK_AFTER))

        assertTrue("Robolectric gives a dialog no window focus", !window.decorView.hasWindowFocus())
        assertTrue(madeUnfocusable)
        assertEquals("focusable again", 0, window.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }
}
