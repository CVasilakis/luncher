package com.luncher.launcher.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.FakeFolderPictures
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.FakePictures.Companion.picture
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.PictureFormat
import com.luncher.launcher.testing.TV_1080P
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The background editor, the window after a share and Add from folder, in US English, with sample pictures drawn as
 * gradients. Reference images: src/test/screenshots/settings/ and pictures/ of this module; see
 * docs/TESTING.md.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-$TV_1080P")
// captureScreenRoboImage, the one capture of a dialog over its screen, both windows.
@OptIn(ExperimentalRoborazziApi::class)
class BackgroundScreenshotTest {

    private val application = RuntimeEnvironment.getApplication() as SettingsTestApplication

    /** Four pictures; a photo on its side among them, and a name too long for its row. */
    private val library = arrayOf(
        picture("sunset.jpg", added = 4, bytes = 3_200_000),
        picture("beach.png", added = 3, bytes = 3_100_000),
        picture("phone-photo.jpg", added = 2, width = 3024, height = 4032, bytes = 2_400_000),
        picture("a picture with a name far too long for its row.jpg", added = 1, bytes = 1_100_000),
    )

    // A remote's keys: the focus shows only out of touch mode, where Robolectric starts.
    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** The background editor with [pictures], the first one the wallpaper, dimmed and lightly blurred, every image drawn. */
    private fun editor(vararg pictures: com.luncher.domain.pictures.Picture): BackgroundActivity {
        val graph = TestPicturesGraph(FakePictures(*pictures)).also { application.graph = it }
        pictures.firstOrNull()?.let { graph.appearanceSettings.appearance = Appearance(background = Background(it.id, dim = 20, blur = Blur.LIGHT)) }
        val activity = Robolectric.buildActivity(BackgroundActivity::class.java).setup().get()
        idle()
        graph.jobs.runAll()
        idle()
        return activity
    }

    /** The row of the list named [name]. */
    private fun BackgroundActivity.row(name: String): View {
        val list = findViewById<ViewGroup>(R.id.settings_background_list)
        return (0 until list.childCount).map(list::getChildAt).single { it.findViewById<TextView>(R.id.settings_background_name)?.text == name }
    }

    // The wallpaper chosen and focused, its options at their values, the preview drawing it.
    @Test
    fun backgroundEditor() {
        editor(*library).window.decorView.captureRoboImage("src/test/screenshots/settings/settings_background.png")
    }

    // No picture yet: how to add one.
    @Test
    fun backgroundEditorEmpty() {
        editor().window.decorView.captureRoboImage("src/test/screenshots/settings/settings_background_empty.png")
    }

    // After Save: the window saying the wallpaper is being drawn.
    @Test
    fun backgroundEditorSaving() {
        val activity = editor(*library)
        activity.row("beach.png").performClick()
        activity.findViewById<View>(R.id.settings_background_save).performClick()
        idle()
        captureScreenRoboImage("src/test/screenshots/settings/settings_background_saving.png")
    }

    // A long press of OK on the wallpaper's picture: the question, saying it's the wallpaper.
    @Test
    fun deleteQuestion() {
        editor(*library).row("sunset.jpg").performLongClick()
        idle()
        captureScreenRoboImage("src/test/screenshots/settings/settings_background_delete.png")
    }

    /** The window over the home screen after a share of [shared], which adds as [result]. */
    private fun received(shared: Uri, result: ImportResult?) {
        val graph = TestPicturesGraph(FakePictures(*library)).also { application.graph = it }
        if (result != null) graph.library.results[shared] = result else graph.library.answerLater = true
        Robolectric.buildActivity(PictureReceivedActivity::class.java, Intent().putParcelableArrayListExtra(PictureReceivedActivity.EXTRA_PICTURES, arrayListOf(shared))).setup()
        idle()
    }

    // A photo shared from a file manager, saved: what to use it for.
    @Test
    fun pictureReceived() {
        val shared = Uri.parse("content://com.example.files/holiday.jpg")
        received(shared, ImportResult.Added(picture("holiday.jpg", added = 9)))
        captureScreenRoboImage("src/test/screenshots/settings/settings_received.png")
    }

