package com.luncher.launcher.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.HorizontalAlignment
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.TileStyle
import com.luncher.domain.appearance.VerticalAlignment
import com.luncher.domain.appearance.appearanceChanges
import android.os.Looper
import com.luncher.domain.jobs.ManualJobs
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.PictureId
import com.luncher.launcher.testing.TV_1080P
import org.robolectric.Shadows.shadowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.management.ManagementFactory

/** The appearance editor's preview alone, at about the size it has in the editor at 1080p. Native graphics: it's drawn. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = TV_1080P)
class AppearancePreviewViewTest {

    private val jobs = ManualJobs()

    private val preview = AppearancePreviewView(RuntimeEnvironment.getApplication(), null).apply {
        measure(exactly(WIDTH), exactly(HEIGHT))
        layout(0, 0, WIDTH, HEIGHT)
        showWallpapers(FakePictureImages(::sampleWallpaper), jobs)
    }

    private val canvas = Canvas(Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888))

    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    private fun exactly(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private fun draw(appearance: Appearance): Bitmap {
        preview.appearance = appearance
        jobs.runAll()                                   // its wallpaper, drawn in the background
        shadowOf(Looper.getMainLooper()).idle()        // and shown
        return Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also { preview.draw(Canvas(it)) }
    }

    /** The bytes this thread allocates running [block], on average over many runs, after as many to warm up. */
    private fun allocated(block: () -> Unit): Double {
        repeat(RUNS) { block() }   // classes loaded, code compiled
        val start = threads.currentThreadAllocatedBytes
        repeat(RUNS) { block() }
        return (threads.currentThreadAllocatedBytes - start).toDouble() / RUNS
    }

    // The catalog-wide check (docs/ARCHITECTURE.md#appearance): an option the preview ignores, or
    // a value of it that looks like another in the sample, fails here.
    @Test
    fun `every value of every option changes what the preview draws`() {
        for (change in appearanceChanges()) {
            assertFalse("$change draws as ${change.option} at its default", draw(change.base).sameAs(draw(change.changed)))
        }
    }

    // Rule 5 (docs/ARCHITECTURE.md): drawing allocates nothing.
    @Test
    fun `drawing allocates nothing`() {
        preview.appearance = Appearance(TileGeometry(verticalAlignment = VerticalAlignment.BOTTOM, horizontalAlignment = HorizontalAlignment.RIGHT), TileStyle(Corners.Radius(12)))

        val perDraw = allocated { preview.draw(canvas) }

        // Below the smallest object, so a one-off allocation of the JVM's own doesn't count.
        assertTrue("$perDraw bytes per draw", perDraw < 16)
    }

    // What it draws from is made anew only for a part that changed; an equal appearance, as each
    // read gives, makes nothing anew.
    @Test
    fun `an equal appearance makes nothing anew`() {
        val appearance = Appearance(TileGeometry(verticalAlignment = VerticalAlignment.BOTTOM, horizontalAlignment = HorizontalAlignment.RIGHT), TileStyle(Corners.Radius(12)))
        val equal = appearance.copy()
        preview.appearance = appearance

        val perSet = allocated { preview.appearance = if (preview.appearance === appearance) equal else appearance }

        assertTrue("$perSet bytes per equal appearance", perSet < 16)
    }

    // The tiles are laid out anew only for a new geometry, which allocates (a TileLayouts); a new
    // style only scales the radius again.
    @Test
    fun `a new tile style lays nothing out again`() {
        val square = Appearance(tileStyle = TileStyle(Corners.Radius(0)))
        val round = Appearance(tileStyle = TileStyle(Corners.Radius(12)))
        val top = square.copy(tileGeometry = TileGeometry(verticalAlignment = VerticalAlignment.TOP))

        val perStyle = allocated { preview.appearance = if (preview.appearance == square) round else square }
        val perGeometry = allocated { preview.appearance = if (preview.appearance == square) top else square }

        assertTrue("$perStyle bytes per new style", perStyle < 16)
        assertTrue("$perGeometry bytes per new geometry", perGeometry >= 16)
    }

    // The sample screen has the TV's shape, as the wallpaper the home screen draws (screenSize),
    // so a picture is cut there as on the home screen.
    @Test
    @Config(qualifiers = "w720dp-h540dp-land-television-xhdpi-notouch-dpad")   // 4:3
    fun `takes the TV's shape, and draws the wallpaper at it`() {
        val drawn = mutableListOf<Pair<Int, Int>>()
        val preview = AppearancePreviewView(RuntimeEnvironment.getApplication(), null).apply {
            measure(exactly(WIDTH), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            layout(0, 0, measuredWidth, measuredHeight)
            showWallpapers(FakePictureImages { picture, width, height, style -> sampleWallpaper(picture, width, height, style).also { drawn += width to height } }, jobs)
        }

        preview.appearance = Appearance(background = Background(PictureId("sunset")))
        jobs.runAll()

        assertEquals(WIDTH * 3 / 4, preview.measuredHeight)
        assertEquals(listOf(WIDTH to WIDTH * 3 / 4), drawn)
    }

    // What's on the wallpaper has the home screen's edge (WallpaperOutline): every light shape the
    // preview draws on a black picture has dark pixels around it on a white one. The focus frame,
    // orange, isn't light, and has none, as on the home screen.
    @Test
    fun `every shape on the wallpaper has an outline`() {
        val preview = AppearancePreviewView(RuntimeEnvironment.getApplication(), null).apply {
            measure(exactly(WIDTH), exactly(HEIGHT))
            layout(0, 0, WIDTH, HEIGHT)
            showWallpapers(FakePictureImages { picture, width, height, _ -> solid(picture, width, height) }, jobs)
        }
        fun drawOn(picture: String): Bitmap {
            preview.appearance = Appearance(background = Background(PictureId(picture)))
            jobs.runAll()
            shadowOf(Looper.getMainLooper()).idle()
            return Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also { preview.draw(Canvas(it)) }
        }
        val onWhite = drawOn("white")

        val shapes = lightShapes(drawOn("black"))

        // The time, the date, the gear, and the tiles but the focused one.
        assertEquals(9, shapes.size)
        for (shape in shapes) {
            val around = Rect(shape).apply { inset(-2, -2) }
            assertTrue("the shape at $shape has no outline", (around.left until around.right).any { x ->
                (around.top until around.bottom).any { y -> brightness(onWhite.getPixel(x, y)) < 128 }
            })
        }
    }

    private fun solid(picture: PictureId, width: Int, height: Int) = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(if (picture.value == "white") Color.WHITE else Color.BLACK) }

    private fun brightness(color: Int) = minOf(Color.red(color), Color.green(color), Color.blue(color))

    /** The bounds of each group of touching light pixels in [image]. */
    private fun lightShapes(image: Bitmap): List<Rect> {
        val seen = BooleanArray(image.width * image.height)
        val shapes = mutableListOf<Rect>()
        for (start in seen.indices) {
            if (seen[start] || brightness(image.getPixel(start % image.width, start / image.width)) <= 128) continue
            val bounds = Rect(start % image.width, start / image.width, start % image.width + 1, start / image.width + 1)
            val next = ArrayDeque(listOf(start))
            seen[start] = true
            while (next.isNotEmpty()) {
                val at = next.removeFirst()
                val x = at % image.width
                val y = at / image.width
                bounds.union(x, y)
                for (dx in -1..1) for (dy in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until image.width || ny !in 0 until image.height) continue
                    val near = ny * image.width + nx
                    if (seen[near] || brightness(image.getPixel(nx, ny)) <= 128) continue
                    seen[near] = true
                    next += near
                }
            }
            shapes += bounds
        }
        return shapes
    }

    private companion object {
        const val WIDTH = 1040
        const val HEIGHT = 585
        const val RUNS = 1000
    }
}
