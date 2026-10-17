package com.luncher.launcher.pictures

import android.graphics.Bitmap
import android.graphics.Color
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.PictureStyle
import com.luncher.launcher.ui.color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File
import com.luncher.launcher.ui.R as UiR

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // to make and decode pictures
class FilePictureImagesTest {

    private val context = RuntimeEnvironment.getApplication()
    private val folder = File(context.filesDir, "pictures").apply { mkdirs() }
    private val images = FilePictureImages(context, folder)

    /** A picture of the library whose file holds [bytes]. */
    private fun stored(bytes: ByteArray): PictureId {
        val picture = PictureId("p${bytes.size}")
        File(folder, picture.value).writeBytes(bytes)
        return picture
    }

    private fun framed(framing: Framing) = PictureStyle(framing)

    private val redBlue = stored(TestPictureFiles.png(TestPictureFiles.halves(200, 100, Color.RED, Color.BLUE)))

    private fun Bitmap.isRed(x: Int, y: Int) = TestPictureFiles.isRed(getPixel(x, y))

    private fun Bitmap.isBlue(x: Int, y: Int) = TestPictureFiles.isBlue(getPixel(x, y))

    @Test
    fun `draws an ARGB_8888 bitmap of exactly the size asked`() {
        val image = images.render(redBlue, 37, 21, framed(Framing()))!!

        assertEquals(37, image.width)
        assertEquals(21, image.height)
        assertEquals(Bitmap.Config.ARGB_8888, image.config)
    }

    @Test
    fun `fills a square with the side of a wide picture its position names`() {
        val left = images.render(redBlue, 50, 50, framed(Framing(Fit.FILL, PicturePosition.LEFT)))!!
        val right = images.render(redBlue, 50, 50, framed(Framing(Fit.FILL, PicturePosition.RIGHT)))!!
        val center = images.render(redBlue, 50, 50, framed(Framing(Fit.FILL, PicturePosition.CENTER)))!!

        assertTrue(left.isRed(2, 25) && left.isRed(47, 25))
        assertTrue(right.isBlue(2, 25) && right.isBlue(47, 25))
        assertTrue(center.isRed(2, 25) && center.isBlue(47, 25))
    }

    @Test
    fun `stretches the whole picture`() {
        val image = images.render(redBlue, 50, 50, framed(Framing(Fit.STRETCH)))!!

        assertTrue(image.isRed(2, 25) && image.isRed(22, 25))
        assertTrue(image.isBlue(27, 25) && image.isBlue(47, 25))
    }

    @Test
    fun `turns a photo taken on its side upright`() {
        // Stored 80 x 40, red left, blue right; tagged "rotate 90° clockwise to show": red at the top.
        val photo = stored(TestPictureFiles.jpeg(TestPictureFiles.halves(80, 40, Color.RED, Color.BLUE), orientation = 6))

        val image = images.render(photo, 40, 80, framed(Framing()))!!

        assertTrue(image.isRed(20, 5))
        assertTrue(image.isBlue(20, 75))
    }

    @Test
    fun `shows the home screen's background through a transparent picture`() {
        val clear = stored(TestPictureFiles.png(TestPictureFiles.halves(20, 10, Color.TRANSPARENT, Color.TRANSPARENT)))

        val image = images.render(clear, 20, 10, framed(Framing()))!!

        assertEquals(context.color(UiR.color.background), image.getPixel(10, 5))
    }

    @Test
    fun `dims by the share asked, toward black`() {
        val plain = images.render(redBlue, 50, 50, framed(Framing(Fit.STRETCH)))!!.getPixel(5, 25)
        val dimmed = images.render(redBlue, 50, 50, PictureStyle(Framing(Fit.STRETCH), dim = 40))!!.getPixel(5, 25)

        assertEquals(Color.red(plain) * 60 / 100.0, Color.red(dimmed).toDouble(), 3.0)
        assertEquals(255, Color.alpha(dimmed))
    }

    @Test
    fun `blurs the edge between colors, more at each level, and leaves the size`() {
        fun purpleAcross(blur: Blur): Int {
            val image = images.render(redBlue, 800, 100, PictureStyle(Framing(Fit.STRETCH), blur = blur))!!
            assertEquals(800, image.width)
            // Pixels around the middle, where red meets blue, that are neither.
            return (200 until 600).count { x -> image.getPixel(x, 50).let { !TestPictureFiles.isRed(it) && !TestPictureFiles.isBlue(it) } }
        }
        val purple = Blur.entries.map(::purpleAcross)

        for ((less, more) in purple.zipWithNext()) assertTrue("$purple", more > less)
    }

    @Test
    fun `a blurred picture is opaque, of exactly the size asked, odd sizes too`() {
        val image = images.render(redBlue, 37, 21, PictureStyle(Framing(), blur = Blur.MEDIUM))!!

        assertEquals(37, image.width)
        assertEquals(21, image.height)
        assertEquals(Bitmap.Config.ARGB_8888, image.config)
        for (x in 0 until 37) for (y in 0 until 21) assertEquals(255, Color.alpha(image.getPixel(x, y)))
    }

    @Test
    fun `the blur keeps a plain color as it is, each channel in its place, up to the edges`() {
        val color = Color.rgb(200, 100, 50)
        val image = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

        PictureFiles.blur(image, Blur.STRONG)

        for (x in 0 until 120) for (y in 0 until 60) assertEquals(color, image.getPixel(x, y))
    }

    @Test
    fun `dims a blurred picture by the share asked too`() {
        fun redAtTheLeft(dim: Int) = images.render(redBlue, 400, 100, PictureStyle(Framing(Fit.STRETCH), dim, Blur.MEDIUM))!!.getPixel(10, 50)
        val plain = redAtTheLeft(0)
        val dimmed = redAtTheLeft(40)

        assertEquals(Color.red(plain) * 60 / 100.0, Color.red(dimmed).toDouble(), 3.0)
        assertEquals(255, Color.alpha(dimmed))
    }

    @Test
    fun `nothing for a picture whose file is gone, and nothing kept`() {
        assertNull(images.render(PictureId("gone"), 10, 10, PictureStyle()))
        assertNull(images.cached(redBlue, 10, 10, PictureStyle()))
    }
}
