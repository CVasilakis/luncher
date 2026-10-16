package com.luncher.launcher.pictures

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
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
 * The pictures' screens, in US English, with sample pictures drawn as gradients. Reference images:
 * src/test/screenshots/pictures/ of this module; see docs/TESTING.md.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-$TV_1080P")
// captureScreenRoboImage, the one capture of a dialog over its screen, both windows.
@OptIn(ExperimentalRoborazziApi::class)
class PicturesScreenshotTest {

    private val application = RuntimeEnvironment.getApplication() as PicturesTestApplication

    /** Four pictures, as the mockup has them; a photo on its side among them. */
    private val library = arrayOf(
        picture("sunset.jpg", added = 4, bytes = 3_200_000),
        picture("beach.png", added = 3, bytes = 3_100_000),
        picture("phone-photo.jpg", added = 2, width = 3024, height = 4032, bytes = 2_400_000),
        picture("a picture with a name far too long for its tile.jpg", added = 1, bytes = 1_100_000),
    )

    // A remote's keys: the grid shows its selection only out of touch mode, where Robolectric starts.
    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Pictures with [pictures], the first one the wallpaper, every thumbnail drawn, the first focused. */
    private fun pictures(vararg pictures: com.luncher.domain.pictures.Picture, intent: Intent? = null, screen: Class<out PicturesActivity> = PicturesActivity::class.java): PicturesActivity {
        val graph = TestPicturesGraph(FakePictures(*pictures)).also { application.graph = it }
        pictures.firstOrNull()?.let { graph.appearanceSettings.appearance = Appearance(background = Background(picture = it.id)) }
        val activity = Robolectric.buildActivity(screen, intent).setup().get()
        idle()
        graph.jobs.runAll()
        idle()
        return activity
    }

    private fun PicturesActivity.ok(position: Int) {
        val row = findViewById<android.view.ViewGroup>(R.id.pictures_grid).getChildAt(0) as android.view.ViewGroup
        row.getChildAt(position).performClick()
        idle()
    }

    @Test
    fun picturesGrid() {
        pictures(*library).window.decorView.captureRoboImage("src/test/screenshots/pictures/pictures.png")
    }

    // From the appearance editor: No picture first, focused.
    @Test
    fun chooseTheWallpaper() {
        pictures(*library, screen = ChooseWallpaperActivity::class.java).window.decorView
            .captureRoboImage("src/test/screenshots/pictures/pictures_choose_wallpaper.png")
    }

    @Test
    fun picturesEmpty() {
        pictures().window.decorView.captureRoboImage("src/test/screenshots/pictures/pictures_empty.png")
    }

    // A picture's actions, over the dimmed grid.
    @Test
    fun pictureActions() {
        pictures(*library).ok(1)
        captureScreenRoboImage("src/test/screenshots/pictures/pictures_actions.png")
    }

    @Test
    fun deleteUnusedQuestion() {
        val activity = pictures(*library)
        activity.findViewById<android.view.View>(R.id.pictures_delete_unused).performClick()
        idle()
        captureScreenRoboImage("src/test/screenshots/pictures/pictures_delete_unused.png")
    }

    // A shared iPhone photo on a TV that can't show HEIC.
    @Test
    fun unsupportedFormatMessage() {
        val shared = Uri.parse("content://com.example.files/IMG_0001.HEIC")
        val graph = TestPicturesGraph(FakePictures(*library, results = mutableMapOf(shared to ImportResult.UnsupportedFormat(PictureFormat.HEIF))))
        application.graph = graph
        Robolectric.buildActivity(PicturesActivity::class.java, Intent(application, PicturesActivity::class.java).setData(shared)).setup()
        idle()
        graph.jobs.runAll()
        idle()
        captureScreenRoboImage("src/test/screenshots/pictures/pictures_unsupported.png")
    }

    // 720p at the 1080p density, 640x360 dp: three tiles a row, the grid scrolls.
    @Test
    @Config(qualifiers = "+w640dp-h360dp")
    fun picturesOnASmallScreen() {
        pictures(*library).window.decorView.captureRoboImage("src/test/screenshots/pictures/pictures_small_screen.png")
    }

    // Arabic: mirrored, the newest at the right; the pictures themselves aren't.
    @Test
    @Config(qualifiers = "ar-rEG-$TV_1080P")
    fun picturesInArabic() {
        pictures(*library).window.decorView.captureRoboImage("src/test/screenshots/pictures/pictures_arabic.png")
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
