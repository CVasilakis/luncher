package com.luncher.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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

    // An opaque picture, or an app's own image, has no look: none drawn, whatever is stored.
    @Test
    fun `an opaque image is drawn without the look`() {
        val plain = drawn(painter.parts(logo), 1f)
        val looked = drawn(painter.parts(logo, shape = shape, look = TileLook(TileBackdrop.DARK_GLASS, TileFocusShape.SHAPE)), 1f)

        assertTrue(plain.sameAs(looked))
        assertEquals(0, Color.alpha(plain.getPixel(40, 85)))
    }
}
