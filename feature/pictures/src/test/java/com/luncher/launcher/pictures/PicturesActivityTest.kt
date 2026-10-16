package com.luncher.launcher.pictures

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.FakePictures.Companion.picture
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.PictureFormat
import androidx.test.platform.app.InstrumentationRegistry
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class PicturesActivityTest {

    private val application = RuntimeEnvironment.getApplication() as PicturesTestApplication

    private val beach = picture("beach.png", added = 2, bytes = 3_100_000)
    private val sunset = picture("sunset.jpg", added = 3, bytes = 1_500_000)
    private val old = picture("old.jpg", added = 1, bytes = 500_000)

    private fun graph(vararg pictures: com.luncher.domain.pictures.Picture) =
        TestPicturesGraph(FakePictures(*pictures)).also { application.graph = it }

    // A remote's keys, not a touchscreen: lists select only out of touch mode, where Robolectric starts.
    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun start(intent: Intent? = null): ActivityController<PicturesActivity> {
        val controller = Robolectric.buildActivity(PicturesActivity::class.java, intent).setup()
        idle()
        return controller
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** The grid's tiles, row by row. */
    private fun Activity.tileViews(): List<View> {
        val grid = findViewById<ViewGroup>(R.id.pictures_grid)
        return (0 until grid.childCount).flatMap { r -> (grid.getChildAt(r) as ViewGroup).let { row -> (0 until row.childCount).map(row::getChildAt) } }
    }

    /** What a tile shows: its picture's name, or "+" for Add from folder. */
    private fun View.name() = findViewById<TextView>(R.id.pictures_name)?.text?.toString() ?: "+"

    /** The name of the tile that has the focus. Not currentFocus: under Robolectric the window never gets focus. */
    private fun Activity.focusedTile() = window.decorView.findFocus()?.name()

    private fun Activity.status() = findViewById<TextView>(R.id.pictures_status).text.toString()

    /** The grid's tiles' names, the Add from folder tile as "+". */
    private fun Activity.tiles(): List<String> = tileViews().map { it.name() }

    /** The dialog showing: its title, text and entries. */
    private fun dialog(): Triple<String, String, List<TextView>>? {
        val dialog = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing } ?: return null
        val entries = dialog.findViewById<LinearLayout>(R.id.pictures_dialog_entries)
        return Triple(
            dialog.findViewById<TextView>(R.id.pictures_dialog_title).text.toString(),
            dialog.findViewById<TextView>(R.id.pictures_dialog_text).text.toString(),
            (0 until entries.childCount).map { entries.getChildAt(it) as TextView },
        )
    }

    private fun choose(label: String) {
        dialog()!!.third.single { it.text == label }.performClick()
        idle()
    }

    private fun Activity.ok(position: Int) {
        tileViews()[position].performClick()
        idle()
    }

    @Test
    fun `shows the newest first, then the Add from folder tile, and what they take`() {
        graph(old, beach, sunset)

        val activity = start().get()

        assertEquals(listOf("sunset.jpg", "beach.png", "old.jpg", "+"), activity.tiles())
        assertEquals("3 pictures · 5.1 MB", activity.status())
        assertEquals("Delete unused (3 · 5.1 MB)", activity.findViewById<TextView>(R.id.pictures_delete_unused).text.toString())
    }

    @Test
    fun `while empty, says how to add a picture, the way to the folder focused`() {
        graph()

        val activity = start().get()

        assertEquals("No pictures yet", activity.status())
        assertEquals(emptyList<String>(), activity.tiles())
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.pictures_empty).visibility)
        assertEquals("Android/media/${activity.packageName}", activity.findViewById<TextView>(R.id.pictures_empty_folder).text.toString())
        assertTrue(activity.findViewById<View>(R.id.pictures_empty_add).isFocused)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.pictures_delete_unused).visibility)
    }

    @Test
    fun `shows the kept thumbnails at once, and draws the others in the background`() {
        val graph = graph(beach, sunset)
        graph.pictureImages.cache[beach.id] = sample("kept", 384, 216)

        start()
        assertEquals("only the one not kept is waiting", 1, graph.jobs.pending)
        assertEquals(emptyList<Any>(), graph.pictureImages.rendered)

        graph.jobs.runAll()
        idle()

        assertEquals(listOf(sunset), graph.pictureImages.rendered)
    }

    @Test
    fun `draws thumbnails at the tile's size in pixels`() {
        val graph = graph(beach)
        var size: Pair<Int, Int>? = null
        graph.pictureImages.cache.clear()
        val sized = object : TestPicturesGraph(graph.library) {
            override val pictureImages = com.luncher.domain.pictures.FakePictureImages { picture, width, height, _ ->
                size = width to height
                sample(picture.name, width, height)
            }
        }
        application.graph = sized

        start()
        sized.jobs.runAll()

        assertEquals(384 to 216, size)   // 192 x 108 dp at 2 px a dp
    }

    @Test
    fun `stops drawing thumbnails when it's hidden`() {
        val graph = graph(beach, sunset)
        val controller = start()

        controller.pause().stop()

        assertEquals(0, graph.jobs.pending)
    }

    @Test
    fun `follows the library while it shows, and only then`() {
        val graph = graph(beach)
        val controller = start()
        val activity = controller.get()

        graph.library.pictures += sunset
        graph.library.delete(emptySet())   // tells the listeners
        idle()
        assertEquals(listOf("sunset.jpg", "beach.png", "+"), activity.tiles())

        controller.pause().stop()
        assertEquals(0, graph.library.listenerCount)
    }

    @Test
    fun `OK on a picture shows its actions, and deleting it asks first, Cancel focused`() {
        val graph = graph(beach, sunset)
        val activity = start().get()

        activity.ok(1)
        val (title, text, entries) = dialog()!!
        assertEquals("beach.png", title)
        assertEquals("1920 × 1080 · 3.1 MB", text)
        assertEquals(listOf("Delete"), entries.map { it.text.toString() })

        choose("Delete")
        val (question, _, answers) = dialog()!!
        assertEquals("Delete beach.png?", question)
        assertEquals(listOf("Cancel", "Delete"), answers.map { it.text.toString() })
        assertTrue(answers[0].isFocused)

        choose("Delete")
        assertEquals(listOf(sunset), graph.library.pictures)
        assertEquals(listOf("sunset.jpg", "+"), activity.tiles())
    }

    @Test
    fun `Cancel deletes nothing`() {
        val graph = graph(beach)
        val activity = start().get()

        activity.ok(0)
        choose("Delete")
        choose("Cancel")

        assertEquals(listOf(beach), graph.library.pictures)
        assertNull(dialog())
    }

    @Test
    fun `Delete unused asks, then deletes every picture nothing uses`() {
        val graph = graph(beach, sunset)
        val activity = start().get()

        activity.findViewById<View>(R.id.pictures_delete_unused).performClick()
        idle()
        val (question, text, _) = dialog()!!
        assertEquals("Delete 2 unused pictures?", question)
        assertEquals("They take 4.6 MB. Pictures in use stay.", text)

        choose("Delete")

        assertEquals(emptyList<Any>(), graph.library.pictures)
        assertEquals("No pictures yet", activity.status())
        assertTrue(activity.findViewById<View>(R.id.pictures_empty_add).isFocused)
    }

    @Test
    fun `the last tile opens Add from folder, and adds the picture it returns`() {
        val folderPicture = Uri.parse("file:///storage/emulated/0/Android/media/x/sunset.jpg")
        val graph = graph(beach)
        graph.library.results[folderPicture] = ImportResult.Added(sunset)
        val activity = start().get()

        activity.ok(1)
        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals(AddFromFolderActivity::class.java.name, started.intent.component?.className)
        shadowOf(activity).receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(folderPicture))
        idle()

        assertEquals(listOf(folderPicture), graph.library.added)
        assertEquals("sunset.jpg", activity.focusedTile())
    }

    @Test
    fun `adds a shared picture, saying so meanwhile, then selects it`() {
        val shared = Uri.parse("content://com.example.files/sunset.jpg")
        val graph = graph(beach)
        graph.library.results[shared] = ImportResult.Added(sunset)
        graph.library.answerLater = true

        val activity = start(Intent(application, PicturesActivity::class.java).setData(shared)).get()
        assertEquals("Adding a picture…", activity.status())

        graph.library.finishAdding()
        idle()

        assertEquals("2 pictures · 4.6 MB", activity.status())
        assertEquals("sunset.jpg", activity.focusedTile())
        assertNull(dialog())
    }

    @Test
    fun `a picture it has already is selected, and nothing said`() {
        val shared = Uri.parse("content://com.example.files/copy.jpg")
        val graph = graph(beach, sunset, old)
        graph.library.results[shared] = ImportResult.AlreadyThere(old)

        val activity = start(Intent(application, PicturesActivity::class.java).setData(shared)).get()

        assertEquals("old.jpg", activity.focusedTile())
        assertNull(dialog())
    }

    @Test
    fun `says why a picture wasn't added, for each reason`() {
        val reasons = mapOf(
            ImportResult.NotAPicture to "It isn't a picture, or it's damaged.",
            ImportResult.UnsupportedFormat(PictureFormat.HEIF) to "This TV can't show HEIF (HEIC) pictures. Save it as a JPEG or PNG, and add that instead.",
            ImportResult.UnsupportedFormat(PictureFormat.AVIF) to "This TV can't show AVIF pictures. Save it as a JPEG or PNG, and add that instead.",
            ImportResult.TooLarge to "It's too large: a picture can have up to 50 MB and 64 megapixels.",
            ImportResult.Unreadable to "Luncher can't read it. Copy it into the folder Android/media/${application.packageName} on this TV or a USB drive, then choose Add from folder.",
            ImportResult.NoSpace to "There isn't enough free storage on this TV for it.",
        )
        for ((result, why) in reasons) {
            val shared = Uri.parse("content://com.example.files/$result")
            graph().library.results[shared] = result

            start(Intent(application, PicturesActivity::class.java).setData(shared))

            val (title, text, entries) = dialog()!!
            assertEquals("This picture wasn't added", title)
            assertEquals(why, text)
            assertEquals(listOf("OK"), entries.map { it.text.toString() })
            choose("OK")
        }
    }

    @Test
    fun `says it couldn't read what another app sent without a picture`() {
        graph()

        start(Intent(application, PicturesActivity::class.java).putExtra(PicturesActivity.EXTRA_NOTHING_READABLE, true))

        assertNotNull(dialog())
        assertTrue(dialog()!!.second.startsWith("Luncher can't read it."))
    }

    @Test
    fun `doesn't add the shared picture again when recreated`() {
        val shared = Uri.parse("content://com.example.files/sunset.jpg")
        val graph = graph()
        graph.library.results[shared] = ImportResult.Added(sunset)
        val controller = Robolectric.buildActivity(PicturesActivity::class.java, Intent(application, PicturesActivity::class.java).setData(shared))

        controller.setup(Bundle())

        assertEquals(emptyList<Uri>(), graph.library.added)
    }

    @Test
    fun `arrows move the focus along the grid, the newest first, then down to Delete unused`() {
        graph(beach, sunset)
        val activity = start().get()
        assertEquals("sunset.jpg", activity.focusedTile())

        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("beach.png", activity.focusedTile())
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("+", activity.focusedTile())
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("the row's end", "+", activity.focusedTile())
        activity.press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals(R.id.pictures_delete_unused, activity.window.decorView.findFocus()?.id)
    }

    @Test
    fun `a row holds as many tiles as fit, then the next row starts`() {
        graph(*(1..5).map { picture("p$it.jpg", added = it.toLong()) }.toTypedArray())
        val activity = start().get()
        val grid = activity.findViewById<ViewGroup>(R.id.pictures_grid)

        // 864 dp between the margins: four 192 dp tiles and their 24 dp gaps.
        assertEquals(listOf(4, 2), (0 until grid.childCount).map { (grid.getChildAt(it) as ViewGroup).childCount })
        activity.press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("p1.jpg", activity.focusedTile())
    }

    @Test
    fun `after deleting the focused picture, the one that took its place has the focus`() {
        val graph = graph(old, beach, sunset)
        val activity = start().get()
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)

        graph.library.delete(setOf(beach.id))
        idle()

        assertEquals("old.jpg", activity.focusedTile())
    }

    /**
     * An arrow, as Android moves the focus for it: to the view its focus search finds. Robolectric's
     * window never gets the focus, so a key event alone moves nothing; the real keys are the
     * instrumented tests'.
     */
    private fun Activity.press(keyCode: Int) {
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            else -> View.FOCUS_DOWN
        }
        window.decorView.findFocus()?.focusSearch(direction)?.requestFocus(direction)
        idle()
    }
}
