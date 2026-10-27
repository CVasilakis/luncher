package com.luncher.launcher.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.luncher.domain.apps.TileBackdrop
import com.luncher.domain.apps.TileFocusShape
import com.luncher.domain.apps.TileLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The tile painter on its own: what a tile's parts say of its look, and the look drawn as far as
 * the focus came. The home screen's tests (feature/home's HomeTileLooksTest) check the tiles drawn
 * with it, and the screenshots how they look.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class TilePainterTest {

    private val context = RuntimeEnvironment.getApplication()
    private val painter = TilePainter(context)

    /** A logo: a red square in the middle of a see-through tile 300 x 170 px. */
    private val logo = Bitmap.createBitmap(300, 170, Bitmap.Config.ARGB_8888).apply {
        for (x in 120 until 180) for (y in 60 until 110) setPixel(x, y, Color.RED)
    }
    private val shape = TileShapes(context).make(logo)

    private fun drawn(parts: TileParts, focus: Float, framed: Boolean = true): Bitmap =
        Bitmap.createBitmap(300, 170, Bitmap.Config.ARGB_8888).also {
            painter.draw(Canvas(it), parts, radius = 16f, focus = focus, focused = focus > 0f, framed = framed, held = false)
        }

    @Test
    fun `the glass is as strong as the focus came, between its resting and its focused strength`() {
        val strengths = mapOf(
            TileBackdrop.LIGHT_GLASS to (TilePainter.LIGHT_GLASS to TilePainter.LIGHT_GLASS_FOCUSED),
            TileBackdrop.DARK_GLASS to (TilePainter.DARK_GLASS to TilePainter.DARK_GLASS_FOCUSED),
        )
        for ((backdrop, strength) in strengths) {
            val parts = painter.parts(logo, seeThrough = true, look = TileLook(backdrop))
            for (focus in listOf(0f, 0.5f, 1f)) {
                val expected = ((strength.first + (strength.second - strength.first) * focus) * 255).toInt()
                assertEquals("$backdrop at $focus", expected, Color.alpha(drawn(parts, focus).getPixel(40, 85)))
            }
        }
    }

    @Test
    fun `lifted without a backdrop, the light card comes with the focus`() {
        val parts = painter.parts(logo, seeThrough = true, look = TileLook(TileBackdrop.NONE))

        assertEquals(0, Color.alpha(drawn(parts, 0f, framed = false).getPixel(40, 85)))
        assertEquals((TilePainter.LIFT_CARD * 0.5f * 255).toInt(), Color.alpha(drawn(parts, 0.5f, framed = false).getPixel(40, 85)))
        assertEquals(0, Color.alpha(drawn(parts, 1f, framed = true).getPixel(40, 85)))
    }

    @Test
    fun `the band around the picture's shape is as strong as the focus came, drawn at the tile's scale where the shape is`() {
        val parts = painter.parts(logo, seeThrough = true, shape = shape, look = TileLook(TileBackdrop.NONE, TileFocusShape.SHAPE))

        val half = Color.alpha(drawn(parts, 0.5f).getPixel(182, 85))
        val whole = Color.alpha(drawn(parts, 1f).getPixel(182, 85))
        assertTrue("$half, then $whole", half in 1 until whole)
        // At its scale, in place: around the square on every side, not far from it.
        for ((x, y) in listOf(182 to 85, 117 to 85, 150 to 57, 150 to 112)) assertTrue("($x, $y)", Color.alpha(drawn(parts, 1f).getPixel(x, y)) > 64)
        for ((x, y) in listOf(200 to 85, 100 to 85, 150 to 40, 150 to 130)) assertEquals("($x, $y)", 0, Color.alpha(drawn(parts, 1f).getPixel(x, y)))
    }

    @Test
    fun `what the parts say of the look, for the lift`() {
        val opaque = painter.parts(logo, look = TileLook(TileBackdrop.DARK_GLASS, TileFocusShape.SHAPE))
        assertTrue(opaque.castsOutlineShadow)
        assertEquals(1f, opaque.outlineAlpha)
        assertFalse(opaque.followsFocus)

        val tile = painter.parts(logo, seeThrough = true, shape = shape, look = TileLook(focus = TileFocusShape.TILE))
        assertTrue(tile.castsOutlineShadow)
        assertEquals(TilePainter.SEE_THROUGH_OUTLINE, tile.outlineAlpha)
        assertTrue(tile.followsFocus)

        val shaped = painter.parts(logo, seeThrough = true, shape = shape, look = TileLook(focus = TileFocusShape.SHAPE))
        assertFalse(shaped.castsOutlineShadow)
        assertEquals(0f, shaped.outlineAlpha)

        // Without its masks, the focus follows the tile, as an opaque picture's.
        val shapeless = painter.parts(logo, seeThrough = true, look = TileLook(focus = TileFocusShape.SHAPE))
        assertTrue(shapeless.castsOutlineShadow)
    }

    /** A logo with soft edges: a red square, fading out to its left, over nothing. */
    private val softLogo = Bitmap.createBitmap(300, 170, Bitmap.Config.ARGB_8888).apply {
        for (x in 60 until 180) for (y in 60 until 110) setPixel(x, y, Color.argb(minOf(255, (x - 60) * 4), 255, 0, 0))
    }

    /** A sharp background, as stripes.jpg: stripes of strong colors, black and white. */
    private val stripes = Bitmap.createBitmap(300, 170, Bitmap.Config.ARGB_8888).apply {
        val colors = intArrayOf(Color.BLACK, Color.WHITE, Color.rgb(200, 30, 40), Color.rgb(20, 90, 200), Color.rgb(250, 200, 30))
        for (x in 0 until width) for (y in 0 until height) setPixel(x, y, colors[((x + y) / 12) % colors.size])
    }

    /** The resting tile of [parts] drawn over [stripes]. */
    private fun restingOverStripes(parts: TileParts, radius: Float): Bitmap = stripes.copy(Bitmap.Config.ARGB_8888, true).also {
        painter.draw(Canvas(it), parts, radius, focus = 0f, focused = false, framed = true, held = false)
    }

    /** [image] over its glass, [color] at [strength], each in a pass of its own over [stripes], as before the glass was composed. */
    private fun twoPasses(image: Bitmap, color: Int, strength: Float, radius: Float): Bitmap = stripes.copy(Bitmap.Config.ARGB_8888, true).also {
        val canvas = Canvas(it)
        val bounds = RectF(0f, 0f, image.width.toFloat(), image.height.toFloat())
        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            alpha = (strength * 255).toInt()
        }
        canvas.drawRoundRect(bounds, radius, radius, glass)
        if (radius == 0f) {
            canvas.drawBitmap(image, 0f, 0f, null)
        } else {
            val picture = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            picture.shader = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(bounds, radius, radius, picture)
        }
    }

    /** How far apart [a] and [b] are: the most any color of any pixel differs. */
    private fun levelsApart(a: Bitmap, b: Bitmap): Int {
        var most = 0
        for (x in 0 until a.width) for (y in 0 until a.height) {
            val p = a.getPixel(x, y)
            val q = b.getPixel(x, y)
            for (channel in listOf(Color::red, Color::green, Color::blue)) most = maxOf(most, abs(channel(p) - channel(q)))
        }
        return most
    }

    // At rest the glass is composed under the picture, in one shader: the same look as the glass
    // drawn first, then the picture, within a blend's rounding.
    @Test
    fun `a resting see-through tile looks as its glass drawn under it, round and square`() {
        val glasses = mapOf(
            TileBackdrop.LIGHT_GLASS to (Color.WHITE to TilePainter.LIGHT_GLASS),
            TileBackdrop.DARK_GLASS to (Color.BLACK to TilePainter.DARK_GLASS),
            TileBackdrop.FROSTED to (Color.WHITE to TilePainter.LIGHT_GLASS),   // as light glass, without a frost
        )
        for ((backdrop, glass) in glasses) for (radius in listOf(16f, 0f)) {
            val parts = painter.parts(softLogo, seeThrough = true, look = TileLook(backdrop))
            val apart = levelsApart(twoPasses(softLogo, glass.first, glass.second, radius), restingOverStripes(parts, radius))
            assertTrue("$backdrop, corners $radius: $apart levels apart", apart <= 2)
        }
    }

    @Test
    fun `a resting see-through tile with glass is drawn in one pass, a focused one in its glass's, the picture's and the frame's`() {
        for (backdrop in listOf(TileBackdrop.LIGHT_GLASS, TileBackdrop.DARK_GLASS, TileBackdrop.FROSTED)) {
            val parts = painter.parts(logo, seeThrough = true, look = TileLook(backdrop))
            for (radius in listOf(16f, 0f)) {
                assertEquals("$backdrop at rest, corners $radius", 1, passes(parts, radius, focus = 0f))
                assertEquals("$backdrop halfway, corners $radius", 3, passes(parts, radius, focus = 0.5f))
            }
        }
        // As before: no glass, or an opaque image, is the picture alone at rest.
        assertEquals(1, passes(painter.parts(logo, seeThrough = true, look = TileLook(TileBackdrop.NONE)), 16f, focus = 0f))
        assertEquals(1, passes(painter.parts(logo, look = TileLook(TileBackdrop.DARK_GLASS)), 16f, focus = 0f))
    }

    private fun passes(parts: TileParts, radius: Float, focus: Float): Int {
        val canvas = CountingCanvas(Bitmap.createBitmap(300, 170, Bitmap.Config.ARGB_8888))
        painter.draw(canvas, parts, radius, focus, focused = focus > 0f, framed = true, held = false)
        return canvas.draws
    }

    // Frosted: the wallpaper's frosted copy behind the tile, as it shows on the screen.

    /**
     * A frosted copy of a 1920 x 1080 screen, an eighth of its size, sharp, so its part is easy to
     * tell: blue from the screen's middle column on (x 960), green from its middle row on (y 544).
     */
    private val frost = TileFrost(
        Bitmap.createBitmap(240, 135, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until 240) for (y in 0 until 135) setPixel(x, y, Color.rgb(0, if (y >= 68) 200 else 0, if (x >= 120) 200 else 0))
        },
        1920, 1080,
    )

    private val frostedParts get() = painter.parts(logo, seeThrough = true, look = TileLook(TileBackdrop.FROSTED))

    /** The tile drawn with its corner at ([x], [y]) on the screen, zoomed by [zoom], as focused as [focus] says. */
    private fun frosted(x: Float, y: Float, zoom: Float = 1f, focus: Float = 0f, radius: Float = 16f): Bitmap =
        Bitmap.createBitmap(300, 170, Bitmap.Config.ARGB_8888).also {
            painter.frost = frost
            painter.draw(Canvas(it), frostedParts, radius, focus, focused = focus > 0f, framed = true, held = false, x = x, y = y, zoom = zoom)
        }

    /** [color] under white of [strength], as the frost's tint is. */
    private fun tinted(color: Int, strength: Float): Int {
        fun channel(value: Int) = (value * (1 - strength) + 255 * strength).toInt()
        return Color.rgb(channel(Color.red(color)), channel(Color.green(color)), channel(Color.blue(color)))
    }

    private fun assertNear(expected: Int, actual: Int, tolerance: Int = 2, message: String = "") {
        val apart = listOf(Color::red, Color::green, Color::blue, Color::alpha).maxOf { abs(it(expected) - it(actual)) }
        assertTrue("$message ${Integer.toHexString(actual)} is ${Integer.toHexString(expected)}", apart <= tolerance)
    }

    private val tint = (TilePainter.FROST_TINT * 255).toInt() / 255f
    private val black = Color.BLACK
    private val blue = Color.rgb(0, 0, 200)
    private val green = Color.rgb(0, 200, 0)

    @Test
    fun `a Frosted tile shows the frost behind it where it is on the screen, tinted, the picture over it`() {
        // Its left part left of the screen's middle, the rest right of it.
        val left = frosted(x = 940f, y = 400f)
        assertNear(tinted(black, tint), left.getPixel(12, 85), message = "left of the middle")
        assertNear(tinted(blue, tint), left.getPixel(28, 85), message = "right of it")
        assertEquals(Color.RED, left.getPixel(150, 85))
        // Lower, below the screen's middle row.
        assertNear(tinted(green, tint), frosted(x = 100f, y = 500f).getPixel(40, 85), message = "below the middle row")
        // Square corners: the same.
        assertNear(tinted(blue, tint), frosted(x = 940f, y = 400f, radius = 0f).getPixel(28, 85), message = "square")
    }

    @Test
    fun `a zoomed Frosted tile shows what's behind it as it shows, its zoom undone around its middle`() {
        // Zoomed 1.15 around its middle (1090 on the screen), the tile's x 28 shows at 950, left of the screen's middle.
        val zoomed = frosted(x = 940f, y = 400f, zoom = 1.15f)

        assertNear(tinted(black, tint), zoomed.getPixel(28, 85))
        assertNear(tinted(blue, tint), zoomed.getPixel(50, 85))
        // The frost fills the tile to its edges: what's behind the tile zoomed is the tile's whole shape.
        assertNear(tinted(black, tint), zoomed.getPixel(2, 85), message = "left edge")
        assertNear(tinted(blue, tint), zoomed.getPixel(297, 85), message = "right edge")
    }

    @Test
    fun `a Frosted tile's tint grows as the focus comes, to its focused strength`() {
        for (focus in listOf(0.5f, 1f)) {
            val strength = TilePainter.FROST_TINT + (TilePainter.FROST_TINT_FOCUSED - TilePainter.FROST_TINT) * focus
            assertNear(tinted(blue, strength), frosted(x = 1200f, y = 100f, focus = focus).getPixel(40, 85), tolerance = 3, message = "at $focus")
        }
    }

    @Test
    fun `a Frosted tile is drawn in two passes at rest, the frost with its tint, then the picture`() {
        painter.frost = frost
        for (radius in listOf(16f, 0f)) {
            assertEquals("at rest, corners $radius", 2, passes(frostedParts, radius, focus = 0f))
            assertEquals("halfway, corners $radius: frost, more tint, picture, frame", 4, passes(frostedParts, radius, focus = 0.5f))
        }
        // The frost is only for Frosted tiles.
        assertEquals(1, passes(painter.parts(logo, seeThrough = true, look = TileLook(TileBackdrop.LIGHT_GLASS)), 16f, focus = 0f))
    }

    // The frost is opaque: lifted, a Frosted tile rises as an opaque one, its outline's shadow whole.
    @Test
    fun `a Frosted tile's outline casts a whole shadow while there's a frost, the glass's lighter one without`() {
        val light = painter.parts(logo, seeThrough = true, look = TileLook(TileBackdrop.LIGHT_GLASS))
        val shaped = painter.parts(logo, seeThrough = true, shape = shape, look = TileLook(TileBackdrop.FROSTED, TileFocusShape.SHAPE))

        painter.frost = null
        assertEquals(TilePainter.SEE_THROUGH_OUTLINE, painter.outlineAlpha(frostedParts))
        painter.frost = frost
        assertEquals(1f, painter.outlineAlpha(frostedParts))
        assertEquals("glass, frost or not", TilePainter.SEE_THROUGH_OUTLINE, painter.outlineAlpha(light))
        assertEquals("the picture casts its own", 0f, painter.outlineAlpha(shaped))
        assertEquals("an opaque image", 1f, painter.outlineAlpha(painter.parts(logo)))
    }

    @Test
    fun `without a frost, a Frosted tile is Light glass`() {
        painter.frost = null
        val asFrosted = drawn(frostedParts, 0.5f)
        val asLight = drawn(painter.parts(logo, seeThrough = true, look = TileLook(TileBackdrop.LIGHT_GLASS)), 0.5f)

        assertTrue(asFrosted.sameAs(asLight))
    }

    // An opaque picture, or an app's own image, has no look: none drawn, whatever is stored.
    @Test
    fun `an opaque image is drawn without the look`() {
        val plain = drawn(painter.parts(logo), 1f)
        val looked = drawn(painter.parts(logo, shape = shape, look = TileLook(TileBackdrop.DARK_GLASS, TileFocusShape.SHAPE)), 1f)

        assertTrue(plain.sameAs(looked))
        assertEquals(0, Color.alpha(plain.getPixel(40, 85)))
    }
}

/** A canvas that counts what's drawn on it, each draw still drawn. */
private class CountingCanvas(bitmap: Bitmap) : Canvas(bitmap) {
    var draws = 0

    override fun drawRect(rect: RectF, paint: Paint) {
        draws++
        super.drawRect(rect, paint)
    }

    override fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) {
        draws++
        super.drawRoundRect(rect, rx, ry, paint)
    }

    override fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) {
        draws++
        super.drawBitmap(bitmap, left, top, paint)
    }

    override fun drawBitmap(bitmap: Bitmap, matrix: Matrix, paint: Paint?) {
        draws++
        super.drawBitmap(bitmap, matrix, paint)
    }
}
