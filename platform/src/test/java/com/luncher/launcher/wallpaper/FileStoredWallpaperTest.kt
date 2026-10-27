package com.luncher.launcher.wallpaper

import android.graphics.Bitmap
import android.graphics.Color
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.wallpaper.DrawnWallpaper
import com.luncher.domain.wallpaper.WallpaperDrawing
import com.luncher.launcher.images.PixelFiles
import com.luncher.launcher.pictures.FilePictureImages
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

    /** A wallpaper of [color], and a frosted copy of another, an eighth of its size, to tell them apart. */
    private fun drawn(color: Int, frost: Int = Color.GRAY) =
        DrawnWallpaper(image(color), Bitmap.createBitmap(drawing.width / 8, drawing.height / 8, Bitmap.Config.ARGB_8888).apply { eraseColor(frost) })

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
    fun `reads back on the next start what it kept, its frosted copy with it, and what it was drawn from`() {
        val kept = drawn(Color.rgb(40, 200, 50), frost = Color.rgb(200, 210, 220))
        kept.frost.setPixel(3, 4, Color.RED)
        stored.store(drawing, kept)
        restart()

        assertEquals(drawing, stored.drawing())
        val read = stored.read(drawing)!!
        assertTrue(read.image.sameAs(kept.image))
        assertTrue(read.frost.sameAs(kept.frost))
    }

    @Test
    fun `reads nothing for another drawing, size or style`() {
        stored.store(drawing, drawn(Color.RED))

        assertNull(stored.read(drawing.copy(width = 1920, height = 1080)))
        assertNull(stored.read(drawing.copy(style = PictureStyle())))
        assertNull(stored.read(drawing.copy(picture = PictureId("other"))))
    }

    @Test
    fun `keeps one wallpaper, the latest, and leaves no unfinished file`() {
        stored.store(drawing, drawn(Color.RED, frost = Color.GREEN))
        val other = drawing.copy(picture = PictureId("other"))
        stored.store(other, drawn(Color.BLUE, frost = Color.YELLOW))

        assertEquals(other, stored.drawing())
        assertEquals(Color.BLUE, stored.read(other)!!.image.getPixel(5, 5))
        assertEquals(Color.YELLOW, stored.read(other)!!.frost.getPixel(5, 5))
        assertEquals(listOf("wallpaper"), context.filesDir.list()!!.filter { it.startsWith("wallpaper") })
    }

    @Test
    fun `deletes the kept one, and an unfinished one a killed process left`() {
        stored.store(drawing, drawn(Color.RED))
        unfinished.writeBytes(ByteArray(10))

        stored.delete()

        assertFalse(file.exists())
        assertFalse(unfinished.exists())
        assertNull(stored.drawing())
    }

    @Test
    fun `one another version drew, or a damaged one, reads as none`() {
        stored.store(drawing, drawn(Color.RED))
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

    @Test
    fun `one kept before frosted copies were, of the file's format before, reads as none, to be drawn again`() {
        // As the version before wrote it: format 1, the same header without the frosted copy's size, the wallpaper alone.
        PixelFiles(0x4C57414C, 1).write(file, image(Color.RED)) { output ->
            output.writeInt(FilePictureImages.DRAWING)
            output.writeUTF(drawing.picture.value)
            output.writeUTF(drawing.style.framing.fit.name)
            output.writeUTF(drawing.style.framing.position.name)
            output.writeInt(drawing.style.dim)
            output.writeUTF(drawing.style.blur.name)
            output.writeInt(drawing.width)
            output.writeInt(drawing.height)
        }
        restart()

        assertNull(stored.drawing())
        assertNull(stored.read(drawing))
    }

    @Test
    fun `a file cut short in its frosted copy reads as none`() {
        stored.store(drawing, drawn(Color.RED))
        file.writeBytes(file.readBytes().copyOf(file.length().toInt() - 4))
        restart()

        assertNull(stored.read(drawing))
    }
}
