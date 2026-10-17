package com.luncher.launcher.pictures

import android.graphics.Bitmap
import android.graphics.Color
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.wallpaper.WallpaperDrawing
import com.luncher.launcher.images.PixelFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // to read pixels back
class FileStoredWallpaperTest {

    private val context = RuntimeEnvironment.getApplication()
    private val file = File(context.filesDir, "wallpaper")
    private val unfinished = File(context.filesDir, "wallpaper" + PixelFiles.TEMPORARY)
    private var stored = FileStoredWallpaper(context, file)

    private val drawing = WallpaperDrawing(
        PictureId("0123456789abcdef0123456789abcdef"),
        PictureStyle(Framing(Fit.FILL, PicturePosition.BOTTOM), dim = 40, blur = Blur.MEDIUM),
        384, 216,
    )

    private fun image(color: Int) = Bitmap.createBitmap(drawing.width, drawing.height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    /** As the next start: a new process, with what the file holds. */
    private fun restart() {
        stored = FileStoredWallpaper(context, file)
    }

    @Test
    fun `keeps nothing at first`() {
        assertNull(stored.drawing())
        assertNull(stored.read(drawing))
    }

    @Test
    fun `reads back on the next start what it kept, and what it was drawn from`() {
        val image = image(Color.rgb(40, 200, 50))
        stored.store(drawing, image)
        restart()

        assertEquals(drawing, stored.drawing())
        assertTrue(stored.read(drawing)!!.sameAs(image))
    }

    @Test
    fun `reads nothing for another drawing, size or style`() {
        stored.store(drawing, image(Color.RED))

        assertNull(stored.read(drawing.copy(width = 1920, height = 1080)))
        assertNull(stored.read(drawing.copy(style = PictureStyle())))
        assertNull(stored.read(drawing.copy(picture = PictureId("other"))))
    }

    @Test
    fun `keeps one wallpaper, the latest, and leaves no unfinished file`() {
        stored.store(drawing, image(Color.RED))
        val other = drawing.copy(picture = PictureId("other"))
        stored.store(other, image(Color.BLUE))

        assertEquals(other, stored.drawing())
        assertEquals(Color.BLUE, stored.read(other)!!.getPixel(5, 5))
        assertEquals(listOf("wallpaper"), context.filesDir.list()!!.filter { it.startsWith("wallpaper") })
    }

    @Test
    fun `deletes the kept one, and an unfinished one a killed process left`() {
        stored.store(drawing, image(Color.RED))
        unfinished.writeBytes(ByteArray(10))

        stored.delete()

        assertFalse(file.exists())
        assertFalse(unfinished.exists())
        assertNull(stored.drawing())
    }

    @Test
    fun `one another version drew, or a damaged one, reads as none`() {
        stored.store(drawing, image(Color.RED))
        // The same wallpaper, its drawing version (after the magic number and the format) one up.
        val bytes = file.readBytes()
        ByteBuffer.wrap(bytes).putInt(8, FilePictureImages.DRAWING + 1)
        file.writeBytes(bytes)
        restart()
        assertNull(stored.drawing())
        assertNull(stored.read(drawing))

        file.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(stored.drawing())
        assertNull(stored.read(drawing))
    }
}
