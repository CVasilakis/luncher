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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** The window over the home screen after another app shared a picture: adding it, then what to use it for, or why not. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class PictureReceivedActivityTest {

    private val application = RuntimeEnvironment.getApplication() as SettingsTestApplication
    private val shared = Uri.parse("content://com.example.files/holiday.jpg")
    private val holiday = picture("holiday.jpg", added = 9)
    private val library = FakePictures<Uri>().also { application.graph = TestPicturesGraph(it) }

    private fun receive(intent: Intent = Intent().putParcelableArrayListExtra(PictureReceivedActivity.EXTRA_PICTURES, arrayListOf(shared))): PictureReceivedActivity = window(intent).get()

    /** The window, shown, to close as the test says. */
    private fun window(intent: Intent = Intent().putParcelableArrayListExtra(PictureReceivedActivity.EXTRA_PICTURES, arrayListOf(shared))): ActivityController<PictureReceivedActivity> {
        val window = Robolectric.buildActivity(PictureReceivedActivity::class.java, intent).setup()
        shadowOf(Looper.getMainLooper()).idle()
        return window
    }

    private fun PictureReceivedActivity.title() = findViewById<TextView>(R.id.settings_received_title).text.toString()

    private fun PictureReceivedActivity.text() = findViewById<TextView>(R.id.settings_received_text).text.toString()

    private fun PictureReceivedActivity.spinning() = findViewById<View>(R.id.settings_received_spinner).visibility == View.VISIBLE

    private fun PictureReceivedActivity.entries(): List<TextView> =
        findViewById<LinearLayout>(R.id.settings_received_entries).let { list -> (0 until list.childCount).map { list.getChildAt(it) as TextView } }

    @Test
    fun `says it's adding the picture, with a spinning wheel and Cancel, focused, until it's done`() {
        library.results[shared] = ImportResult.Added(holiday)
        library.answerLater = true

        val activity = receive()

        assertEquals("Adding the picture…", activity.title())
        assertTrue(activity.spinning())
        assertEquals(listOf("Cancel"), activity.entries().map { it.text.toString() })
        assertTrue(activity.entries().single().isFocused)
        library.finishAdding()
        assertEquals("Picture saved", activity.title())
        assertTrue(!activity.spinning())
        assertEquals(listOf("Background"), activity.entries().map { it.text.toString() })
    }

    @Test
    fun `Cancel closes the window, and the picture isn't added`() {
        library.results[shared] = ImportResult.Added(holiday)
        library.answerLater = true
        val window = window()

        window.get().entries().single().performClick()
        assertTrue(window.get().isFinishing)
        window.pause().stop().destroy()

        assertEquals(listOf(shared), library.cancelled)
        assertFalse(library.adding)
        assertEquals(emptyList<Any>(), library.pictures)
    }

    @Test
    fun `Back while it's adding closes the window, and the picture isn't added`() {
        library.results[shared] = ImportResult.Added(holiday)
        library.answerLater = true
        val window = window()

        @Suppress("DEPRECATION")
        window.get().onBackPressed()
        assertTrue(window.get().isFinishing)
        window.pause().stop().destroy()

        assertEquals(listOf(shared), library.cancelled)
        assertEquals(emptyList<Any>(), library.pictures)
    }

    @Test
    fun `closing the window once the picture is added cancels nothing`() {
        library.results[shared] = ImportResult.Added(holiday)
        val window = window()

        window.pause().stop().destroy()

        assertEquals(emptyList<Uri>(), library.cancelled)
        assertEquals(listOf(holiday), library.pictures)
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

    private val beach = Uri.parse("content://com.example.files/beach.jpg")
    private val forest = Uri.parse("content://com.example.files/forest.jpg")

    /** A share of [pictures], as SharePictureActivity sends it. */
    private fun share(vararg pictures: Uri) = Intent().putParcelableArrayListExtra(PictureReceivedActivity.EXTRA_PICTURES, arrayListOf(*pictures))

    @Test
    fun `adding several pictures says which one of how many, with Cancel, focused`() {
        library.answerLater = true

        val activity = receive(share(shared, beach, forest))

        assertEquals("Adding picture 1 of 3…", activity.title())
        assertTrue(activity.spinning())
        assertEquals(listOf("Cancel"), activity.entries().map { it.text.toString() })
        library.finishAdding()
        assertEquals("Adding picture 2 of 3…", activity.title())
        assertTrue(activity.entries().single().isFocused)
    }

    @Test
    fun `once several are added, says how many were saved and how many weren't, and only OK`() {
        library.results[shared] = ImportResult.Added(holiday)
        library.results[beach] = ImportResult.AlreadyThere(holiday)

        val activity = receive(share(shared, beach, forest))

        assertEquals("2 pictures saved", activity.title())
        assertEquals("1 picture wasn't added.", activity.text())
        assertEquals(listOf("OK"), activity.entries().map { it.text.toString() })
        assertTrue(activity.entries().single().isFocused)
    }

    @Test
    fun `when none of several was added, says so in the title alone`() {
        val activity = receive(share(shared, beach))

        assertEquals("The pictures weren't added", activity.title())
        assertEquals(View.GONE, activity.findViewById<View>(R.id.settings_received_text).visibility)
    }

    @Test
    fun `OK after several closes the window, the pictures in the library`() {
        library.results[shared] = ImportResult.Added(holiday)
        val window = window(share(shared, beach))

        window.get().entries().single().performClick()

        assertTrue(window.get().isFinishing)
        assertEquals(listOf(holiday), library.pictures)
        assertEquals(0, application.graph.receivedPictures.session)
    }

    @Test
    fun `a share that arrives while it adds joins the pictures being added`() {
        library.answerLater = true
        val window = window(share(shared, beach))

        window.newIntent(share(forest))

        assertEquals("Adding picture 1 of 3…", window.get().title())
        repeat(3) { library.finishAdding() }
        assertEquals(listOf(shared, beach, forest), library.added)
    }

    @Test
    fun `Cancel while several are added keeps those saved and adds no more`() {
        library.results[shared] = ImportResult.Added(holiday)
        library.answerLater = true
        val window = window(share(shared, beach, forest))
        library.finishAdding()

        window.get().entries().single().performClick()

        assertTrue(window.get().isFinishing)
        assertEquals(listOf(beach), library.cancelled)
        assertEquals(listOf(shared, beach), library.added)
        assertEquals(listOf(holiday), library.pictures)
    }

    @Test
    fun `made anew by a configuration change, the window goes on where it was, adding nothing twice`() {
        library.results[shared] = ImportResult.Added(holiday)
        library.answerLater = true
        val window = window(share(shared, beach))
        library.finishAdding()

        window.recreate()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Adding picture 2 of 2…", window.get().title())
        assertEquals(listOf(shared, beach), library.added)
        assertEquals(emptyList<Uri>(), library.cancelled)
    }

    @Test
    fun `closed by Android, as Home does while Luncher is the home app, it stops the pictures still to add`() {
        library.answerLater = true
        val window = window(share(shared, beach))

        window.pause().stop().destroy()

        assertEquals(listOf(shared), library.cancelled)
        assertEquals(0, application.graph.receivedPictures.session)
    }

    @Test
    fun `Home while it adds closes the window, keeping the pictures saved and adding no more`() {
        library.results[shared] = ImportResult.Added(holiday)
        library.answerLater = true
        val window = window(share(shared, beach, forest))
        library.finishAdding()

        window.userLeaving().pause().stop()

        assertTrue(window.get().isFinishing)
        assertEquals(listOf(beach), library.cancelled)
        assertEquals(listOf(holiday), library.pictures)
        assertEquals(0, application.graph.receivedPictures.session)
    }

    // Android tells the window the user leaves it as the share screen starts over it, though it shows nothing.
    @Test
    fun `a share that arrives over the window joins it, though Android says the user leaves it`() {
        library.answerLater = true
        val window = window(share(shared))

        window.userLeaving().pause().newIntent(share(beach)).resume()

        assertFalse(window.get().isFinishing)
        assertEquals("Adding picture 1 of 2…", window.get().title())
        assertEquals(emptyList<Uri>(), library.cancelled)
    }

    @Test
    fun `out of sight without the user leaving it, as when the TV sleeps, it goes on adding`() {
        library.answerLater = true
        val window = window(share(shared, beach))
        window.userLeaving().pause().resume()

        window.pause().stop()

        assertFalse(window.get().isFinishing)
        assertEquals(emptyList<Uri>(), library.cancelled)
        library.finishAdding()
        assertEquals(listOf(shared, beach), library.added)
    }

    private val tooMany = Intent().putExtra(PictureReceivedActivity.EXTRA_TOO_MANY, true)

    @Test
    fun `a share of too many pictures says Luncher adds up to a hundred at a time, adding none`() {
        val activity = receive(tooMany)

        assertEquals("The pictures weren't added", activity.title())
        assertEquals("Luncher adds up to 100 pictures at a time. Share fewer and try again.", activity.text())
        assertEquals(listOf("OK"), activity.entries().map { it.text.toString() })
        assertEquals(emptyList<Uri>(), library.added)
    }

    @Test
    fun `a share of too many pictures while others are added says so at the end`() {
        library.results[shared] = ImportResult.Added(holiday)
        library.answerLater = true
        val window = window(share(shared))

        window.newIntent(tooMany)
        assertEquals("Adding the picture…", window.get().title())
        library.finishAdding()

        assertEquals("1 picture saved", window.get().title())
        assertEquals("Luncher adds up to 100 pictures at a time. Share fewer and try again.", window.get().text())
    }

    // On API 29 Android closes Luncher's task when another app's opens; a share arriving then
    // comes to a new window, before the old one is destroyed.
    @Test
    fun `a new window that took the session over goes on when the old one is destroyed after it`() {
        library.results[shared] = ImportResult.Added(holiday)
        library.results[beach] = ImportResult.AlreadyThere(holiday)
        library.answerLater = true
        val old = window(share(shared))
        val new = window(share(beach))

        old.pause().stop().destroy()

        assertEquals(emptyList<Uri>(), library.cancelled)
        library.finishAdding()
        library.finishAdding()
        assertEquals("2 pictures saved", new.get().title())
    }

    @Test
    fun `opened in place of a window that's gone, it takes the session over, which the old one can't end`() {
        library.answerLater = true
        val old = window(share(shared))

        val new = window(share(beach).putExtra(PictureReceivedActivity.EXTRA_TAKE_OVER, true))
        old.pause().stop().destroy()

        assertEquals(emptyList<Uri>(), library.cancelled)
        assertEquals("Adding picture 1 of 2…", new.get().title())
    }
}
