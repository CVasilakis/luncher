package com.luncher.launcher.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // to draw and read pixels
class BlurredWallpaperFrostTest {

    private val frosts = BlurredWallpaperFrost()

    /** A wallpaper of 1080p: black on the left half, white on the right, a sharp edge in the middle. */
    private fun halves(): Bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.BLACK)
        Canvas(this).drawRect(960f, 0f, 1920f, 1080f, Paint().apply { color = Color.WHITE })
    }

    @Test
    fun `is an eighth of the wallpaper's size, opaque, and leaves the wallpaper as it is`() {
        val wallpaper = halves()
        val before = wallpaper.copy(Bitmap.Config.ARGB_8888, false)

        val frost = frosts.frost(wallpaper)

        assertEquals(240, frost.width)
        assertEquals(135, frost.height)
        assertEquals(Bitmap.Config.ARGB_8888, frost.config)
        assertFalse(wallpaper.isRecycled)
        assertTrue(wallpaper.sameAs(before))
        for (x in 0 until frost.width) assertEquals(0xFF, Color.alpha(frost.getPixel(x, 60)))
    }

    @Test
    fun `blurs a sharp edge into a gradual one, keeping the colors far from it`() {
        val frost = frosts.frost(halves())
        val row = (0 until frost.width).map { Color.red(frost.getPixel(it, 60)) }

        assertTrue("far left stays black: ${row.first()}", row.first() < 4)
        assertTrue("far right stays white: ${row.last()}", row.last() > 251)
        // Strong: 1.5 % of the width, about 3.6 px here; the edge spreads over several pixels each way.
        val between = row.count { it in 16..239 }
        assertTrue("$between pixels between black and white", between >= 8)
        assertTrue("it rises steadily", row.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `averages every pixel as it shrinks, so thin lines of a color aren't skipped`() {
        // One line in eight white: a frosted copy sampled every eighth pixel would see all or none.
        val wallpaper = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
            val canvas = Canvas(this)
            val paint = Paint().apply { color = Color.WHITE }
            for (x in 3 until 1920 step 8) canvas.drawRect(x.toFloat(), 0f, x + 1f, 1080f, paint)
        }

        val frost = frosts.frost(wallpaper)

        // An eighth of the light, everywhere: 255 / 8, give or take the rounding of each halving.
        for (x in listOf(10, 120, 230)) assertTrue("${Color.red(frost.getPixel(x, 60))} at $x", abs(Color.red(frost.getPixel(x, 60)) - 32) <= 3)
    }
}
