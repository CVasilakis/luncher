package com.luncher.launcher.pictures

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.PictureStyle
import com.luncher.launcher.ui.ScreenSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.DataInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // to make and decode pictures
class WorkingCopyImagesTest {

    private val context = RuntimeEnvironment.getApplication()
    private val originals = File(context.filesDir, "pictures").apply { mkdirs() }
    private val copies = File(context.filesDir, "picture-copies")

    /** The screen as it is: the tests change it as a TV switching its resolution would. */
    private var screen = ScreenSize(1920, 1080)
    private val images = WorkingCopyImages(context, originals, copies) { screen }

    /** A picture of the library whose file holds [bitmap], as a PNG. */
    private fun stored(name: String, bitmap: Bitmap): PictureId {
        File(originals, name).writeBytes(TestPictureFiles.png(bitmap))
        return PictureId(name)
    }

    /** The working copy's own size, after its header. */
    private fun copySize(picture: PictureId): Pair<Int, Int> =
        DataInputStream(File(copies, picture.value).inputStream()).use { input ->
            input.skipBytes(20)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, bounds)
            bounds.outWidth to bounds.outHeight
        }

    private val wide = stored("wide", TestPictureFiles.halves(4000, 1000, Color.RED, Color.BLUE))

    @Test
    fun `prepares a picture as a copy covering half the screen, its shape kept`() {
        assertFalse(images.isPrepared(wide))

        assertTrue(images.prepare(wide) { false })

        assertTrue(images.isPrepared(wide))
        assertEquals(2160 to 540, copySize(wide))
    }

    @Test
    fun `never makes a copy larger than the picture`() {
        val small = stored("small", TestPictureFiles.halves(200, 100, Color.RED, Color.BLUE))

        images.prepare(small) { false }

        assertEquals(200 to 100, copySize(small))
    }

    @Test
    fun `a copy made for another screen isn't prepared, and preparing replaces it, one file still`() {
        images.prepare(wide) { false }

        screen = ScreenSize(3840, 2160)

        assertFalse(images.isPrepared(wide))
        images.prepare(wide) { false }
        assertTrue(images.isPrepared(wide))
        assertEquals(4000 to 1000, copySize(wide))
        assertEquals(listOf("wide"), copies.list()!!.toList())
    }

    @Test
    fun `a copy of another format or drawing isn't prepared`() {
        images.prepare(wide) { false }
        val file = File(copies, wide.value)
        file.writeBytes(file.readBytes().also { it[7] = 99 })   // the format's version

        assertFalse(images.isPrepared(wide))
    }

    @Test
    fun `stopped, prepares nothing and leaves nothing`() {
        assertFalse(images.prepare(wide) { true })

        assertFalse(images.isPrepared(wide))
        assertEquals(emptyList<String>(), copies.list().orEmpty().toList())
    }

    @Test
    fun `a picture whose file is gone isn't prepared, and draws nothing`() {
        assertFalse(images.prepare(PictureId("gone")) { false })
        assertNull(images.render(PictureId("gone"), 96, 54, PictureStyle()) { false })
    }

    @Test
    fun `draws from the copy, framed, blurred and dimmed as the style says, of exactly the size asked`() {
        images.prepare(wide) { false }
        File(originals, wide.value).delete()   // so the copy is all it has

        val left = images.render(wide, 90, 60, PictureStyle(Framing(Fit.FILL, PicturePosition.LEFT))) { false }!!
        val stretched = images.render(wide, 90, 60, PictureStyle(Framing(Fit.STRETCH))) { false }!!
        val styled = images.render(wide, 91, 61, PictureStyle(Framing(Fit.STRETCH), dim = 60, blur = Blur.MEDIUM)) { false }!!

        assertTrue(TestPictureFiles.isRed(left.getPixel(5, 30)))
        assertTrue(TestPictureFiles.isRed(left.getPixel(85, 30)))
        assertTrue(TestPictureFiles.isRed(stretched.getPixel(5, 30)))
        assertTrue(TestPictureFiles.isBlue(stretched.getPixel(85, 30)))
        assertEquals(91, styled.width)
        assertEquals(61, styled.height)
        assertEquals(Bitmap.Config.ARGB_8888, styled.config)
        assertTrue("dimmed", Color.blue(styled.getPixel(88, 30)) < 120)
    }

    @Test
    fun `prepares a picture it's asked to draw first`() {
        val image = images.render(wide, 96, 54, PictureStyle()) { false }

        assertTrue(image != null)
        assertTrue(images.isPrepared(wide))
    }

    @Test
    fun `draws from the original when the copy can't be written`() {
        copies.parentFile!!.mkdirs()
        copies.writeText("a file where the folder should be")

        val image = images.render(wide, 96, 54, PictureStyle(Framing(Fit.STRETCH))) { false }!!

        assertFalse(images.isPrepared(wide))
        assertTrue(TestPictureFiles.isRed(image.getPixel(5, 27)))
        assertTrue(TestPictureFiles.isBlue(image.getPixel(90, 27)))
    }
}
