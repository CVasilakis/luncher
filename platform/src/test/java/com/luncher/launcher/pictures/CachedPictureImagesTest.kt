package com.luncher.launcher.pictures

import android.graphics.Bitmap
import android.graphics.Color
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // to read pixels back
class CachedPictureImagesTest {

    private val context = RuntimeEnvironment.getApplication()
    private val folder = File(context.cacheDir, "thumbnails")

    /** Draws each image in a color of its own, half transparent, one pixel nearly transparent. */
    private val drawn = FakePictureImages { _, width, height, _ ->
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.argb(128, 40, 200, 50))
            setPixel(0, 0, Color.argb(10, 255, 255, 255))
        }
    }
    private var cache = CachedPictureImages(context, drawn, folder)

    private val sunset = PictureId("sunset")

    /** As the next start: a new process, with what the files hold. */
    private fun restart() {
        cache = CachedPictureImages(context, drawn, folder)
    }

    @Test
    fun `keeps nothing before it has drawn`() {
        assertNull(cache.cached(sunset, 384, 216, PictureStyle()))
    }

    @Test
    fun `reads back on the next start what it drew, exactly, transparency included`() {
        val image = cache.render(sunset, 384, 216, PictureStyle())!!
        restart()

        val read = cache.cached(sunset, 384, 216, PictureStyle())!!

        assertTrue(read.sameAs(image))
        assertEquals(image.getPixel(0, 0), read.getPixel(0, 0))
        assertEquals(1, drawn.rendered.size)
    }

    @Test
    fun `keeps one file per picture, named by it, which deleting the picture deletes`() {
        cache.render(sunset, 384, 216, PictureStyle())

        assertTrue(File(folder, "sunset").isFile)
    }

    @Test
    fun `another size replaces what was kept, leaving no file of the old one`() {
        cache.render(sunset, 384, 216, PictureStyle())
        cache.render(sunset, 192, 108, PictureStyle())

        assertEquals(listOf("sunset"), folder.list()!!.toList())
        assertNull(cache.cached(sunset, 384, 216, PictureStyle()))
        assertEquals(192, cache.cached(sunset, 192, 108, PictureStyle())!!.width)
    }

    @Test
    fun `finds nothing for another size, framing, dim, blur or picture`() {
        cache.render(sunset, 384, 216, PictureStyle())

        assertNull(cache.cached(sunset, 300, 216, PictureStyle()))
        assertNull(cache.cached(sunset, 384, 216, PictureStyle(Framing(Fit.STRETCH))))
        assertNull(cache.cached(sunset, 384, 216, PictureStyle(dim = 20)))
        assertNull(cache.cached(sunset, 384, 216, PictureStyle(blur = Blur.LIGHT)))
        assertNull(cache.cached(PictureId("other"), 384, 216, PictureStyle()))
    }

    @Test
    fun `finds nothing in a file it can't read`() {
        cache.render(sunset, 384, 216, PictureStyle())
        val file = File(folder, "sunset")
        file.writeBytes(file.readBytes().copyOf(40))   // cut short

        assertNull(cache.cached(sunset, 384, 216, PictureStyle()))
    }
}
