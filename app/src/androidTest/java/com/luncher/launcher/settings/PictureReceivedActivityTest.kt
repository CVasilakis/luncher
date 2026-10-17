package com.luncher.launcher.settings

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingRootException
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withChild
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.pictures.PictureImport
import com.luncher.domain.pictures.Pictures
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.home.HomeActivity
import com.luncher.launcher.pictures.CachedPictureImages
import com.luncher.launcher.pictures.FilePictureImages
import com.luncher.launcher.pictures.FilesPictures
import com.luncher.launcher.testing.RetryWhenCovered
import com.luncher.launcher.testing.SamplePictures
import com.luncher.launcher.testing.resolvedHome
import com.luncher.launcher.testing.waitForFocus
import com.luncher.launcher.testing.waitForHomeScreen
import junit.framework.AssertionFailedError
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A picture shared from another app, on a real device: Luncher's home screen opens with the
 * window over it, which adds the picture; Back leaves the home screen, Background the editor.
 */
@RunWith(AndroidJUnit4::class)
class PictureReceivedActivityTest {

    @get:Rule
    val retryWhenCovered = RetryWhenCovered()

    private val application = ApplicationProvider.getApplicationContext<LuncherApplication>()

    /** The test's own library, deleted after it: the device keeps what it had. */
    private val folder = File(application.filesDir, "pictures-test")
    private val images = File(application.cacheDir, "pictures-test")

    /**
     * Closing Luncher's home screen at the end of a test can hang while Luncher is the device's
     * home app (docs/INSTRUMENTED-TESTS.md): fail at once instead.
     */
    @Before
    fun checkLuncherIsNotTheHomeApp() {
        assertNotEquals(
            "Luncher is the device's home app; re-enable the stock launcher first (docs/INSTRUMENTED-TESTS.md)",
            application.packageName,
            resolvedHome(),
        )
    }

    /** Not while the home app is still starting, which can open windows over the screen. */
    @Before
    fun startFromTheHomeScreen() {
        waitForHomeScreen()
    }

    /** The real library, in the test's own folders. */
    @Before
    fun useTheTestsFolders() {
        application.graph = object : AppGraph(application) {
            private val library by lazy { FilesPictures(application, jobs, folder, images) }
            override val pictures: Pictures get() = library
            override val pictureImport: PictureImport<Uri> get() = library
            override val pictureImages: PictureImages<Bitmap> by lazy { CachedPictureImages(application, FilePictureImages(application, folder), images) }
            override val appearanceSettings = FakeAppearanceSettings()
        }
    }

    @After
    fun restore() {
        // The process outlives the test, unlike under Robolectric.
        application.graph = AppGraph(application)
        folder.deleteRecursively()
        images.deleteRecursively()
    }

    private fun share(intent: Intent) {
        application.startActivity(intent.setClassName(application, SharePictureActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * A key, as from a remote, to the focused window. Not Espresso's, which needs one resumed
     * activity: right after a share, the home screen and the window are both being started.
     */
    private fun press(keyCode: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode)
    }

    /** Checks [condition] on the view [view] matches, retrying while a window comes up. */
    private fun waitUntil(view: Matcher<View>, condition: Matcher<View>) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (true) {
            try {
                onView(view).check(matches(condition))
                return
            } catch (e: AssertionFailedError) {
                if (SystemClock.uptimeMillis() > deadline) throw e
            } catch (e: NoMatchingViewException) {
                if (SystemClock.uptimeMillis() > deadline) throw e   // not there yet: a window coming up
            } catch (e: NoMatchingRootException) {
                if (SystemClock.uptimeMillis() > deadline) throw e
            }
            SystemClock.sleep(POLL_MS)
        }
    }

    @Test
    fun aShare_opensTheHomeScreenWithTheWindowOverIt_andBackLeavesTheHomeScreen() {
        share(
            Intent(Intent.ACTION_SEND).setType("image/jpeg")
                .putExtra(Intent.EXTRA_STREAM, SamplePictures.uri("progressive.jpg"))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )

        waitForFocus(PictureReceivedActivity::class.java)
        waitUntil(withId(R.id.settings_received_title), withText(R.string.settings_received_saved))
        waitUntil(withId(R.id.settings_received_text), withText("progressive.jpg"))
        waitUntil(withText(R.string.settings_appearance_background), hasFocus())

        press(KeyEvent.KEYCODE_BACK)
        waitForFocus(HomeActivity::class.java)
    }

    @Test
    fun background_opensTheEditorWithTheReceivedPictureChosen() {
        share(
            Intent(Intent.ACTION_SEND).setType("image/jpeg")
                .putExtra(Intent.EXTRA_STREAM, SamplePictures.uri("progressive.jpg"))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        waitForFocus(PictureReceivedActivity::class.java)
        waitUntil(withText(R.string.settings_appearance_background), hasFocus())

        press(KeyEvent.KEYCODE_DPAD_CENTER)

        waitForFocus(BackgroundActivity::class.java)
        waitUntil(withChild(allOf(withId(R.id.settings_background_name), withText("progressive.jpg"))), hasFocus())
        waitUntil(allOf(withId(R.id.settings_background_chosen), isDisplayed()), isDisplayed())
    }

    // Another app's picture, shared without giving access to it: Android refuses to pass on access
    // Luncher hasn't got, which must not crash it.
    @Test
    fun aShareWithoutAccess_saysItCantReadIt_andOkLeavesTheHomeScreen() {
        // "Set as", which Android doesn't give a read grant of its own, as it does a Share's stream.
        share(Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(Uri.parse("content://media/external/images/media/1"), "image/jpeg"))

        waitForFocus(PictureReceivedActivity::class.java)
        waitUntil(withId(R.id.settings_received_text), withText(containsString("Android/media/${application.packageName}")))
        waitUntil(withText(R.string.settings_pictures_ok), hasFocus())
        press(KeyEvent.KEYCODE_DPAD_CENTER)

        waitForFocus(HomeActivity::class.java)
    }

    @Test
    fun aShareOfAPath_saysToUseTheDropFolder() {
        share(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, Uri.parse("file:///sdcard/Pictures/holiday.jpg")))

        waitForFocus(PictureReceivedActivity::class.java)
        waitUntil(withId(R.id.settings_received_text), withText(containsString("Android/media/${application.packageName}")))
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 50L
    }
}
