package com.luncher.launcher.pictures

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingRootException
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withChild
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.FakePictures.Companion.picture
import com.luncher.domain.pictures.FolderPictures
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.pictures.PictureImport
import com.luncher.domain.pictures.Pictures
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.testing.RetryWhenCovered
import com.luncher.launcher.testing.SamplePictures
import com.luncher.launcher.testing.waitForHomeScreen
import junit.framework.AssertionFailedError
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Pictures and Add from folder on a real device, driven by real key events, and a share reaching them. */
@RunWith(AndroidJUnit4::class)
class PicturesActivityTest {

    @get:Rule
    val retryWhenCovered = RetryWhenCovered()

    private val application = ApplicationProvider.getApplicationContext<LuncherApplication>()

    /** The test's own library and drop folder, deleted after it: the device keeps what it had. */
    private val folder = File(application.filesDir, "pictures-test")
    private val images = File(application.cacheDir, "pictures-test")

    @Suppress("DEPRECATION")
    private val dropFolder = File(application.externalMediaDirs.first(), "pictures-test")

    /** New at each launch: [RetryWhenCovered] runs a covered test again on the same instance. */
    private lateinit var fake: FakePictures<Uri>

    /** Not while the home app is still starting, which can open windows over the screen. */
    @Before
    fun startFromTheHomeScreen() {
        waitForHomeScreen()
    }

    @After
    fun restore() {
        // The process outlives the test, unlike under Robolectric.
        application.graph = AppGraph(application)
        folder.deleteRecursively()
        images.deleteRecursively()
        dropFolder.deleteRecursively()
    }