    // While it's added.
    @Test
    fun pictureReceivedAdding() {
        received(Uri.parse("content://com.example.files/holiday.jpg"), null)
        captureScreenRoboImage("src/test/screenshots/settings/settings_received_adding.png")
    }

    /** The window after a share of four photos, the last not a picture, the second being added with [adding]. */
    private fun receivedFour(adding: Boolean) {
        val shared = (1..4).map { Uri.parse("content://com.example.files/IMG_000$it.jpg") }
        val graph = TestPicturesGraph(FakePictures(*library)).also { application.graph = it }
        graph.library.results[shared[0]] = ImportResult.Added(picture("IMG_0001.jpg", added = 9))
        graph.library.results[shared[1]] = ImportResult.AlreadyThere(library[0])
        graph.library.results[shared[2]] = ImportResult.Added(picture("IMG_0003.jpg", added = 10))
        graph.library.answerLater = adding
        Robolectric.buildActivity(PictureReceivedActivity::class.java, Intent().putParcelableArrayListExtra(PictureReceivedActivity.EXTRA_PICTURES, ArrayList(shared))).setup()
        if (adding) graph.library.finishAdding()
        idle()
    }

    // Four photos shared at once, the second being added.
    @Test
    fun picturesReceivedAdding() {
        receivedFour(adding = true)
        captureScreenRoboImage("src/test/screenshots/settings/settings_received_several_adding.png")
    }

    // Four photos shared at once, one of them not a picture: how many were saved, and how many not.
    @Test
    fun picturesReceived() {
        receivedFour(adding = false)
        captureScreenRoboImage("src/test/screenshots/settings/settings_received_several.png")
    }

    // A shared iPhone photo on a TV that can't show HEIC.
    @Test
    fun pictureReceivedUnsupported() {
        val shared = Uri.parse("content://com.example.files/IMG_0001.HEIC")
        received(shared, ImportResult.UnsupportedFormat(PictureFormat.HEIF))
        captureScreenRoboImage("src/test/screenshots/settings/settings_received_unsupported.png")
    }

    // 720p at the 1080p density, 640x360 dp: the pictures take half the screen; the start scrolls.
    @Test
    @Config(qualifiers = "+w640dp-h360dp")
    fun backgroundEditorOnASmallScreen() {
        editor(*library).window.decorView.captureRoboImage("src/test/screenshots/settings/settings_background_small_screen.png")
    }

    // Arabic: mirrored, the pictures at the left; the preview's content and the pictures themselves aren't.
    @Test
    @Config(qualifiers = "ar-rEG-$TV_1080P")
    fun backgroundEditorInArabic() {
        editor(*library).window.decorView.captureRoboImage("src/test/screenshots/settings/settings_background_arabic.png")
    }

    /** Add from folder with [pictures] in the folders, the first selected and previewed. */
    private fun folderScreen(vararg pictures: com.luncher.domain.pictures.FolderPicture<Uri>): Activity {
        val graph = object : TestPicturesGraph() {
            override val folderPictures = FakeFolderPictures(*pictures, draw = { picture, width, height -> sample(picture.name, width, height) })
        }
        application.graph = graph
        val activity = Robolectric.buildActivity(AddFromFolderActivity::class.java).setup().get()
        idle()
        graph.jobs.runAll()
        idle()
        return activity
    }

    // The mockup's files, and an iPhone photo this TV can't show.
    @Test
    fun addFromFolder() {
        folderScreen(
            folderPicture("holiday.jpg", removable = true),
            folderPicture("logo-netflix.png", removable = true),
            folderPicture("wallpaper-4k.jpg"),
            folderPicture("IMG_0001.HEIC", unsupported = PictureFormat.HEIF),
        ).window.decorView.captureRoboImage("src/test/screenshots/pictures/pictures_add_from_folder.png")
    }

    @Test
    fun addFromFolderEmpty() {
        folderScreen().window.decorView.captureRoboImage("src/test/screenshots/pictures/pictures_add_from_folder_empty.png")
    }
}
