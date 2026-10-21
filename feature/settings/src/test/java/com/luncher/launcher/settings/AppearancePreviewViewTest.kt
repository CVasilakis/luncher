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
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.jobs.ManualJobs
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.wallpaper.FakeStoredWallpaper
import com.luncher.domain.wallpaper.Wallpaper
import com.luncher.domain.wallpaper.WallpaperDrawing
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

    /** The home screen's wallpaper: none until a test keeps one and shows it. */
    private val stored = FakeStoredWallpaper<Bitmap>()
    private val home = Wallpaper(stored, FakePictureImages(::sampleWallpaper), FakeAppearanceSettings(), jobs) { it() }

    private val images = FakePictureImages(::sampleWallpaper)

    private val preview = AppearancePreviewView(RuntimeEnvironment.getApplication(), null).apply {
        measure(exactly(WIDTH), exactly(HEIGHT))
        layout(0, 0, WIDTH, HEIGHT)
        showWallpapers(home, images, jobs)
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
            showWallpapers(home, FakePictureImages { picture, width, height, style -> sampleWallpaper(picture, width, height, style).also { drawn += width to height } }, jobs)
        }

        preview.appearance = Appearance(background = Background(PictureId("sunset")))
        jobs.runAll()

        assertEquals(WIDTH * 3 / 4, preview.measuredHeight)
        assertEquals(listOf(WIDTH to WIDTH * 3 / 4), drawn)
    }

    // The home screen's wallpaper, in memory already, shows in the first frame: after the TV
    // started, the picture isn't drawn again in the background.
    @Test
    fun `draws the home screen's wallpaper at once, when it's of the background`() {
        val background = Background(PictureId("sunset"), dim = 20)
        homeShows(background, solid(PictureId("white"), 1920, 1080))

        preview.appearance = Appearance(background = background)

        assertEquals(0, jobs.pending)
        val drawn = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also { preview.draw(Canvas(it)) }
        assertEquals(Color.WHITE, drawn.getPixel(CLEAR_X, CLEAR_Y))
    }

    @Test
    fun `draws the picture itself in another style than the home screen's`() {
        homeShows(Background(PictureId("sunset")), solid(PictureId("white"), 1920, 1080))

        preview.appearance = Appearance(background = Background(PictureId("sunset"), dim = 20))

        assertEquals(1, jobs.pending)
    }

    // Drawing a 1920 px wallpaper at about 1000 px in one scale would read 2 of every 4 columns of
    // fine stripes, some of them twice: bands of black and white where the TV shows gray.
    @Test
    fun `shrinks the home screen's wallpaper without moiré`() {
        val background = Background(PictureId("stripes"))
        val stripes = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        for (x in 0 until stripes.width) for (y in 0 until stripes.height) stripes.setPixel(x, y, if (x % 2 == 0) Color.BLACK else Color.WHITE)
        homeShows(background, stripes)

        preview.appearance = Appearance(background = background)

        val drawn = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also { preview.draw(Canvas(it)) }
        val grays = (CLEAR_X - 100..CLEAR_X + 100).map { brightness(drawn.getPixel(it, CLEAR_Y)) }
        assertTrue("from ${grays.min()} to ${grays.max()}", grays.all { it in 112..143 })
    }

    // Back from another screen (onRestart), the picture shown is drawn again only if it was dropped.
    @Test
    fun `coming back with the background it shows draws nothing again`() {
        draw(Appearance(background = Background(PictureId("sunset"))))
        images.cache.clear()                            // as the working copies, which keep nothing

        preview.stopWallpaper()
        preview.resumeWallpaper()

        assertEquals(0, jobs.pending)
    }

    // Back from the background editor, which saved a new background: the editor resumes the
    // preview before it reads the appearance again, so the preview still has the one replaced.
    @Test
    fun `coming back after a new background was saved draws only the new one, the home screen's`() {
        val old = Background(PictureId("sunset"))
        val new = Background(PictureId("forest"), dim = 20)
        homeShows(old, solid(PictureId("black"), 1920, 1080))
        preview.appearance = Appearance(background = old)
        preview.stopWallpaper()
        home.save(new, 1920, 1080) {}
        jobs.runAll()

        preview.resumeWallpaper()
        assertEquals("the background replaced isn't drawn", 0, jobs.pending)
        preview.appearance = Appearance(background = new)

        assertEquals(0, jobs.pending)
        val drawn = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also { preview.draw(Canvas(it)) }
        assertEquals(sampleWallpaper(new.picture!!, WIDTH, HEIGHT, new.style).getPixel(CLEAR_X, CLEAR_Y), drawn.getPixel(CLEAR_X, CLEAR_Y))
    }

    /** The home screen showing [image] as [background]'s wallpaper, kept at 1080p. */
    private fun homeShows(background: Background, image: Bitmap) {
        stored.kept = WallpaperDrawing(background.picture!!, background.style, 1920, 1080) to image
        home.show(background, 1920, 1080)
    }

    // What's on the wallpaper has the home screen's edge (WallpaperOutline): every light shape the
    // preview draws on a black picture has dark pixels around it on a white one. The focus frame,
    // orange, isn't light, and has none, as on the home screen.
    @Test
    fun `every shape on the wallpaper has an outline`() {
        val preview = AppearancePreviewView(RuntimeEnvironment.getApplication(), null).apply {
            measure(exactly(WIDTH), exactly(HEIGHT))
            layout(0, 0, WIDTH, HEIGHT)
            showWallpapers(home, FakePictureImages { picture, width, height, _ -> solid(picture, width, height) }, jobs)
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

        /** A point on the sample screen with nothing over the wallpaper: between the date and the gear, above the tiles. */
        const val CLEAR_X = WIDTH / 2
        const val CLEAR_Y = 10
        const val RUNS = 1000
    }
}
