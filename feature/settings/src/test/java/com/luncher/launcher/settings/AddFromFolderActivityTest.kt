package com.luncher.launcher.settings

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.luncher.domain.pictures.FakeFolderPictures
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

    private fun graph(vararg pictures: com.luncher.domain.pictures.FolderPicture<android.net.Uri>): TestPicturesGraph {
        val graph = object : TestPicturesGraph() {
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
    fun `OK on a picture returns it to Pictures, which adds it`() {
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
}
