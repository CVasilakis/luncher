package com.luncher.launcher.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Looper
import android.view.View
import com.github.takahirom.roborazzi.captureRoboImage
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.TileStyle
import com.luncher.domain.apps.AppCustomization
import com.luncher.domain.apps.FakeAppCustomizations
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.TileBackdrop
import com.luncher.domain.apps.TileFocusShape
import com.luncher.domain.apps.TileImage
import com.luncher.domain.apps.TileLook
import com.luncher.domain.clock.FakeClock
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.PictureId
import com.luncher.launcher.pictures.FilePictureImages
import com.luncher.launcher.pictures.WorkingCopyImages
import com.luncher.launcher.testing.TV_1080P
import com.luncher.launcher.testing.captureFocused
import com.luncher.launcher.testing.focusFrontWindow
import com.luncher.launcher.ui.WorkingDialog
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * A see-through picture's tile with each look, over the wallpaper: every backdrop, its focus
 * following the tile or the picture's shape, framed and lifted. Reference images live in
 * src/test/screenshots/home/ of this module, as [HomeScreenshotTest]'s. The shadow a lifted tile's
 * outline casts is Robolectric's, drawn without the GPU: the emulators show a TV's.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-$TV_1080P")
class HomeTileLooksScreenshotTest {

    /** Every look: each backdrop with the focus following the tile, then each following the shape. */
    private val looks = TileFocusShape.entries.flatMap { focus -> TileBackdrop.entries.map { TileLook(it, focus) } }

    /**
     * The home screen over the landscape, or [wallpaper], an app per look of [looks], each showing
     * the logo with it, its tile prepared as the app prepares it; the focus on the first, its zoom
     * done.
     */
    private fun start(style: TileStyle, looks: List<TileLook> = this.looks, wallpaper: () -> PictureId = ::landscape): HomeActivity {
        val application = RuntimeEnvironment.getApplication() as HomeTestApplication
        val logo = seeThroughLogo()
        val background = Background(wallpaper(), Framing(Fit.FILL, PicturePosition.BOTTOM))
        // Numbered, so the order by name is the looks'.
        val digits = looks.size.toString().length
        val names = looks.mapIndexed { index, look -> "${(index + 1).toString().padStart(digits, '0')} ${label(look.backdrop)}, ${label(look.focus)}" }
        val graph = object : TestHomeGraph(application) {
            override val installedApps = FakeInstalledApps(names.map(::app))
            override val clock = FakeClock()
            override val appearanceSettings = FakeAppearanceSettings(Appearance(tileStyle = style, background = background))
            override val pictureImages = FilePictureImages(application)
            override val preparedPictures = WorkingCopyImages(application)
            override val appCustomizations = FakeAppCustomizations(
                names.zip(looks).associate { (name, look) -> app(name).launchable to AppCustomization(image = TileImage(logo, Framing(), look)) },
            )
        }
        application.graph = graph
        val activity = Robolectric.buildActivity(HomeActivity::class.java).setup().get()
        while (graph.jobs.pending > 0) {   // the wallpaper, the logo's copy, then its tiles
            graph.jobs.runAll()
            shadowOf(Looper.getMainLooper()).idle()
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WorkingDialog.SHORTEST))   // the window closed
        return activity
    }

    private fun label(value: Enum<*>) = value.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    private fun HomeActivity.tiles(): List<AppTileView> {
        val tiles = findViewById<AppTilesView>(R.id.home_apps)
        return (0 until tiles.childCount).map { tiles.getChildAt(it) as AppTileView }
    }

    private fun HomeActivity.focus(view: View) {
        view.requestFocus()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))   // the zoom, and the look's
    }

    private fun HomeActivity.screen(): Bitmap {
        val window = window.decorView
        return Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888).also { window.draw(Canvas(it)) }
    }

    // Framed, the first four tiles' focus following the tile, the last four's the logo's shape; the
    // first tile focused, without a backdrop: the frame around nothing but the logo.
    @Test
    fun tileLooksFramed() {
        start(TileStyle()).window.decorView.captureFocused("src/test/screenshots/home/home_tile_looks_framed.png")
    }

    // Lifted: the first tile, without a backdrop, gets a light card as the focus comes.
    @Test
    fun tileLooksLifted() {
        start(TileStyle(focusFrame = false)).window.decorView.captureFocused("src/test/screenshots/home/home_tile_looks_lifted.png")
    }

    // Each look at rest and focused, side by side, cut from the home screen as it's drawn: framed.
    @Test
    fun tileLooksFramedSheet() {
        sheet(start(TileStyle())).captureRoboImage("src/test/screenshots/home/home_tile_looks_framed_sheet.png")
    }

    // The same lifted.
    @Test
    fun tileLooksLiftedSheet() {
        sheet(start(TileStyle(focusFrame = false))).captureRoboImage("src/test/screenshots/home/home_tile_looks_lifted_sheet.png")
    }

    // Frosted tiles over a sharp wallpaper: each shows the stripes and rings behind it, blurred,
    // in line with them around the tile; at rest, the first focused (zoomed, its frost too), and
    // scrolled down to the last row. Each screen at half its size, one below the other.
    @Test
    fun tileLooksFrosted() {
        val activity = start(TileStyle(), looks = List(24) { TileLook(TileBackdrop.FROSTED) }, wallpaper = ::stripes)
        focusFrontWindow()
        val tiles = activity.tiles()
        activity.focus(activity.findViewById(R.id.home_settings))
        val resting = activity.screen()
        activity.focus(tiles.first())
        val focused = activity.screen()
        activity.focus(tiles.last())
        val scrolled = activity.screen()
        check(activity.findViewById<AppTilesView>(R.id.home_apps).scrollY > 0) { "the last row needs a scroll" }
        val width = resting.width / 2
        val height = resting.height / 2
        val sheet = Bitmap.createBitmap(width, 3 * height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        for ((row, screen) in listOf(resting, focused, scrolled).withIndex()) {
            canvas.drawBitmap(screen, null, Rect(0, row * height, width, (row + 1) * height), paint)
        }
        sheet.captureRoboImage("src/test/screenshots/home/home_tile_looks_frosted.png")
    }

    /**
     * A row per tile, its look named at the left, then the tile at rest and focused, each cut from
     * the home screen around the place it takes zoomed.
     */
    private fun sheet(activity: HomeActivity): Bitmap {
        focusFrontWindow()
        val tiles = activity.tiles()
        val first = tiles.first().focusedBounds()
        val margin = 24
        val cellWidth = first.right - first.left + 2 * margin
        val cellHeight = first.bottom - first.top + 2 * margin
        val labelWidth = 320
        val sheet = Bitmap.createBitmap(labelWidth + 2 * cellWidth, tiles.size * cellHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(34, 34, 34))
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
        }
        for ((row, tile) in tiles.withIndex()) {
            activity.focus(tile)
            val focused = activity.screen()
            activity.focus(activity.findViewById(R.id.home_settings))   // far from every tile
            val resting = activity.screen()
            val bounds = tile.focusedBounds()
            val cut = Rect(bounds.left - margin, bounds.top - margin, bounds.left - margin + cellWidth, bounds.top - margin + cellHeight)
            val top = row * cellHeight
            canvas.drawText(tile.app.name, 16f, top + cellHeight / 2f, label)
            canvas.drawBitmap(resting, cut, Rect(labelWidth, top, labelWidth + cellWidth, top + cellHeight), null)
            canvas.drawBitmap(focused, cut, Rect(labelWidth + cellWidth, top, labelWidth + 2 * cellWidth, top + cellHeight), null)
        }
        return sheet
    }
}
