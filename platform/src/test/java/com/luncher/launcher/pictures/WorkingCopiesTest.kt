package com.luncher.launcher.pictures

import android.graphics.Bitmap
import android.graphics.Color
import com.luncher.domain.pictures.PreparingCause
import com.luncher.launcher.ui.ScreenSize
import com.luncher.launcher.ui.color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.random.Random
import com.luncher.launcher.ui.R as UiR

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // to make and decode pictures
class WorkingCopiesTest {

    private val context = RuntimeEnvironment.getApplication()
    private val originals = File(context.filesDir, "pictures").apply { mkdirs() }
    private val background = context.color(UiR.color.background)

    /** Half of it is 960 x 540 px, which a copy covers. */
    private val copies = WorkingCopies(File(context.filesDir, "picture-copies"), background) { ScreenSize(1920, 1080) }

    /** A picture's file holding [bytes]. */
    private fun original(name: String, bytes: ByteArray) = File(originals, name).apply { writeBytes(bytes) }

    /** [name]'s copy made from [file], as an import or a screen makes it. */
    private fun make(name: String, file: File): WorkingCopies.Made =
        copies.make(file, PictureFiles.shape(file)!!, copies.fileOf(name)) { false }

    /** The first bytes of [name]'s image, after the copy's header: a PNG's or a JPEG's signature. */
    private fun imageStart(name: String): List<Int> =
        copies.fileOf(name).readBytes().copyOfRange(HEADER_BYTES, HEADER_BYTES + 2).map { it.toInt() and 0xFF }

    private fun seeThroughMade(made: WorkingCopies.Made): Boolean = (made as WorkingCopies.Made.Written).seeThrough

    @Test
    fun `keeps a picture with see-through pixels as a PNG, see-through`() {
        val made = make("logo", original("logo", TestPictureFiles.png(TestPictureFiles.logo(400, 200))))

        assertTrue(seeThroughMade(made))
        assertEquals(PNG, imageStart("logo"))
        assertEquals(true, copies.seeThrough("logo"))
        val copy = copies.read("logo")!!
        assertEquals("clear around the logo", 0, Color.alpha(copy.getPixel(2, 2)))
        assertTrue(TestPictureFiles.isRed(copy.getPixel(200, 100)))
    }

    @Test
    fun `keeps a picture whose decoder says it has alpha, but which is opaque, as a JPEG, not see-through`() {
        val bytes = TestPictureFiles.png(TestPictureFiles.halves(400, 200, Color.RED, Color.BLUE))
        assertEquals("an RGBA PNG", 6, bytes[25].toInt())

        val made = make("halves", original("halves", bytes))

        assertFalse(seeThroughMade(made))
        assertEquals(JPEG, imageStart("halves"))
        assertEquals(false, copies.seeThrough("halves"))
    }

    @Test
    fun `keeps a JPEG as a JPEG, never see-through`() {
        val made = make("photo", original("photo", TestPictureFiles.jpeg(TestPictureFiles.halves(400, 200, Color.RED, Color.BLUE), 1)))

        assertFalse(seeThroughMade(made))
        assertEquals(JPEG, imageStart("photo"))
    }

    @Test
    fun `a JPEG cut short isn't see-through, its missing rows the background, as before`() {
        // Noise, so most of the file is the image's data, and the cut falls there.
        val random = Random(1)
        val noise = Bitmap.createBitmap(IntArray(400 * 400) { Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)) }, 400, 400, Bitmap.Config.ARGB_8888)
        val whole = TestPictureFiles.jpeg(noise, 1)
        val file = original("cut", whole.copyOf(whole.size / 2))

        val made = make("cut", file)

        assertFalse(seeThroughMade(made))
        assertEquals(JPEG, imageStart("cut"))
        val bottom = copies.read("cut")!!.getPixel(200, 395)
        assertEquals(255, Color.alpha(bottom))
        assertTrue("the background, not black: ${Integer.toHexString(bottom)}", near(bottom, background))
    }

    @Test
    fun `a copy as the earlier format wrote it isn't made, and says nothing of the picture`() {
        make("photo", original("photo", TestPictureFiles.jpeg(TestPictureFiles.halves(400, 200, Color.RED, Color.BLUE), 1)))
        val jpeg = ByteArrayOutputStream().also { copies.read("photo")!!.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
        val earlier = ByteArrayOutputStream()
        DataOutputStream(earlier).run {
            writeInt(0x4C57524B); writeInt(1); writeInt(FilePictureImages.DRAWING); writeInt(960); writeInt(540)
            write(jpeg)
        }
        copies.fileOf("photo").writeBytes(earlier.toByteArray())

        assertFalse(copies.made("photo"))
        assertEquals(PreparingCause.OUTDATED, copies.whyMake("photo"))
        assertNull(copies.seeThrough("photo"))
        assertNull(copies.read("photo"))
    }

    private fun near(color: Int, expected: Int) =
        abs(Color.red(color) - Color.red(expected)) <= 8 &&
            abs(Color.green(color) - Color.green(expected)) <= 8 &&
            abs(Color.blue(color) - Color.blue(expected)) <= 8

    private companion object {
        /** The copy's header: six Ints. */
        const val HEADER_BYTES = 24
        val PNG = listOf(0x89, 'P'.code)
        val JPEG = listOf(0xFF, 0xD8)
    }
}
