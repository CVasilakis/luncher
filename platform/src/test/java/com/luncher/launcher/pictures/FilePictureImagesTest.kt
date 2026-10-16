package com.luncher.launcher.pictures

import android.graphics.Bitmap
import android.graphics.Color
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PicturePosition
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
    private fun stored(bytes: ByteArray, width: Int, height: Int): Picture {
        val picture = Picture(PictureId("p${bytes.size}"), "p", width, height, bytes.size.toLong(), 0)
        File(folder, picture.id.value).writeBytes(bytes)
        return picture
    }

    private val redBlue = stored(TestPictureFiles.png(TestPictureFiles.halves(200, 100, Color.RED, Color.BLUE)), 200, 100)

    private fun Bitmap.isRed(x: Int, y: Int) = TestPictureFiles.isRed(getPixel(x, y))

    private fun Bitmap.isBlue(x: Int, y: Int) = TestPictureFiles.isBlue(getPixel(x, y))

    @Test
    fun `draws an ARGB_8888 bitmap of exactly the size asked`() {
        val image = images.render(redBlue, 37, 21, Framing())!!

        assertEquals(37, image.width)
        assertEquals(21, image.height)
        assertEquals(Bitmap.Config.ARGB_8888, image.config)
    }

    @Test
    fun `fills a square with the side of a wide picture its position names`() {
        val left = images.render(redBlue, 50, 50, Framing(Fit.FILL, PicturePosition.LEFT))!!
        val right = images.render(redBlue, 50, 50, Framing(Fit.FILL, PicturePosition.RIGHT))!!
        val center = images.render(redBlue, 50, 50, Framing(Fit.FILL, PicturePosition.CENTER))!!

        assertTrue(left.isRed(2, 25) && left.isRed(47, 25))
        assertTrue(right.isBlue(2, 25) && right.isBlue(47, 25))
        assertTrue(center.isRed(2, 25) && center.isBlue(47, 25))
    }

    @Test
    fun `stretches the whole picture`() {
        val image = images.render(redBlue, 50, 50, Framing(Fit.STRETCH))!!

        assertTrue(image.isRed(2, 25) && image.isRed(22, 25))
        assertTrue(image.isBlue(27, 25) && image.isBlue(47, 25))
    }

    @Test
    fun `turns a photo taken on its side upright`() {
        // Stored 80 x 40, red left, blue right; tagged "rotate 90° clockwise to show": red at the top.
        val photo = stored(TestPictureFiles.jpeg(TestPictureFiles.halves(80, 40, Color.RED, Color.BLUE), orientation = 6), 40, 80)

        val image = images.render(photo, 40, 80, Framing())!!

        assertTrue(image.isRed(20, 5))
        assertTrue(image.isBlue(20, 75))
    }

    @Test
    fun `shows the home screen's background through a transparent picture`() {
        val clear = stored(TestPictureFiles.png(TestPictureFiles.halves(20, 10, Color.TRANSPARENT, Color.TRANSPARENT)), 20, 10)

        val image = images.render(clear, 20, 10, Framing())!!

        assertEquals(context.color(UiR.color.background), image.getPixel(10, 5))
    }

    @Test
    fun `nothing for a picture whose file is gone, and nothing kept`() {
        val gone = Picture(PictureId("gone"), "gone", 1, 1, 1, 0)

        assertNull(images.render(gone, 10, 10, Framing()))
        assertNull(images.cached(redBlue, 10, 10, Framing()))
    }
}
