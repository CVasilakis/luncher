package com.luncher.launcher.home

import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Looper
import android.text.format.DateFormat
import android.view.View
import android.widget.TextView
import com.luncher.domain.appearance.AppName
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.HorizontalAlignment
import com.luncher.domain.appearance.Side
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.TileStyle
import com.luncher.domain.appearance.TopBarAppearance
import com.luncher.domain.appearance.VerticalAlignment
import com.luncher.domain.apps.AppAction
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.AppMenuEntry
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeAppInfoScreen
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.clock.FakeClock
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.wallpaper.FakeStoredWallpaper
import com.luncher.domain.wallpaper.WallpaperDrawing
import com.luncher.launcher.testing.TV_SCREENS
import com.luncher.launcher.testing.TV_SCREENS_IN_EVERY_LANGUAGE
import com.luncher.launcher.testing.TvScreen
import com.luncher.launcher.testing.assertApart
import com.luncher.launcher.testing.assertInside
import com.luncher.launcher.testing.assertWhole
import com.luncher.launcher.testing.bounds
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.junit.After
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
import org.robolectric.shadows.ShadowDialog
import com.luncher.launcher.ui.R as UiR

/**
 * The home screen's layout on every screen of [TV_SCREENS], in every language: everything inside
 * the TV's overscan margin, nothing overlapping, no text cut, and tiles of about the size they're
 * meant to be; a tile's menu too. With the longest time and date the language has, 12 apps shown
 * and 3 hidden.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // real text widths; without it a character is 1 px wide
@Config(sdk = [33, Config.TARGET_SDK])    // 33 for API 22 to 33, which scale large text more (TvDevice.kt)
class HomeLayoutTest(private val screen: TvScreen) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens() = TV_SCREENS_IN_EVERY_LANGUAGE.map { arrayOf<Any>(it) }

        /** A held tile's zoom (AppTileView), the largest a tile gets. */
        const val HELD_ZOOM = 1.15f
    }

    private val shown = listOf("an app name far too long for its tile") + (1..11).map { "app%02d".format(it) }
    private val hidden = listOf("x1", "x2", "x3")

    private val defaultLocale = Locale.getDefault()

    private val appearance = FakeAppearanceSettings()
    private val stored = FakeStoredWallpaper<Bitmap>()
    private val appInfo = FakeAppInfoScreen()

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    @Before
    fun useScreenAndFakes() {
        RuntimeEnvironment.setFontScale(screen.fontScale)
        RuntimeEnvironment.setQualifiers(screen.qualifiers)
        // The language formats the clock and sorts the apps too, through the default Locale, which
        // Robolectric sets from a test's @Config only.
        Locale.setDefault(RuntimeEnvironment.getApplication().resources.configuration.locales[0])
        val application = RuntimeEnvironment.getApplication() as HomeTestApplication
        application.graph = object : TestHomeGraph(application) {
            override val installedApps = FakeInstalledApps((shown + hidden).map(::app))
            override val appArrangements = FakeAppArrangements(AppArrangement(order = null, hidden = hidden.map { app(it).launchable }))
            override val clock = longestDate().let { FakeClock(FakeClock.at(it.year, it.monthValue, it.dayOfMonth, 23, 55, uses24Hour = false)) }   // "11:55 PM"
            override val appearanceSettings = this@HomeLayoutTest.appearance
            override val storedWallpaper = this@HomeLayoutTest.stored
            override val appInfoScreen = this@HomeLayoutTest.appInfo
        }
    }

    private fun start() = Robolectric.buildActivity(HomeActivity::class.java).setup().get()

    /**
     * The day of 2026 whose date is longest in the screen's language, formatted as [ClockView]
     * does: "Wednesday, September 30" in English.
     */
    private fun longestDate(): LocalDate {
        val locale = RuntimeEnvironment.getApplication().resources.configuration.locales[0]
        val format = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
        format.timeZone = TimeZone.getTimeZone("UTC")
        val days = generateSequence(LocalDate.of(2026, 1, 1)) { it.plusDays(1) }.takeWhile { it.year == 2026 }
        return days.maxBy { format.format(Date.from(it.atStartOfDay(ZoneOffset.UTC).toInstant())).length }
    }

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
        val meant = TileGeometry().tileWidthDp * resources.displayMetrics.density
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
        // Its name below it, as far as it can reach zoomed, clear of the others too.
        val paints = findViewById<AppTilesView>(R.id.home_apps).paints
        if (!paints.showsNames) return
        for (a in tiles) {
            val zoomed = bounds(a, HELD_ZOOM)
            val name = RectF(zoomed.left, zoomed.bottom, zoomed.right, zoomed.bottom + paints.nameHeight)
            for (b in tiles - a) assertApart(screen, "${a.app.label}'s name and ${b.app.label}", name, bounds(b))
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

    // The clock and the settings entry swapped, the time after the date; the rows at the bottom, a
    // short row centered.
    @Test
    fun `the home screen fits with every option away from its default`() {
        appearance.appearance = Appearance(
            TileGeometry(verticalAlignment = VerticalAlignment.BOTTOM, horizontalAlignment = HorizontalAlignment.CENTER, fullBottomRow = true),
            TileStyle(Corners.Radius(16)),
            TopBarAppearance(clockSide = Side.RIGHT, timeSide = Side.RIGHT),
        )

        `the home screen fits`()
    }

    // The focused tile lifted, its name below it: the rows further apart.
    @Test
    fun `the home screen fits with the focused tile lifted and its name shown`() {
        appearance.appearance = Appearance(TileGeometry(appName = AppName.WHEN_FOCUSED), TileStyle(focusFrame = false))

        `the home screen fits`()
    }

    @Test
    fun `moving an app fits with the focused tile's name shown`() {
        appearance.appearance = Appearance(TileGeometry(appName = AppName.WHEN_FOCUSED), TileStyle(focusFrame = false))

        `moving an app fits`()
    }

    @Test
    fun `moving an app fits`() {
        val activity = start()
        val tile = activity.tiles().first()
        tile.startMoving()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))   // the next frames: layout, zoom
        val title = activity.findViewById<TextView>(R.id.home_move_title)
        val hint = activity.findViewById<TextView>(R.id.home_move_hint)
        assertTrue(tile.held)

        for (view in listOf(title, hint)) assertInside(screen, activity.safeArea(), activity.name(view), bounds(view))
        // The title may end in "…" where a language's title and hint don't both fit, never the hint.
        assertWhole(screen, hint)
        activity.checkTiles()
    }

    /**
     * The menu of [tile], opened as a long press of OK would, the tile's zoom done, with its
     * entries, or [entries] instead: inside the overscan margin (across, or in line with the zoomed
     * tile's edge) and below the top bar, clear of the tile as it's drawn (above, below or beside
     * it), its entries' labels whole, and the entries it doesn't show whole reached by scrolling.
     */
    private fun HomeActivity.checkMenu(tile: AppTileView, entries: List<AppMenuEntry>? = null) {
        tile.requestFocus()
        tile.performLongClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val menu = checkNotNull(shownMenu()) { "no menu for ${tile.app.label} on $screen" }
        if (entries != null) {
            menu.show(tile, entries) {}
            shadowOf(Looper.getMainLooper()).idle()
        }
        val window = checkNotNull(menu.window)
        // A window of its own, which Robolectric doesn't place: where and as big as TileMenu made it.
        val x = window.attributes.x.toFloat()
        val y = window.attributes.y.toFloat()
        val bounds = RectF(x, y, x + window.attributes.width, y + window.attributes.height)
        val area = safeArea()
        val topBar = bounds(findViewById(R.id.home_top_bar))
        val what = "the menu of ${tile.app.label}, ${menu.entries().size} entries"
        val drawn = tile.focusedBounds().let { RectF(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }

        // Across, a row's first or last tile's menu is in line with its edge, as far past the
        // margin as the tile's zoom.
        assertInside(screen, RectF(minOf(area.left, drawn.left), maxOf(area.top, topBar.bottom), maxOf(area.right, drawn.right), area.bottom), what, bounds)
        assertApart(screen, "$what and its tile", bounds, drawn)
        for (entry in menu.entries()) assertWhole(screen, entry.label)
        // What doesn't show scrolls: the list's window and the rows it holds.
        val scroll = menu.findViewById<View>(R.id.home_tile_menu_scroll)
        val column = menu.findViewById<View>(R.id.home_tile_menu)
        assertTrue("$what: the list ${scroll.height} px of ${column.height} px, doesn't scroll on $screen", scroll.height >= column.height || scroll.canScrollVertically(1))
        menu.dismiss()
    }

    /** The tiles whose menus are checked: a row's first, middle and last, the next row's middle, and the last one. */
    private fun HomeActivity.menuTiles(): List<AppTileView> {
        val tiles = visibleTiles()
        val rows = tiles.groupBy { bounds(it).top }.values.toList()
        val second = rows.getOrNull(1)
        return listOfNotNull(rows[0].first(), rows[0][rows[0].size / 2], rows[0].last(), second?.get(second.size / 2), tiles.last()).distinct()
    }

    // Above a tile where there's room, below it in the first row, beside it on a short screen, or
    // scrolling where the whole menu fits nowhere.
    @Test
    fun `a tile's menu fits`() {
        val activity = start()

        for (tile in activity.menuTiles()) activity.checkMenu(tile)
    }

    // App info dimmed: as wide and tall, so it fits alike.
    @Test
    fun `a tile's menu fits with App info dimmed`() {
        appInfo.exists = false

        `a tile's menu fits`()
    }

    // More entries than the menu has today (later actions): taller than some screens' room, so
    // beside the tile or scrolling.
    @Test
    fun `a tile's menu of six entries fits`() {
        val activity = start()
        val six = listOf(AppAction.Move, AppAction.Hide, AppAction.Info, AppAction.Move, AppAction.Hide, AppAction.Info).map { AppMenuEntry(it) }

        for (tile in activity.menuTiles()) activity.checkMenu(tile, six)
    }

    // A wallpaper drawn again, for either reason: its window's message whole, inside the screen.
    @Test
    fun `the window saying the wallpaper is drawn again fits`() {
        val sunset = PictureId("sunset")
        appearance.appearance = Appearance(background = Background(sunset))
        for (kept in listOf(null, WallpaperDrawing(sunset, PictureStyle(), 1, 1) to Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))) {
            stored.kept = kept
            val activity = start()
            shadowOf(Looper.getMainLooper()).idle()
            val dialog = ShadowDialog.getLatestDialog()
            val message = dialog.findViewById<TextView>(UiR.id.working_message)
            // A window of its own, centered, which Robolectric doesn't place: inside the TV's
            // overscan margin if it fits within it.
            val panel = dialog.window!!.decorView
            val area = activity.safeArea()

            assertTrue(dialog.isShowing)
            assertTrue("the window ${panel.width} x ${panel.height} px, wider or taller than $area on $screen", panel.width <= area.width() && panel.height <= area.height())
            assertWhole(screen, message)
            dialog.dismiss()
        }
    }
}
