package com.luncher.launcher.settings

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.luncher.domain.pictures.FakeFolderPictures
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.PictureFormat
import androidx.test.platform.app.InstrumentationRegistry
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `lists the TV's pictures first, then the drives', each by name, with their size`() {
        graph(logo, holiday, wallpaper, phone)

        val activity = start()

        assertEquals(
            listOf(
                "This TV · IMG_0001.HEIC | This TV can't show HEIF (HEIC) pictures",
                "This TV · wallpaper-4k.jpg | 4000 × 3000 · 2.4 MB",
                "USB drive · holiday.jpg | 4000 × 3000 · 2.4 MB",
                "USB drive · logo.png | 4000 × 3000 · 2.4 MB",
            ),
            activity.rows(),
        )
        assertEquals("Pictures in the folder Android/media/${activity.packageName}, on this TV or a USB drive", activity.findViewById<TextView>(R.id.settings_pictures_folder_where).text.toString())
    }

    @Test
    fun `says when the folders have no pictures`() {
        graph()

        val activity = start()

        val state = activity.findViewById<TextView>(R.id.settings_pictures_folder_state)
        assertEquals(View.VISIBLE, state.visibility)
        assertEquals("There are no pictures in the folder yet.", state.text.toString())
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

        assertEquals(listOf("logo.png"), graph.folderPictures.previewed.map { it.name })
        assertNotNull(activity.findViewById<ImageView>(R.id.settings_pictures_folder_preview).drawable)
    }

    @Test
    fun `OK on a picture returns it to the background editor, which adds it`() {
        graph(wallpaper, holiday)
        val activity = start()

        activity.list().performItemClick(null, 1, 1)

        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals(holiday.source, shadowOf(activity).resultIntent.data)
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
            "This TV can't show HEIF (HEIC) pictures. Save it as a JPEG or PNG, and add that instead.",
            dialog.findViewById<TextView>(R.id.settings_pictures_dialog_text).text.toString(),
        )
        assertEquals(1, dialog.findViewById<LinearLayout>(R.id.settings_pictures_dialog_entries).childCount)
    }

    @Test
    fun `draws no preview of a picture this TV can't show`() {
        val graph = graph(phone)
        val activity = start()
        graph.jobs.runAll()

        assertEquals(emptyList<Any>(), graph.folderPictures.previewed)
        assertNull(activity.findViewById<ImageView>(R.id.settings_pictures_folder_preview).drawable)
    }

    @Test
    fun `stops previewing when it's hidden`() {
        val graph = graph(wallpaper)
        val controller = Robolectric.buildActivity(AddFromFolderActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        controller.pause().stop()

        assertEquals(0, graph.jobs.pending)
    }

    // The library has "holiday.jpg" (2.4 MB, as the folder's) and "logo.png" (another size).
    private val known = FakePictures<android.net.Uri>(
        FakePictures.picture("holiday.jpg", bytes = 2_400_000),
        FakePictures.picture("logo.png", bytes = 99_000),
    )
    private val huge = folderPicture("panorama.jpg", bytes = 184_000_000, width = 21621, height = 7004)

    @Test
    fun `marks the pictures it can tell can't be added, too large or named like one of the library`() {
        graph(holiday, logo, huge, wallpaper, library = known)

        val activity = start()

        assertEquals(
            listOf(
                "This TV · panorama.jpg | 21621 × 7004 · 184 MB · Too large to add",
                "This TV · wallpaper-4k.jpg | 4000 × 3000 · 2.4 MB",
                "USB drive · holiday.jpg | 4000 × 3000 · 2.4 MB · In Luncher's pictures already",
                "USB drive · logo.png | 4000 × 3000 · 2.4 MB · Another of Luncher's pictures has this name",
            ),
            activity.rows(),
        )
    }

    @Test
    fun `OK on a picture it can tell can't be added says why at once, reading nothing, and stays`() {
        val graph = graph(holiday, logo, huge, library = known)
        val activity = start()
        val messages = listOf(
            0 to "It's too large: a picture can have up to 50 MB and 64 megapixels.",
            1 to "holiday.jpg is in Luncher's pictures already.",
            2 to "Luncher's pictures have another picture named logo.png. Delete that one in Luncher first, or give this file another name, then add it.",
        )

        for ((position, message) in messages) {
            activity.list().performItemClick(null, position, position.toLong())
            shadowOf(Looper.getMainLooper()).idle()

            assertFalse(activity.isFinishing)
            assertEquals(message, ShadowDialog.getLatestDialog().findViewById<TextView>(R.id.settings_pictures_dialog_text).text.toString())
        }
        assertEquals(emptyList<Any>(), known.added)
        graph.jobs.runAll()
        assertEquals(emptyList<Any>(), graph.folderPictures.previewed)   // the selected one, the panorama, has none
    }

    @Test
    fun `draws no preview of a picture too large to add`() {
        val graph = graph(huge)
        val activity = start()
        graph.jobs.runAll()

        assertEquals(emptyList<Any>(), graph.folderPictures.previewed)
        assertNull(activity.findViewById<ImageView>(R.id.settings_pictures_folder_preview).drawable)
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
}