    /** Pictures with these [pictures], in memory. */
    private fun launchWith(vararg pictures: com.luncher.domain.pictures.Picture): ActivityScenario<PicturesActivity> {
        fake = FakePictures(*pictures)
        application.graph = object : AppGraph(application) {
            override val pictures: Pictures = this@PicturesActivityTest.fake
            override val pictureImport: PictureImport<Uri> = this@PicturesActivityTest.fake
            override val pictureImages: PictureImages<Bitmap> = FakePictureImages { _, width, height, _ ->
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }
            }
        }
        return ActivityScenario.launch(PicturesActivity::class.java)
    }

    /** The real library and drop folder, but the test's own folders. */
    private fun useTheTestsFolders() {
        val graph = object : AppGraph(application) {
            private val library by lazy { FilesPictures(application, jobs, folder, images) }
            override val pictures: Pictures get() = library
            override val pictureImport: PictureImport<Uri> get() = library
            override val folderPictures: FolderPictures<Uri, Bitmap> by lazy { MediaFolderPictures(application, jobs) { listOf(dropFolder) } }
            override val pictureImages: PictureImages<Bitmap> by lazy { CachedPictureImages(application, FilePictureImages(application, folder), images) }
        }
        application.graph = graph
    }

    private fun press(keyCode: Int) {
        onView(isRoot()).perform(pressKey(keyCode))
    }

    private fun pressInDialog(keyCode: Int) {
        onView(isRoot()).inRoot(isDialog()).perform(pressKey(keyCode))
    }

    /** The grid's tile of the picture named [name]. */
    private fun tile(name: String) = withChild(allOf(withId(R.id.pictures_name), withText(name)))

    /** Checks [condition] on the view [view] matches, retrying while a window comes up. */
    private fun waitUntil(view: Matcher<View>, condition: Matcher<View>, inDialog: Boolean = false) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (true) {
            try {
                val interaction = onView(view)
                (if (inDialog) interaction.inRoot(isDialog()) else interaction).check(matches(condition))
                return
            } catch (e: AssertionFailedError) {
                if (SystemClock.uptimeMillis() > deadline) throw e
            } catch (e: NoMatchingViewException) {
                if (SystemClock.uptimeMillis() > deadline) throw e   // not there yet: a window coming up
            } catch (e: NoMatchingRootException) {
                if (SystemClock.uptimeMillis() > deadline) throw e   // the dialog not up yet
            }
            SystemClock.sleep(POLL_MS)
        }
    }

    @Test
    fun arrowsMoveAlongTheGrid_okOnAPictureDeletesItAfterAsking() {
        launchWith(picture("old.jpg", added = 1), picture("beach.png", added = 2), picture("sunset.jpg", added = 3)).use {
            waitUntil(tile("sunset.jpg"), hasFocus())

            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            waitUntil(tile("beach.png"), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)

            waitUntil(withText(R.string.pictures_delete), hasFocus(), inDialog = true)
            pressInDialog(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(withText(R.string.pictures_cancel), hasFocus(), inDialog = true)
            pressInDialog(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(withText(R.string.pictures_delete), hasFocus(), inDialog = true)
            pressInDialog(KeyEvent.KEYCODE_DPAD_CENTER)

            waitUntil(tile("old.jpg"), hasFocus())
            onView(tile("beach.png")).check(doesNotExist())
            assertEquals(listOf("old.jpg", "sunset.jpg"), fake.pictures.map { it.name }.sorted())
        }
    }

    @Test
    fun deleteUnused_asksFirst_thenDeletesThem() {
        launchWith(picture("beach.png", added = 2), picture("sunset.jpg", added = 3)).use {
            waitUntil(tile("sunset.jpg"), hasFocus())

            press(KeyEvent.KEYCODE_DPAD_DOWN)   // below the row, to Delete unused
            waitUntil(withId(R.id.pictures_delete_unused), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(withText(R.string.pictures_cancel), hasFocus(), inDialog = true)
            pressInDialog(KeyEvent.KEYCODE_DPAD_DOWN)
            pressInDialog(KeyEvent.KEYCODE_DPAD_CENTER)

            waitUntil(withId(R.id.pictures_empty_add), hasFocus())
            assertEquals(emptyList<Any>(), fake.pictures)
        }
    }

    @Test
    fun addFromFolder_addsAPictureFromTheDropFolder_andFocusesIt() {
        useTheTestsFolders()
        dropFolder.mkdirs()
        InstrumentationAssets.copy("pictures/baseline.jpg", File(dropFolder, "holiday.jpg"))

        ActivityScenario.launch(PicturesActivity::class.java).use {
            waitUntil(withId(R.id.pictures_empty_add), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)

            waitUntil(withText(containsString("holiday.jpg")), isDisplayed())
            waitUntil(withId(R.id.pictures_folder_list), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)

            waitUntil(tile("holiday.jpg"), hasFocus())
            waitUntil(withId(R.id.pictures_status), withText(containsString("1")))
        }
    }

    @Test
    fun aShareFromAnotherApp_addsThePicture_andShowsItInPictures() {
        useTheTestsFolders()
        val share = Intent(Intent.ACTION_SEND)
            .setClassName(application, SharePictureActivity::class.java.name)
            .setType("image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, SamplePictures.uri("progressive.jpg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)

        application.startActivity(share)

        waitUntil(tile("progressive.jpg"), hasFocus())
    }

    @Test
    fun aShareOfAPath_saysToUseTheDropFolder() {
        useTheTestsFolders()
        val share = Intent(Intent.ACTION_SEND)
            .setClassName(application, SharePictureActivity::class.java.name)
            .setType("image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("file:///sdcard/Pictures/holiday.jpg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        application.startActivity(share)

        waitUntil(withId(R.id.pictures_dialog_text), withText(containsString("Android/media/${application.packageName}")), inDialog = true)
        pressInDialog(KeyEvent.KEYCODE_DPAD_CENTER)
        waitUntil(withId(R.id.pictures_empty_add), hasFocus())
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 50L
    }
}

/** The test package's assets. */
private object InstrumentationAssets {
    fun copy(asset: String, to: File) {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use { input ->
            to.outputStream().use { input.copyTo(it) }
        }
    }
}
