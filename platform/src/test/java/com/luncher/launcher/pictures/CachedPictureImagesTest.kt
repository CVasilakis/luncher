package com.luncher.launcher.pictures

import android.graphics.Bitmap
import android.graphics.Color
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PictureId
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
    private val folder = File(context.cacheDir, "pictures")

    /** Draws each image in a color of its own, half transparent, one pixel nearly transparent. */
    private val drawn = FakePictureImages { _, width, height, _ ->
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.argb(128, 40, 200, 50))
            setPixel(0, 0, Color.argb(10, 255, 255, 255))
        }
    }
    private var cache = CachedPictureImages(context, drawn, folder)

    private val sunset = Picture(PictureId("sunset"), "sunset.jpg", 1920, 1080, 1, 0)

    /** As the next start: a new process, with what the files hold. */
    private fun restart() {
        cache = CachedPictureImages(context, drawn, folder)
    }

    @Test
    fun `keeps nothing before it has drawn`() {
        assertNull(cache.cached(sunset, 384, 216, Framing()))
    }

    @Test
    fun `reads back on the next start what it drew, exactly, transparency included`() {
        val image = cache.render(sunset, 384, 216, Framing())!!
        restart()

        val read = cache.cached(sunset, 384, 216, Framing())!!

        assertTrue(read.sameAs(image))
        assertEquals(image.getPixel(0, 0), read.getPixel(0, 0))
        assertEquals(1, drawn.rendered.size)
    }

    @Test
    fun `keeps it in the picture's own folder, which deleting the picture deletes`() {
        cache.render(sunset, 384, 216, Framing())

        assertTrue(File(folder, "sunset/384x216").isFile)
    }

    @Test
    fun `finds nothing for another size, framing or picture`() {
        cache.render(sunset, 384, 216, Framing())

        assertNull(cache.cached(sunset, 300, 216, Framing()))
        assertNull(cache.cached(sunset, 384, 216, Framing(Fit.STRETCH)))
        assertNull(cache.cached(sunset.copy(id = PictureId("other")), 384, 216, Framing()))
    }

    @Test
    fun `finds nothing in a file it can't read`() {
        cache.render(sunset, 384, 216, Framing())
        val file = File(folder, "sunset/384x216")
        file.writeBytes(file.readBytes().copyOf(40))   // cut short

        assertNull(cache.cached(sunset, 384, 216, Framing()))
    }
}
