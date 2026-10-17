package com.luncher.launcher.settings

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TV_1080P)
class SharePictureActivityTest {

    private val context = RuntimeEnvironment.getApplication()
    private val picture = Uri.parse("content://com.example.files/sunset.jpg")

    /** Sends [intent], as another app would: what it started, after finishing at once. */
    private fun send(intent: Intent): Intent {
        val activity = Robolectric.buildActivity(SharePictureActivity::class.java, intent).create().get()
        assertTrue("finished before it's shown", activity.isFinishing)
        return shadowOf(activity).nextStartedActivity
    }

    private fun assertPassesThePicture(started: Intent) {
        assertEquals(PicturesActivity::class.java.name, started.component?.className)
        assertEquals(picture, started.data)
        assertTrue("the read access passed on", started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `passes a shared picture to Pictures, with the access to read it`() {
        assertPassesThePicture(send(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, picture)))
    }

    @Test
    fun `takes the picture from the clip when the stream extra isn't there`() {
        val intent = Intent(Intent.ACTION_SEND).setType("image/jpeg")
        intent.clipData = ClipData.newRawUri("", picture)

        assertPassesThePicture(send(intent))
    }

    @Test
    fun `passes a picture to Set as`() {
        assertPassesThePicture(send(Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(picture, "image/jpeg")))
    }

    @Test
    fun `with no picture to pass, has Pictures say it couldn't read one`() {
        for (intent in listOf(Intent(Intent.ACTION_SEND).setType("image/jpeg"), Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, "not a Uri"), Intent("com.example.OTHER"))) {
            val started = send(intent)

            assertEquals(PicturesActivity::class.java.name, started.component?.className)
            assertNull(started.data)
            assertTrue(started.getBooleanExtra(PicturesActivity.EXTRA_NOTHING_READABLE, false))
            assertFalse(started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        }
    }
}
