package com.luncher.launcher.pictures

import android.graphics.Color
import android.os.Looper
import com.luncher.domain.jobs.ManualJobs
import com.luncher.domain.pictures.FolderPicture
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureFormat
import org.junit.Assert.assertEquals
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
class MediaFolderPicturesTest {

    private val context = RuntimeEnvironment.getApplication()
    private val jobs = ManualJobs()

    /** The drop folder on the TV's storage (Robolectric has no storage volumes to ask). */
    private val dropFolder = File(context.getExternalFilesDir(null)!!.parentFile!!.parentFile, "../media/${context.packageName}")
        .canonicalFile.apply { mkdirs() }
    private val folders = MediaFolderPictures(context, jobs) { listOf(dropFolder) }

    private fun listed(): List<FolderPicture<android.net.Uri>> {
        var listed: List<FolderPicture<android.net.Uri>>? = null
        folders.list { listed = it }
        jobs.runAll()
        shadowOf(Looper.getMainLooper()).idle()
        return checkNotNull(listed)
    }

    @Test
    fun `lists the pictures a file manager put in the drop folder, upright`() {
        File(dropFolder, "wide.png").writeBytes(TestPictureFiles.png(TestPictureFiles.halves(64, 36, Color.RED, Color.BLUE)))
        File(dropFolder, "photo.jpg").writeBytes(TestPictureFiles.jpeg(TestPictureFiles.halves(80, 40, Color.RED, Color.BLUE), orientation = 6))

        val listed = listed().sortedBy { it.name }

        assertEquals(listOf("photo.jpg", "wide.png"), listed.map { it.name })
        assertEquals(listOf(40 to 80, 64 to 36), listed.map { it.width to it.height })
        assertEquals(listOf(null, null), listed.map { it.unsupported })
        assertEquals(File(dropFolder, "wide.png").length(), listed[1].bytes)
        assertEquals(File(dropFolder, "wide.png").path, listed[1].source.path)
    }

    @Test
    fun `lists a picture this TV can't show, marked, and leaves out what isn't a picture`() {
        File(dropFolder, "IMG_0001.HEIC").writeBytes(TestPictureFiles.heic())
        File(dropFolder, "notes.txt").writeText("not a picture")
        File(dropFolder, "subfolder").mkdirs()

        val listed = listed()

        assertEquals(listOf("IMG_0001.HEIC"), listed.map { it.name })
        assertEquals(PictureFormat.HEIF, listed.single().unsupported)
        assertEquals(0, listed.single().width)
    }

    @Test
    fun `lists in the background, answering on the main thread`() {
        var listed: List<FolderPicture<android.net.Uri>>? = null
        folders.list { listed = it }
        assertEquals(null, listed)
        jobs.runAll()
        assertEquals(null, listed)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(emptyList<FolderPicture<android.net.Uri>>(), listed)
    }

    @Test
    fun `previews a picture framed at the size asked`() {
        File(dropFolder, "wide.png").writeBytes(TestPictureFiles.png(TestPictureFiles.halves(64, 36, Color.RED, Color.BLUE)))

        val preview = folders.preview(listed().single(), 32, 18, Framing()) { false }!!

        assertEquals(32, preview.width)
        assertTrue(TestPictureFiles.isRed(preview.getPixel(2, 9)))
        assertTrue(TestPictureFiles.isBlue(preview.getPixel(29, 9)))
    }

    @Test
    fun `stops drawing a preview once told, at its next read, giving nothing`() {
        File(dropFolder, "large.jpg").writeBytes(TestPictureFiles.jpeg(TestPictureFiles.halves(1600, 900, Color.RED, Color.BLUE), orientation = 1))
        val picture = listed().single()
        var asked = 0

        // Told to stop once the decode has asked twice: it reads no further.
        val preview = folders.preview(picture, 32, 18, Framing()) { ++asked > 2 }

        assertNull(preview)
        assertNull(folders.preview(picture, 32, 18, Framing()) { true })
    }
}
