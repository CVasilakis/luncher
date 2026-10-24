package com.luncher.launcher.settings

import android.app.Activity
import android.os.Looper
import android.view.FocusFinder
import android.view.KeyEvent
import java.time.Duration
import com.luncher.domain.pictures.ImportResult
import com.luncher.launcher.ui.WorkingDialog
import com.luncher.launcher.ui.R as UiR
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.luncher.domain.pictures.FakeFolderPictures
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.PictureFormat
import androidx.test.platform.app.InstrumentationRegistry
import com.luncher.launcher.testing.TV_1080P
import com.luncher.domain.appearance.ThemeAppearance
import com.luncher.domain.appearance.colorRoles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class AddFromFolderActivityTest {

    private val application = RuntimeEnvironment.getApplication() as SettingsTestApplication

    private val holiday = folderPicture("holiday.jpg", removable = true)
    private val logo = folderPicture("logo.png", removable = true)
    private val wallpaper = folderPicture("wallpaper-4k.jpg")
    private val phone = folderPicture("IMG_0001.HEIC", unsupported = PictureFormat.HEIF)

    private fun graph(vararg pictures: com.luncher.domain.pictures.FolderPicture<android.net.Uri>, library: FakePictures<android.net.Uri> = FakePictures()): TestPicturesGraph {
        val graph = object : TestPicturesGraph(library) {
            override val folderPictures = FakeFolderPictures(*pictures, draw = { picture, width, height -> sample(picture.name, width, height) })
        }
        application.graph = graph
        return graph
    }

    // A remote's keys, not a touchscreen: lists select only out of touch mode, where Robolectric starts.
    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun start(): AddFromFolderActivity {
        val activity = Robolectric.buildActivity(AddFromFolderActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    private fun Activity.list() = findViewById<ListView>(R.id.settings_pictures_folder_list)

    private fun Activity.rows(): List<String> {
        val list = list()
        return (0 until list.adapter.count).map { position ->
            val row = list.adapter.getView(position, null, list)
            row.findViewById<TextView>(R.id.settings_pictures_folder_name).text.toString() + " | " +
                row.findViewById<TextView>(R.id.settings_pictures_folder_details).text
        }
    }

    @Test
    fun `lists the TV's pictures and the drives' together, by name, with their size`() {
        graph(logo, holiday, wallpaper, phone)

        val activity = start()

        assertEquals(
            listOf(
                "holiday.jpg | USB drive · 4000 × 3000 · 2.4 MB",
                "IMG_0001.HEIC | Local · HEIF (HEIC) not supported on this TV",
                "logo.png | USB drive · 4000 × 3000 · 2.4 MB",
                "wallpaper-4k.jpg | Local · 4000 × 3000 · 2.4 MB",
            ),
            activity.rows(),
        )
        assertEquals("Pictures on this TV or a USB drive, in the folder Android/media/${activity.packageName}", activity.findViewById<TextView>(R.id.settings_pictures_folder_where).text.toString())
    }

    @Test
    fun `says when the folders have no pictures`() {
        graph()

        val activity = start()

        val state = activity.findViewById<TextView>(R.id.settings_pictures_folder_state)
        assertEquals(View.VISIBLE, state.visibility)
        assertEquals("No pictures in the folder yet", state.text.toString())
    }

    @Test
    fun `previews the selected picture in the background, the latest one only`() {
        val graph = graph(wallpaper, holiday, logo)
        val activity = start()
        val list = activity.list()

        graph.jobs.runAll()   // the first one's, selected at first
        shadowOf(Looper.getMainLooper()).idle()
        graph.folderPictures.previewed.clear()

        list.setSelection(1)
        shadowOf(Looper.getMainLooper()).idle()
        list.setSelection(2)
        shadowOf(Looper.getMainLooper()).idle()
        graph.jobs.runAll()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("wallpaper-4k.jpg"), graph.folderPictures.previewed.map { it.name })
        assertNotNull(activity.findViewById<ImageView>(R.id.settings_pictures_folder_preview).drawable)
    }

    /** The window over the screen while a picture is added, if it shows. */
    private fun working(): android.app.Dialog? = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing && it.findViewById<View>(UiR.id.working_message) != null }

    @Test
    fun `OK on a picture adds it while a window says so, then returns it to the background editor`() {
        val library = FakePictures<android.net.Uri>().apply {
            answerLater = true
            results[holiday.source] = ImportResult.Added(FakePictures.picture("holiday.jpg"))
        }
        graph(wallpaper, holiday, library = library)
        val activity = start()

        activity.list().performItemClick(null, 0, 0)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(holiday.source), library.added)
        val window = checkNotNull(working())
        assertEquals("Adding the picture…", window.findViewById<TextView>(UiR.id.working_message).text.toString())
        assertEquals("Cancel", window.findViewById<TextView>(UiR.id.working_cancel).text.toString())
        assertFalse(activity.isFinishing)

        library.finishAdding()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WorkingDialog.SHORTEST))

        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals("id-holiday.jpg", shadowOf(activity).resultIntent.getStringExtra(AddFromFolderActivity.EXTRA_ADDED))
    }

    @Test
    fun `a picture the library had already, by its content, is returned as added`() {
        val known = FakePictures.picture("old name.jpg")
        graph(holiday, library = FakePictures<android.net.Uri>(known).apply { results[holiday.source] = ImportResult.AlreadyThere(known) })
        val activity = start()

        activity.list().performItemClick(null, 0, 0)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WorkingDialog.SHORTEST))

        assertEquals(known.id.value, shadowOf(activity).resultIntent.getStringExtra(AddFromFolderActivity.EXTRA_ADDED))
    }

    @Test
    fun `a picture adding refuses is said in the folder screen, which stays for another choice`() {
        graph(holiday, library = FakePictures<android.net.Uri>().apply { results[holiday.source] = ImportResult.NotAPicture })
        val activity = start()

        activity.list().performItemClick(null, 0, 0)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WorkingDialog.SHORTEST))

        assertFalse(activity.isFinishing)
        assertEquals("Picture not added", ShadowDialog.getLatestDialog().findViewById<TextView>(UiR.id.dialog_title).text.toString())
        assertEquals("The file isn't a picture, or it's damaged.", ShadowDialog.getLatestDialog().findViewById<TextView>(UiR.id.dialog_text).text.toString())
    }

    @Test
    fun `Cancel, or Back, while a picture is added stops it, closes the window at once, and stays`() {
        for (key in listOf("Cancel", "Back")) {
            val library = FakePictures<android.net.Uri>().apply {
                answerLater = true
                results[holiday.source] = ImportResult.Added(FakePictures.picture("holiday.jpg"))
            }
            graph(holiday, library = library)
            val activity = start()
            activity.list().performItemClick(null, 0, 0)
            shadowOf(Looper.getMainLooper()).idle()
            val window = checkNotNull(working())

            if (key == "Cancel") {
                window.findViewById<TextView>(UiR.id.working_cancel).performClick()
            } else {
                window.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
                window.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
            }
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(key, listOf(holiday.source), library.cancelled)
            assertFalse(key, window.isShowing)
            assertFalse(key, activity.isFinishing)
            assertEquals(key, emptyList<Any>(), library.pictures)
        }
    }

    @Test
    fun `closed while a picture is added, the adding stops`() {
        val library = FakePictures<android.net.Uri>().apply {
            answerLater = true
            results[holiday.source] = ImportResult.Added(FakePictures.picture("holiday.jpg"))
        }
        graph(holiday, library = library)
        val controller = Robolectric.buildActivity(AddFromFolderActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        controller.get().list().performItemClick(null, 0, 0)

        controller.pause().stop().destroy()

        assertEquals(listOf(holiday.source), library.cancelled)
    }

    @Test
    fun `OK stops the preview being drawn, so the adding doesn't wait for it`() {
        val graph = graph(holiday, library = FakePictures<android.net.Uri>().apply { answerLater = true })
        val activity = start()
        graph.jobs.runAll()
        val preview = graph.folderPictures.previewStops.single()

        activity.list().performItemClick(null, 0, 0)

        assertTrue(preview())
    }

    @Test
    fun `OK on a picture this TV can't show says so, and stays`() {
        graph(phone, wallpaper)
        val activity = start()

        activity.list().performItemClick(null, 0, 0)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(activity.isFinishing)
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue(dialog.isShowing)
        assertEquals(
            "This TV can't show this HEIF (HEIC) picture. Save it as JPEG or PNG, then add it again.",
            dialog.findViewById<TextView>(UiR.id.dialog_text).text.toString(),
        )
        assertEquals(1, dialog.findViewById<LinearLayout>(UiR.id.dialog_entries).childCount)
    }

    @Test
    fun `draws no preview of a picture this TV can't show, a warning mark in its place`() {
        val graph = graph(phone)
        val activity = start()
        graph.jobs.runAll()

        assertEquals(emptyList<Any>(), graph.folderPictures.previewed)
        assertNull(activity.findViewById<ImageView>(R.id.settings_pictures_folder_preview).drawable)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.settings_pictures_folder_no_preview).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.settings_pictures_folder_loading).visibility)
    }

    @Test
    fun `a picture whose preview can't be drawn, a damaged one, shows the warning mark once it's tried`() {
        val damaged = folderPicture("damaged.jpg")
        val graph = object : TestPicturesGraph(FakePictures()) {
            override val folderPictures = FakeFolderPictures(damaged, this@AddFromFolderActivityTest.wallpaper, draw = { picture, width, height -> if (picture == damaged) null else sample(picture.name, width, height) })
        }
        application.graph = graph
        val activity = start()
        val mark = activity.findViewById<View>(R.id.settings_pictures_folder_no_preview)
        assertEquals("not before it's tried: the wheel shows", View.GONE, mark.visibility)

        graph.jobs.runAll()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, mark.visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.settings_pictures_folder_loading).visibility)
        activity.list().setSelection(1)
        shadowOf(Looper.getMainLooper()).idle()
        graph.jobs.runAll()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("a picture that can be drawn hides it", View.GONE, mark.visibility)
        assertNotNull(activity.findViewById<ImageView>(R.id.settings_pictures_folder_preview).drawable)
    }

    /** The graph of [pictures], whose previews are drawn but of [damaged], as a cut or damaged file's aren't. */
    private fun graphWithDamaged(damaged: com.luncher.domain.pictures.FolderPicture<android.net.Uri>, vararg pictures: com.luncher.domain.pictures.FolderPicture<android.net.Uri>, library: FakePictures<android.net.Uri> = FakePictures()): TestPicturesGraph {
        val graph = object : TestPicturesGraph(library) {
            override val folderPictures = FakeFolderPictures(*pictures, draw = { picture, width, height -> if (picture == damaged) null else sample(picture.name, width, height) })
        }
        application.graph = graph
        return graph
    }

    @Test
    fun `a picture whose preview can't be drawn is marked damaged, and OK on it says why at once`() {
        val damaged = folderPicture("damaged.jpg")
        val library = FakePictures<android.net.Uri>()
        val graph = graphWithDamaged(damaged, damaged, wallpaper, library = library)
        val activity = start()
        assertEquals("not before it's tried", "damaged.jpg | Local · 4000 × 3000 · 2.4 MB", activity.rows()[0])

        graph.jobs.runAll()
        idle()

        assertEquals(listOf("damaged.jpg | Local · 4000 × 3000 · 2.4 MB · Damaged", "wallpaper-4k.jpg | Local · 4000 × 3000 · 2.4 MB"), activity.rows())
        activity.list().performItemClick(null, 0, 0)
        idle()
        val (title, text) = message()
        assertEquals("Picture not added" to "The file isn't a picture, or it's damaged.", title to text)
        assertEquals(emptyList<Any>(), library.added)
        assertNull(working())
    }

    @Test
    fun `a picture drawn ahead and found damaged is marked, shows the warning mark at once, and Add all passes it by`() {
        val damaged = folderPicture("zoo.jpg")
        val library = FakePictures<android.net.Uri>().apply { results[wallpaper.source] = ImportResult.Added(FakePictures.picture("wallpaper-4k.jpg")) }
        val graph = graphWithDamaged(damaged, wallpaper, damaged, library = library)
        val activity = start()

        graph.next()   // the selected one's
        graph.next()   // the other's, ahead
        assertEquals(listOf("wallpaper-4k.jpg", "zoo.jpg"), graph.folderPictures.previewed.map { it.name })
        assertEquals("zoo.jpg | Local · 4000 × 3000 · 2.4 MB · Damaged", activity.rows()[1])
        graph.folderPictures.previewed.clear()
        activity.list().setSelection(1)
        idle()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.settings_pictures_folder_no_preview).visibility)
        graph.jobs.runAll()
        assertEquals("not drawn again", emptyList<Any>(), graph.folderPictures.previewed)

        activity.addAll().performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WorkingDialog.SHORTEST))

        assertEquals(listOf(wallpaper.source), library.added)
        assertEquals("1 picture added" to "1 not a picture, or damaged", message().let { it.first to it.second })
    }

    @Test
    fun `a picture adding refuses as not a picture is marked damaged`() {
        graph(holiday, library = FakePictures<android.net.Uri>().apply { results[holiday.source] = ImportResult.NotAPicture })
        val activity = start()

        activity.list().performItemClick(null, 0, 0)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WorkingDialog.SHORTEST))

        assertEquals(listOf("holiday.jpg | USB drive · 4000 × 3000 · 2.4 MB · Damaged"), activity.rows())
        assertFalse("nothing left to add", activity.addAll().isFocusable)
    }

    @Test
    fun `a picture refused for want of storage says so, its row left unmarked for another try`() {
        graph(holiday, library = FakePictures<android.net.Uri>().apply { results[holiday.source] = ImportResult.NoSpace })
        val activity = start()

        activity.list().performItemClick(null, 0, 0)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WorkingDialog.SHORTEST))

        assertEquals("Picture not added", ShadowDialog.getLatestDialog().findViewById<TextView>(UiR.id.dialog_title).text.toString())
        assertEquals("Not enough free storage on this TV. Free up space, then try again.", ShadowDialog.getLatestDialog().findViewById<TextView>(UiR.id.dialog_text).text.toString())
        assertEquals(listOf("holiday.jpg | USB drive · 4000 × 3000 · 2.4 MB"), activity.rows())
        assertTrue(activity.addAll().isFocusable)
    }

    @Test
    fun `Add all goes on after the storage fills, saying how many it couldn't store`() {
        graph(holiday, logo, wallpaper, library = FakePictures<android.net.Uri>().apply {
            results[holiday.source] = ImportResult.Added(FakePictures.picture("holiday.jpg"))
            results[logo.source] = ImportResult.NoSpace
            results[wallpaper.source] = ImportResult.NoSpace
        })
        val activity = start()

        activity.addAll().performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WorkingDialog.SHORTEST))

        assertEquals("1 picture added" to "2 not added: not enough free storage", message().let { it.first to it.second })
    }

    @Test
    fun `stops previewing when it's hidden`() {
        val graph = graph(wallpaper)
        val controller = Robolectric.buildActivity(AddFromFolderActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        controller.pause().stop()

        assertEquals(0, graph.jobs.pending)
    }

    private fun Activity.addAll() = findViewById<TextView>(R.id.settings_pictures_folder_add_all)

    /** The message showing: its title, text and entries. */
    private fun message(): Triple<String, String, List<TextView>> {
        val dialog = ShadowDialog.getLatestDialog().also { assertTrue(it.isShowing) }
        val entries = dialog.findViewById<LinearLayout>(UiR.id.dialog_entries)
        return Triple(
            dialog.findViewById<TextView>(UiR.id.dialog_title).text.toString(),
            dialog.findViewById<TextView>(UiR.id.dialog_text).text.toString(),
            (0 until entries.childCount).map { entries.getChildAt(it) as TextView },
        )
    }

    @Test
    fun `Add all adds the pictures not marked, one at a time, saying which of how many, then how it went`() {
        val unchanged = folderPicture("unchanged.jpg", bytes = 2_400_000)
        val library = FakePictures<android.net.Uri>(FakePictures.picture("unchanged.jpg", bytes = 2_400_000)).apply {
            answerLater = true
            results[holiday.source] = ImportResult.Added(FakePictures.picture("holiday.jpg"))
            results[logo.source] = ImportResult.NotAPicture
            results[wallpaper.source] = ImportResult.Added(FakePictures.picture("wallpaper-4k.jpg"))
        }
        graph(holiday, logo, wallpaper, unchanged, phone, library = library)
        val activity = start()
        assertEquals("Add all", activity.addAll().text.toString())

        activity.addAll().performClick()
        idle()

        val window = checkNotNull(working())
        val message = window.findViewById<TextView>(UiR.id.working_message)
        assertEquals("Adding picture 1 of 3…", message.text.toString())
        assertEquals("Cancel", window.findViewById<TextView>(UiR.id.working_cancel).text.toString())
        library.finishAdding()
        assertEquals("Adding picture 2 of 3…", message.text.toString())
        library.finishAdding()
        library.finishAdding()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WorkingDialog.SHORTEST))

        assertEquals("the list's order, not the marked ones", listOf(holiday.source, logo.source, wallpaper.source), library.added)
        val (title, text, entries) = message()
        assertEquals("2 pictures added", title)
        assertEquals("1 already in Luncher's pictures\n1 not a picture, or damaged\n1 in a format this TV can't show", text)
        assertEquals(listOf("OK"), entries.map { it.text.toString() })
        assertFalse(activity.isFinishing)

        entries.single().performClick()

        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals(
            listOf("id-holiday.jpg", "id-wallpaper-4k.jpg"),
            shadowOf(activity).resultIntent.getStringArrayExtra(AddFromFolderActivity.EXTRA_ADDED_ALL)!!.toList(),
        )
    }

    @Test
    fun `Back on Add all's message returns the pictures added too`() {
        graph(holiday, library = FakePictures<android.net.Uri>().apply { results[holiday.source] = ImportResult.Added(FakePictures.picture("holiday.jpg")) })
        val activity = start()
        activity.addAll().performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WorkingDialog.SHORTEST))
        assertEquals("1 picture added", message().first)

        ShadowDialog.getLatestDialog().apply {
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        idle()

        assertTrue(activity.isFinishing)
        assertEquals(listOf("id-holiday.jpg"), shadowOf(activity).resultIntent.getStringArrayExtra(AddFromFolderActivity.EXTRA_ADDED_ALL)!!.toList())
    }

    @Test
    fun `Add all with none added says why, and stays`() {
        graph(holiday, logo, library = FakePictures())
        val activity = start()

        activity.addAll().performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WorkingDialog.SHORTEST))
        val (title, text, entries) = message()
        assertEquals("Pictures not added", title)
        assertEquals("2 not pictures, or damaged", text)
        entries.single().performClick()

        assertFalse(activity.isFinishing)
    }

    @Test
    fun `Cancel during Add all, before any was added, closes the window and stays`() {
        val library = FakePictures<android.net.Uri>().apply {
            answerLater = true
            results[holiday.source] = ImportResult.Added(FakePictures.picture("holiday.jpg"))
        }
        graph(holiday, logo, library = library)
        val activity = start()
        activity.addAll().performClick()
        idle()
        val window = checkNotNull(working())

        window.findViewById<TextView>(UiR.id.working_cancel).performClick()
        idle()

        assertFalse(window.isShowing)
        assertEquals(listOf(holiday.source), library.cancelled)
        assertNull(working())
        assertFalse(activity.isFinishing)
        assertEquals(emptyList<Any>(), library.pictures)
    }

    @Test
    fun `Cancel during Add all, after one was added, says how it went, those cancelled counted`() {
        val library = FakePictures<android.net.Uri>().apply {
            answerLater = true
            results[holiday.source] = ImportResult.Added(FakePictures.picture("holiday.jpg"))
        }
        graph(holiday, logo, wallpaper, library = library)
        val activity = start()
        activity.addAll().performClick()
        idle()
        library.finishAdding()   // holiday.jpg: added
        library.finishAdding()   // logo.png: not a picture

        checkNotNull(working()).findViewById<TextView>(UiR.id.working_cancel).performClick()
        idle()

        val (title, text) = message()
        assertEquals("1 picture added", title)
        assertEquals("1 not a picture, or damaged\n1 not added: cancelled", text)
        assertEquals(listOf(wallpaper.source), library.cancelled)
    }

    @Test
    fun `closed during Add all, the adding stops`() {
        val library = FakePictures<android.net.Uri>().apply { answerLater = true }
        graph(holiday, logo, library = library)
        val controller = Robolectric.buildActivity(AddFromFolderActivity::class.java).setup()
        idle()
        controller.get().addAll().performClick()

        controller.pause().stop().destroy()

        assertEquals(listOf(holiday.source), library.cancelled)
        assertEquals("nothing after it", listOf(holiday.source), library.added)
    }

    @Test
    fun `Add all is dimmed and passed by while no picture can be added`() {
        graph(phone, folderPicture("holiday.jpg", bytes = 2_400_000), library = known)

        val activity = start()

        assertFalse(activity.addAll().isFocusable)
        assertTrue(activity.addAll().alpha < 1f)
    }

    @Test
    fun `Down past the last picture stays there, not on Add all, which Right still reaches`() {
        graph(wallpaper, holiday, logo)
        val activity = start()
        val list = activity.list()
        list.requestFocus()
        list.setSelection(list.adapter.count - 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertSame("Android alone goes to Add all", activity.addAll(), FocusFinder.getInstance().findNextFocus(activity.window.decorView as ViewGroup, list, View.FOCUS_DOWN))
        assertSame(list, list.focusSearch(View.FOCUS_DOWN))
        assertSame(activity.addAll(), list.focusSearch(View.FOCUS_RIGHT))
    }

    @Test
    fun `Add all stops the preview being drawn, so the adding doesn't wait for it`() {
        val graph = graph(holiday, library = FakePictures<android.net.Uri>().apply { answerLater = true })
        val activity = start()
        graph.jobs.runAll()
        val preview = graph.folderPictures.previewStops.single()

        activity.addAll().performClick()

        assertTrue(preview())
    }

    // The library has "holiday.jpg" (2.4 MB, as the folder's) and "logo.png" (another size).
    private val known = FakePictures<android.net.Uri>(
        FakePictures.picture("holiday.jpg", bytes = 2_400_000),
        FakePictures.picture("logo.png", bytes = 99_000),
    )
    private val huge = folderPicture("aerial.jpg", bytes = 184_000_000, width = 21621, height = 7004)

    @Test
    fun `marks the pictures it can tell can't be added, too large or named like one of the library`() {
        graph(holiday, logo, huge, wallpaper, library = known)

        val activity = start()

        assertEquals(
            listOf(
                "aerial.jpg | Local · 21621 × 7004 · 184 MB · Too large (over 50 MB)",
                "holiday.jpg | USB drive · 4000 × 3000 · 2.4 MB · Already in Luncher's pictures",
                "logo.png | USB drive · 4000 × 3000 · 2.4 MB · Name already used in Luncher's pictures",
                "wallpaper-4k.jpg | Local · 4000 × 3000 · 2.4 MB",
            ),
            activity.rows(),
        )
    }

    @Test
    fun `a picture over the pixels alone is said to be, its file small`() {
        graph(folderPicture("wide.jpg", bytes = 458_000, width = 9000, height = 8000))

        assertEquals(listOf("wide.jpg | Local · 9000 × 8000 · 458 kB · Too large (over 64 megapixels)"), start().rows())
    }

    @Test
    fun `why a picture can't be added is in the error color, its size not`() {
        graph(huge, phone, wallpaper)
        val activity = start()
        val accent = colorRoles(ThemeAppearance()).error
        val list = activity.list()

        fun colored(position: Int): List<String> {
            val text = list.adapter.getView(position, null, list).findViewById<TextView>(R.id.settings_pictures_folder_details).text
            val spanned = text as? android.text.Spanned ?: return emptyList()
            return spanned.getSpans(0, text.length, android.text.style.ForegroundColorSpan::class.java)
                .filter { it.foregroundColor == accent }
                .map { text.subSequence(spanned.getSpanStart(it), spanned.getSpanEnd(it)).toString() }
        }

        assertEquals(listOf("Too large (over 50 MB)"), colored(0))
        assertEquals(listOf("HEIF (HEIC) not supported on this TV"), colored(1))
        assertEquals(emptyList<String>(), colored(2))
    }

    @Test
    fun `OK on a picture it can tell can't be added says why at once, reading nothing, and stays`() {
        val graph = graph(holiday, logo, huge, library = known)
        val activity = start()
        val messages = listOf(
            0 to ("Picture not added" to "Too large: over 50 MB."),
            1 to ("Already in Luncher's pictures" to "holiday.jpg"),
            2 to ("Picture not added" to "Another picture named logo.png is already in Luncher's pictures. Rename or delete it in Luncher, or rename this file, then add it again."),
        )

        for ((position, message) in messages) {
            activity.list().performItemClick(null, position, position.toLong())
            shadowOf(Looper.getMainLooper()).idle()

            assertFalse(activity.isFinishing)
            val dialog = ShadowDialog.getLatestDialog()
            assertEquals(message, dialog.findViewById<TextView>(UiR.id.dialog_title).text.toString() to dialog.findViewById<TextView>(UiR.id.dialog_text).text.toString())
        }
        assertEquals(emptyList<Any>(), known.added)
        graph.jobs.runAll()
        assertEquals(emptyList<Any>(), graph.folderPictures.previewed)   // the selected one, aerial.jpg, has none
    }

    @Test
    fun `draws no preview of a picture too large to add, a warning mark in its place`() {
        val graph = graph(huge)
        val activity = start()
        graph.jobs.runAll()

        assertEquals(emptyList<Any>(), graph.folderPictures.previewed)
        assertNull(activity.findViewById<ImageView>(R.id.settings_pictures_folder_preview).drawable)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.settings_pictures_folder_no_preview).visibility)
    }

    @Test
    fun `tells a preview being drawn to stop when another picture is selected, or the screen hides`() {
        val graph = graph(wallpaper, holiday)
        val controller = Robolectric.buildActivity(AddFromFolderActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        graph.jobs.runAll()   // the first picture's preview, what it's told kept for later
        val first = graph.folderPictures.previewStops.single()
        assertFalse(first())

        controller.get().list().setSelection(1)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(first())

        graph.jobs.runAll()
        val second = graph.folderPictures.previewStops.last()
        assertFalse(second())
        controller.pause().stop()
        assertTrue(second())
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** The job thread runs the next job, and its result shows. */
    private fun TestPicturesGraph.next() {
        jobs.runNext()
        idle()
    }

    private val first = folderPicture("1.jpg")
    private val second = folderPicture("2.jpg")
    private val third = folderPicture("3.jpg")
    private val fourth = folderPicture("4.jpg")

    @Test
    fun `once the selected preview is drawn, draws the others ahead, nearest first, one at a time`() {
        val graph = graph(first, second, third, fourth)
        val activity = start()
        activity.list().setSelection(1)
        idle()
        graph.folderPictures.previewed.clear()

        repeat(4) { graph.next() }

        assertEquals(listOf("2.jpg", "3.jpg", "1.jpg", "4.jpg"), graph.folderPictures.previewed.map { it.name })
        assertEquals(0, graph.jobs.pending)
    }

    @Test
    fun `a picture drawn ahead shows at once when it's selected, with no job`() {
        val graph = graph(first, second)
        val activity = start()
        repeat(2) { graph.next() }   // the first one's, then the second's ahead
        val drawn = graph.folderPictures.previewed.size

        activity.list().setSelection(1)
        idle()

        assertNotNull(activity.findViewById<ImageView>(R.id.settings_pictures_folder_preview).drawable)
        assertEquals(drawn, graph.folderPictures.previewed.size)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.settings_pictures_folder_loading).visibility)
    }

    @Test
    fun `shows a spinning wheel in the preview's place while it's drawn`() {
        val graph = graph(first)
        val activity = start()
        val wheel = activity.findViewById<View>(R.id.settings_pictures_folder_loading)
        assertEquals(View.VISIBLE, wheel.visibility)

        graph.next()

        assertEquals(View.GONE, wheel.visibility)
        assertNotNull(activity.findViewById<ImageView>(R.id.settings_pictures_folder_preview).drawable)
    }

    @Test
    fun `selecting another picture stops the one being drawn ahead, for the one the user waits for`() {
        val graph = graph(first, second, third)
        val activity = start()
        graph.next()               // the first one's
        graph.jobs.runNext()       // the second's, drawn ahead: its result not shown yet
        val ahead = graph.folderPictures.previewStops.last()
        assertFalse(ahead())

        activity.list().setSelection(2)
        idle()

        assertTrue(ahead())
    }

    @Test
    fun `draws nothing ahead of a picture this TV can't show, or too large`() {
        val graph = graph(first, phone, huge)
        start()

        repeat(5) { graph.next() }

        assertEquals(listOf("1.jpg"), graph.folderPictures.previewed.map { it.name })
    }

    @Test
    fun `forgets the previews kept when it opens, and when it closes, not when it's made anew`() {
        val graph = graph(first)
        val controller = Robolectric.buildActivity(AddFromFolderActivity::class.java).setup()
        idle()
        assertEquals(1, graph.folderPictures.forgotten)

        RuntimeEnvironment.setQualifiers("+tvdpi")
        controller.configurationChange()
        assertEquals(1, graph.folderPictures.forgotten)

        controller.get().finish()
        controller.pause().stop().destroy()
        assertEquals(2, graph.folderPictures.forgotten)
    }

    @Test
    fun `draws nothing ahead while it's hidden`() {
        val graph = graph(first, second)
        val controller = Robolectric.buildActivity(AddFromFolderActivity::class.java).setup()
        idle()
        controller.pause().stop()

        graph.jobs.runAll()
        idle()

        assertEquals(emptyList<Any>(), graph.folderPictures.previewed.filter { it.name == "2.jpg" })
    }
}
