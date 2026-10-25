package com.luncher.launcher.pictures

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.ManualJobs
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PicturePosition
import com.luncher.launcher.pictures.TestPictureFiles.isBlue
import com.luncher.launcher.pictures.TestPictureFiles.isRed
import com.luncher.launcher.ui.ScreenSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // to make and decode pictures
class WorkingCopyTilesTest {

    private val context = RuntimeEnvironment.getApplication()
    private val originals = File(context.filesDir, "pictures").apply { mkdirs() }
    private val copies = File(context.filesDir, "picture-copies")
    private val jobs = ManualJobs()

    /** The screen as it is: the tests change it as a TV switching its resolution would. */
    private var screen = ScreenSize(1920, 1080)
    private val prepared = WorkingCopyImages(context, originals, copies) { screen }

    /** The pictures whose copy was deleted as damaged, as the main thread was told. */
    private val damaged = mutableListOf<PictureId>()
    private val tiles = WorkingCopyTiles(context, jobs, originals, copies, { screen }) { damaged += it }

    /** A picture of the library, its left half red, its right half blue, 4:1, with its working copy made. */
    private fun wide(name: String = "wide"): PictureId {
        File(originals, name).writeBytes(TestPictureFiles.png(TestPictureFiles.halves(4000, 1000, Color.RED, Color.BLUE)))
        val picture = PictureId(name)
        assertTrue(prepared.prepare(picture) { false })
        return picture
    }

    private fun copyOf(picture: PictureId) = File(copies, picture.value)

    /** Where [tile] is red and where blue, across its middle row: a letter each tenth. */
    private fun across(tile: Bitmap): String = (0 until 10).joinToString("") { i ->
        val color = tile.getPixel(tile.width * i / 10 + tile.width / 20, tile.height / 2)
        when {
            isRed(color) -> "r"
            isBlue(color) -> "b"
            else -> "?"
        }
    }

    @Test
    fun `draws a picture into the tile at its size, framed as set`() {
        val wide = wide()

        val drawn = listOf(
            Framing(Fit.FILL, PicturePosition.LEFT),
            Framing(Fit.FILL, PicturePosition.CENTER),
            Framing(Fit.FILL, PicturePosition.RIGHT),
            Framing(Fit.STRETCH),
        ).map { framing -> tiles.draw(wide, framing, 307, 172)!! }

        drawn.forEach { assertEquals(307 to 172, it.width to it.height) }
        assertEquals(listOf("rrrrrrrrrr", "rrrrrbbbbb", "bbbbbbbbbb", "rrrrrbbbbb"), drawn.map(::across))
        // Filled, the tile shows a 16:9 part of the 4:1 picture; stretched, the whole: the middle's cut is half as far in.
        val filled = drawn[1]
        val stretched = drawn[3]
        assertTrue(isRed(filled.getPixel(150, 86)) && isBlue(filled.getPixel(157, 86)))
        assertTrue(isRed(stretched.getPixel(150, 86)) && isBlue(stretched.getPixel(157, 86)))
        assertEquals(0, jobs.pending)
    }

    @Test
    fun `draws from the working copy, never from the original`() {
        val wide = wide()
        File(originals, wide.value).delete()

        assertNotNull(tiles.draw(wide, Framing(), 307, 172))

        val other = PictureId("other")
        File(originals, other.value).writeBytes(TestPictureFiles.png(TestPictureFiles.halves(400, 100, Color.RED, Color.BLUE)))
        assertNull("no copy yet: nothing, though the original is there", tiles.draw(other, Framing(), 307, 172))
        assertFalse(copyOf(other).exists())
    }

    @Test
    fun `a copy made for another screen draws nothing, and is kept to be made again`() {
        val wide = wide()
        screen = ScreenSize(1280, 720)

        assertNull(tiles.draw(wide, Framing(), 205, 115))

        assertEquals(0, jobs.pending)
        assertTrue(copyOf(wide).exists())
    }

    @Test
    fun `a copy that doesn't decode draws nothing, and is deleted in a job of the library's files`() {
        val wide = wide()
        copyOf(wide).writeBytes(copyOf(wide).readBytes().copyOf(20) + ByteArray(500) { 7 })   // its header, then no picture

        assertNull(tiles.draw(wide, Framing(), 307, 172))

        assertTrue("not on the main thread", copyOf(wide).exists())
        assertEquals(listOf(JobPriority.PICTURE_LIBRARY), jobs.pendingPriorities())
        jobs.runAll()
        assertFalse(copyOf(wide).exists())
    }

    @Test
    fun `once a damaged copy is deleted, the main thread is told, to have it made again`() {
        val wide = wide()
        copyOf(wide).writeBytes(copyOf(wide).readBytes().copyOf(20) + ByteArray(500) { 7 })
        tiles.draw(wide, Framing(), 307, 172)

        assertEquals("not before it's deleted", emptyList<PictureId>(), damaged)
        jobs.runAll()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(wide), damaged)
    }

    @Test
    fun `a copy cut short in its header is damaged too`() {
        val wide = wide()
        copyOf(wide).writeBytes(copyOf(wide).readBytes().copyOf(10))

        assertNull(tiles.draw(wide, Framing(), 307, 172))
        jobs.runAll()

        assertFalse(copyOf(wide).exists())
    }

    @Test
    fun `a good copy made in place of a damaged one before its deleting runs stays`() {
        val wide = wide()
        val good = copyOf(wide).readBytes()
        copyOf(wide).writeBytes(good.copyOf(20) + ByteArray(500) { 7 })
        assertNull(tiles.draw(wide, Framing(), 307, 172))

        copyOf(wide).writeBytes(good)   // as a prepare job would, under the same name
        jobs.runAll()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(copyOf(wide).exists())
        assertEquals("nothing to make again", emptyList<PictureId>(), damaged)
        assertNotNull(tiles.draw(wide, Framing(), 307, 172))
    }

    @Test
    fun `a picture exists while its original is there`() {
        val wide = wide()

        assertTrue(tiles.exists(wide))
        File(originals, wide.value).delete()
        assertFalse(tiles.exists(wide))
    }
}
