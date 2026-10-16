package com.luncher.launcher.pictures

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.PictureFormat
import com.luncher.launcher.jobs.BackgroundThreadJobs
import com.luncher.launcher.testing.SamplePictures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Which picture formats this Android version decodes, as the library judges them when another app
 * shares one: the sample files of assets/pictures/ (96 x 54 px, red at the left, blue at the
 * right), added through the real library into a folder of the test's own, which it deletes after.
 * HEIF isn't among them: no sample can be made here, and it's checked by hand (docs).
 */
@RunWith(AndroidJUnit4::class)
class PictureFormatsTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val folder = File(context.filesDir, "picture-formats-test")
    private val images = File(context.cacheDir, "picture-formats-test")
    private val jobs = BackgroundThreadJobs()
    private lateinit var library: FilesPictures

    @Before
    fun newLibrary() {
        folder.deleteRecursively()
        images.deleteRecursively()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { library = FilesPictures(context, jobs, folder, images) }
    }

    @After
    fun deleteIt() {
        folder.deleteRecursively()
        images.deleteRecursively()
    }

    /** Adds the sample [name] as another app shares it, and waits for the answer. */
    private fun add(name: String): ImportResult {
        val done = CountDownLatch(1)
        var result: ImportResult? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            library.add(SamplePictures.uri(name)) {
                result = it
                done.countDown()
            }
        }
        assertTrue("no answer for $name", done.await(30, TimeUnit.SECONDS))
        return checkNotNull(result)
    }

    private fun added(name: String) = (add(name) as? ImportResult.Added)?.picture ?: throw AssertionError("$name not added")

    @Test
    fun jpegPngWebpBmpGif_areAddedOnEveryVersion() {
        val everywhere = listOf("baseline.jpg", "progressive.jpg", "cmyk.jpg", "8bit.png", "16bit.png", "lossy.webp", "lossless-alpha.webp", "bitmap.bmp", "animated.gif")
        for (name in everywhere) {
            val picture = added(name)
            assertEquals(name, 96 to 54, picture.width to picture.height)
        }
    }

    @Test
    fun aPhotoTaggedAsTakenOnItsSide_isUpright() {
        val photo = added("exif6.jpg")

        assertEquals(54 to 96, photo.width to photo.height)
        // Stored red left and blue right, the tag says to turn it right: red on top.
        val image = FilePictureImages(context, folder).render(photo.id, 54, 96, PictureStyle())!!
        assertTrue(isRed(image.getPixel(27, 10)))
        assertTrue(isBlue(image.getPixel(27, 86)))
    }

    @Test
    fun avif_isAddedFromApi33_andBeforeNamedAsAFormatThisTvCantShow() {
        val result = add("av1.avif")

        if (Build.VERSION.SDK_INT >= 33) {
            assertTrue("$result", result is ImportResult.Added)
        } else {
            assertEquals(ImportResult.UnsupportedFormat(PictureFormat.AVIF), result)
        }
    }

    @Test
    fun aFileNamedLikeAPictureThatIsntOne_isNotAPicture() {
        assertEquals(ImportResult.NotAPicture, add("text.png"))
    }

    @Test
    fun everyPictureIsDrawnIntoArgb8888_whateverItDecodesTo() {
        val drawer = FilePictureImages(context, folder)
        for (name in listOf("16bit.png", "animated.gif", "lossless-alpha.webp", "cmyk.jpg")) {
            val image = drawer.render(added(name).id, 48, 27, PictureStyle())!!

            assertEquals(name, Bitmap.Config.ARGB_8888, image.config)
            assertTrue("$name: left not red", isRed(image.getPixel(4, 13)) || name == "lossless-alpha.webp")
        }
    }

    private fun isRed(color: Int) = Color.red(color) > 150 && Color.blue(color) < 100

    private fun isBlue(color: Int) = Color.blue(color) > 150 && Color.red(color) < 100
}
