package com.luncher.launcher.settings

import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.FakePictures.Companion.picture
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.PictureFormat
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The window over the home screen after another app shared a picture: adding it, then what to use it for, or why not. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class PictureReceivedActivityTest {

    private val application = RuntimeEnvironment.getApplication() as SettingsTestApplication
    private val shared = Uri.parse("content://com.example.files/holiday.jpg")
    private val holiday = picture("holiday.jpg", added = 9)
    private val library = FakePictures<Uri>().also { application.graph = TestPicturesGraph(it) }

    private fun receive(intent: Intent = Intent().setData(shared)): PictureReceivedActivity {
        val activity = Robolectric.buildActivity(PictureReceivedActivity::class.java, intent).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    private fun PictureReceivedActivity.title() = findViewById<TextView>(R.id.settings_received_title).text.toString()

    private fun PictureReceivedActivity.text() = findViewById<TextView>(R.id.settings_received_text).text.toString()

    private fun PictureReceivedActivity.spinning() = findViewById<View>(R.id.settings_received_spinner).visibility == View.VISIBLE

    private fun PictureReceivedActivity.entries(): List<TextView> =
        findViewById<LinearLayout>(R.id.settings_received_entries).let { list -> (0 until list.childCount).map { list.getChildAt(it) as TextView } }

    @Test
    fun `says it's adding the picture, with a spinning wheel, until it's done`() {
        library.results[shared] = ImportResult.Added(holiday)
        library.answerLater = true

        val activity = receive()

        assertEquals("Adding the picture…", activity.title())
        assertTrue(activity.spinning())
        assertEquals(emptyList<String>(), activity.entries().map { it.text.toString() })
        library.finishAdding()
        assertEquals("Picture saved", activity.title())
        assertTrue(!activity.spinning())
    }

    @Test
    fun `once saved, names the picture and offers its uses, the first focused`() {
        library.results[shared] = ImportResult.Added(holiday)

        val activity = receive()

        assertEquals("Picture saved", activity.title())
        assertEquals("holiday.jpg", activity.text())
        assertEquals(listOf("Background"), activity.entries().map { it.text.toString() })
        assertTrue(activity.entries().first().isFocused)
        assertEquals(listOf(holiday), library.pictures)
    }

    @Test
    fun `a picture the library has already is saved as well`() {
        library.results[shared] = ImportResult.AlreadyThere(holiday)

        assertEquals("Picture saved", receive().title())
    }

    @Test
    fun `Background opens the background editor with the picture chosen, and closes the window`() {
        library.results[shared] = ImportResult.Added(holiday)
        val activity = receive()

        activity.entries().single { it.text == "Background" }.performClick()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(BackgroundActivity::class.java.name, started.component?.className)
        assertEquals(holiday.id.value, started.getStringExtra(BackgroundActivity.EXTRA_PICTURE))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `one it couldn't add says why, and OK closes the window`() {
        library.results[shared] = ImportResult.UnsupportedFormat(PictureFormat.HEIF)

        val activity = receive()

        assertEquals("This picture wasn't added", activity.title())
        assertTrue(activity.text().contains("HEIF (HEIC)"))
        assertEquals(listOf("OK"), activity.entries().map { it.text.toString() })
        activity.entries().single().performClick()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `with nothing it could read, says so, adding nothing`() {
        val activity = receive(Intent().putExtra(PictureReceivedActivity.EXTRA_NOTHING_READABLE, true))

        assertTrue(activity.text().startsWith("Luncher can't read it."))
        assertEquals(emptyList<Uri>(), library.added)
    }

    @Test
    fun `Back closes it`() {
        library.results[shared] = ImportResult.Added(holiday)
        val activity = receive()

        @Suppress("DEPRECATION")
        activity.onBackPressed()

        assertTrue(activity.isFinishing)
    }
}
