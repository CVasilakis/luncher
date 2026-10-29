package com.luncher.launcher.settings

import android.content.Intent
import android.net.Uri
import android.os.Looper
import com.luncher.launcher.ui.Badge
import com.luncher.launcher.ui.R as UiR
import java.time.Duration
import com.luncher.launcher.ui.ShortestShowing
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.FakePictures.Companion.picture
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.PictureFormat
import com.luncher.domain.pictures.SizeLimit
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

    /** The wheel's half second passed: what the window says once the pictures are done shows then. */
    private fun waitOutTheWheel() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ShortestShowing.SHORTEST))

    private fun PictureReceivedActivity.title() = findViewById<TextView>(R.id.settings_received_title).text.toString()

    private fun PictureReceivedActivity.text() = findViewById<TextView>(R.id.settings_received_text).text.toString()

    private fun PictureReceivedActivity.spinning() = findViewById<View>(R.id.settings_received_spinner).visibility == View.VISIBLE

    private fun PictureReceivedActivity.entries(): List<TextView> =
        findViewById<LinearLayout>(R.id.settings_received_entries).let { list -> (0 until list.childCount).map { list.getChildAt(it) as TextView } }

    // As the settings panel: Luncher's short fade, not what the dialog theme leaves to Android.
    @Test
    fun `the window comes and goes with a short fade of Luncher's own`() {
        library.results[shared] = ImportResult.Added(holiday)

        assertEquals(UiR.style.Animation_Luncher_Panel, receive().window.attributes.windowAnimations)
    }

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
        assertEquals("the wheel shows half a second at least", "Adding the picture…", activity.title())
        waitOutTheWheel()
        assertEquals("Picture added", activity.title())
        assertTrue(!activity.spinning())
        assertEquals(listOf("OK"), activity.entries().map { it.text.toString() })
    }

    @Test
    fun `a picture refused for its name before any copying shows no wheel, and why at once`() {
        library.results[shared] = ImportResult.SameFile(holiday)
        library.refusedAtOnce += shared
        library.answerLater = true
        val activity = receive()

        assertFalse(activity.spinning())
        library.finishAdding()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Already in Luncher's pictures", activity.title())
        assertEquals("holiday.jpg", activity.text())
        assertEquals(listOf("OK"), activity.entries().map { it.text.toString() })
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
    fun `once saved, names the picture, a check beside the title, and OK focused`() {
        library.results[shared] = ImportResult.Added(holiday)

        val activity = receive()

        assertEquals("Picture added", activity.title())
        assertEquals("holiday.jpg", activity.text())
        assertEquals(listOf("OK"), activity.entries().map { it.text.toString() })
        assertTrue(activity.entries().first().isFocused)
        val mark = activity.findViewById<Badge>(R.id.settings_received_mark)
        assertEquals(View.VISIBLE, mark.visibility)
        assertEquals(Badge.Kind.DONE, mark.kind)
        assertEquals(listOf(holiday), library.pictures)
    }

    @Test
    fun `a picture the library has already says it's there, with its name and OK`() {
        library.results[shared] = ImportResult.AlreadyThere(holiday)

        val activity = receive()

        assertEquals("Already in Luncher's pictures", activity.title())
        assertEquals("holiday.jpg", activity.text())
        assertEquals(listOf("OK"), activity.entries().map { it.text.toString() })
    }

    // It's in Luncher's pictures now, where the background editor offers it: nothing else to choose here.
    @Test
    fun `one picture added says so, with its name, and OK closes the window`() {
        library.results[shared] = ImportResult.Added(holiday)
        val activity = receive()

        assertEquals("Picture added", activity.title())
        assertEquals("holiday.jpg", activity.text())
        assertEquals(listOf("OK"), activity.entries().map { it.text.toString() })
        activity.entries().single().performClick()
        assertTrue(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `one it couldn't add says why, and OK closes the window`() {
        library.results[shared] = ImportResult.UnsupportedFormat(PictureFormat.HEIF)

        val activity = receive()

        assertEquals("Picture not added", activity.title())
        assertEquals("This TV can't show this HEIF (HEIC) picture. Save it as JPEG or PNG, then add it again.", activity.text())
        assertEquals(listOf("OK"), activity.entries().map { it.text.toString() })
        activity.entries().single().performClick()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `one too large says which limit it's over`() {
        library.results[shared] = ImportResult.TooLarge(SizeLimit.BYTES)
        assertEquals("Too large: over 50 MB.", receive().text())

        library.results[shared] = ImportResult.TooLarge(SizeLimit.PIXELS)
        application.graph = TestPicturesGraph(library)
        assertEquals("Too large: over 64 megapixels.", receive().text())
    }

    @Test
    fun `with nothing it could read, says so, adding nothing`() {
        val activity = receive(Intent().putExtra(PictureReceivedActivity.EXTRA_NOTHING_READABLE, true))

        assertTrue(activity.text().startsWith("The file can't be read."))
        assertEquals(emptyList<Uri>(), library.added)
    }

    @Test
    fun `with nothing shared, says there's nothing to add, not to copy a file`() {
        val activity = receive(Intent().putExtra(PictureReceivedActivity.EXTRA_NOTHING_SHARED, true))

        assertEquals("Nothing to add", activity.title())
        assertEquals("The app shared no picture.", activity.text())
        assertEquals(listOf("OK"), activity.entries().map { it.text.toString() })
        assertTrue(activity.entries().single().isFocused)
        assertEquals(emptyList<Uri>(), library.added)
    }

    @Test
    fun `a share of folders only says there's nothing to add`() {
        library.results[shared] = ImportResult.Folder

        val activity = receive()
        waitOutTheWheel()

        assertEquals("Nothing to add", activity.title())
        assertEquals("The app shared no picture.", activity.text())
    }

    @Test
    fun `a folder shared with a picture isn't mentioned, the picture said as if alone`() {
        library.results[shared] = ImportResult.Folder
        library.results[beach] = ImportResult.Added(holiday)

        val activity = receive(share(shared, beach))
        waitOutTheWheel()

        assertEquals("Picture added", activity.title())
        assertEquals("holiday.jpg", activity.text())
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

        assertEquals("1 picture added", activity.title())
        assertEquals("1 already in Luncher's pictures\n1 not a picture, or damaged", activity.text())
        assertEquals(listOf("OK"), activity.entries().map { it.text.toString() })
        assertTrue(activity.entries().single().isFocused)
    }

    @Test
    fun `when none of several was added, says so, and why`() {
        val activity = receive(share(shared, beach))

        assertEquals("Pictures not added", activity.title())
        assertEquals("2 not pictures, or damaged", activity.text())
    }

    @Test
    fun `when all of several were added, says how many, with no more lines`() {
        library.results[shared] = ImportResult.Added(holiday)
        library.results[beach] = ImportResult.Added(picture("beach.jpg"))

        val activity = receive(share(shared, beach))

        assertEquals("2 pictures added", activity.title())
        assertEquals(View.GONE, activity.findViewById<View>(R.id.settings_received_text).visibility)
    }

    @Test
    fun `says how many weren't added for each reason, in its order, those the library had first`() {
        val pictures = (1..10).map { Uri.parse("content://com.example.files/$it.jpg") }
        listOf(
            ImportResult.NoSpace,
            ImportResult.Unreadable,
            ImportResult.NameTaken(holiday),
            ImportResult.TooLarge(SizeLimit.PIXELS),
            ImportResult.TooLarge(SizeLimit.BYTES),
            ImportResult.UnsupportedFormat(PictureFormat.AVIF),
            ImportResult.NotAPicture,
            ImportResult.SameFile(holiday),
            ImportResult.AlreadyThere(holiday),
            ImportResult.Added(holiday),
        ).forEachIndexed { i, result -> library.results[pictures[i]] = result }

        val activity = receive(share(*pictures.toTypedArray()))

        assertEquals("1 picture added", activity.title())
        assertEquals(
            listOf(
                "2 already in Luncher's pictures",
                "1 not a picture, or damaged",
                "1 in a format this TV can't show",
                "1 larger than 50 MB",
                "1 larger than 64 megapixels",
                "1 with a name already used in Luncher's pictures",
                "1 couldn't be read",
                "1 not added: not enough free storage",
            ),
            activity.text().lines(),
        )
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
    fun `Cancel once one of several is saved stops adding, keeps it, and says how it went`() {
        library.results[shared] = ImportResult.Added(holiday)
        library.answerLater = true
        val window = window(share(shared, beach, forest))
        library.finishAdding()

        window.get().entries().single().performClick()
        waitOutTheWheel()

        assertFalse(window.get().isFinishing)
        assertEquals(listOf(beach), library.cancelled)
        assertEquals(listOf(shared, beach), library.added)
        assertEquals(listOf(holiday), library.pictures)
        assertEquals("1 picture added", window.get().title())
        assertEquals("2 not added: cancelled", window.get().text())
        assertEquals(listOf("OK"), window.get().entries().map { it.text.toString() })
        window.get().entries().single().performClick()
        assertTrue(window.get().isFinishing)
    }

    @Test
    fun `Back once one of several is saved stops adding as Cancel does, and Back again closes the window`() {
        library.results[shared] = ImportResult.Added(holiday)
        library.answerLater = true
        val window = window(share(shared, beach, forest))
        library.finishAdding()

        @Suppress("DEPRECATION")
        window.get().onBackPressed()
        waitOutTheWheel()

        assertFalse(window.get().isFinishing)
        assertEquals(listOf(beach), library.cancelled)
        assertEquals("1 picture added", window.get().title())
        @Suppress("DEPRECATION")
        window.get().onBackPressed()
        assertTrue(window.get().isFinishing)
    }

    @Test
    fun `Cancel before any of several is saved closes the window, adding no more`() {
        library.answerLater = true
        val window = window(share(shared, beach, forest))
        library.finishAdding()

        window.get().entries().single().performClick()

        assertTrue(window.get().isFinishing)
        window.pause().stop().destroy()
        assertEquals(listOf(beach), library.cancelled)
        assertEquals(emptyList<Any>(), library.pictures)
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

        assertEquals("Pictures not added", activity.title())
        assertEquals("Too many pictures: up to 100 can be added at a time. Share fewer, then try again.", activity.text())
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

        waitOutTheWheel()
        assertEquals("1 picture added", window.get().title())
        assertEquals("Too many pictures: up to 100 can be added at a time. Share fewer, then try again.", window.get().text())
    }

    // On API 29 Android closes Luncher's task when another app's opens; a share arriving then
    // comes to a new window, before the old one is destroyed.
    @Test
    fun `a new window that took the session over goes on when the old one is destroyed after it`() {
        library.results[shared] = ImportResult.Added(holiday)
        library.results[beach] = ImportResult.Added(picture("beach.jpg"))
        library.answerLater = true
        val old = window(share(shared))
        val new = window(share(beach))

        old.pause().stop().destroy()

        assertEquals(emptyList<Uri>(), library.cancelled)
        library.finishAdding()
        library.finishAdding()
        waitOutTheWheel()
        assertEquals("2 pictures added", new.get().title())
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
