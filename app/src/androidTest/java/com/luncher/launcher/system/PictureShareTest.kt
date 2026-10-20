package com.luncher.launcher.system

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withChild
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.pictures.PreparedPictures
import com.luncher.domain.pictures.PictureImport
import com.luncher.domain.pictures.Pictures
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.home.HomeActivity
import com.luncher.launcher.pictures.CachedPictureImages
import com.luncher.launcher.pictures.WorkingCopyImages
import com.luncher.launcher.pictures.FilesPictures
import com.luncher.launcher.settings.BackgroundActivity
import com.luncher.launcher.settings.PictureReceivedActivity
import com.luncher.launcher.settings.R
import com.luncher.launcher.settings.SharePictureActivity
import com.luncher.launcher.testing.RetryWhenCovered
import com.luncher.launcher.testing.GrantedPictures
import com.luncher.launcher.testing.SamplePictures
import com.luncher.launcher.testing.ShareSender
import com.luncher.launcher.testing.StalledPictures
import com.luncher.launcher.testing.resolvedHome
import com.luncher.launcher.testing.waitForFocus
import com.luncher.launcher.testing.waitForHomeScreen
import com.luncher.launcher.testing.waitUntilMatches
import com.luncher.launcher.testing.waitUntilSeen
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A picture shared from another app, on a real device: Luncher's home screen opens with the
 * window over it, which adds the pictures; Back leaves the home screen, Background the editor.
 *
 * The share opens Luncher's home screen in a task of its own, which the runner closes after the
 * test: closed in front, Android would bring forward the task next in line, which can be another
 * app's (the device's settings, after HomeKeyTest), or on API 22 ask which home app, and the next
 * test would wait for a home screen behind it. So each test ends by pressing Home ([goHome]), and
 * the class is in the system tier (API 24 on).
 */
@RunWith(AndroidJUnit4::class)
class PictureShareTest {

    @get:Rule
    val retryWhenCovered = RetryWhenCovered()

    private val application = ApplicationProvider.getApplicationContext<LuncherApplication>()

    /** The test's own library, deleted after it: the device keeps what it had. */
    private val folder = File(application.filesDir, "pictures-test")
    private val images = File(application.cacheDir, "pictures-test")
    private val copies = File(application.filesDir, "picture-copies-test")

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
            private val library by lazy { FilesPictures(application, jobs, outsideCalls, folder, images, copies = copies) }
            private val workingCopies by lazy { WorkingCopyImages(application, folder, copies) }
            override val pictures: Pictures get() = library
            override val pictureImport: PictureImport<Uri> get() = library
            override val pictureImages: PictureImages<Bitmap> get() = workingCopies
            override val thumbnails: PictureImages<Bitmap> by lazy { CachedPictureImages(application, workingCopies, images) }
            override val preparedPictures: PreparedPictures get() = workingCopies
            override val appearanceSettings = FakeAppearanceSettings()
        }
    }

    /**
     * Home, so the stock launcher is in front before the runner closes Luncher's tasks behind it:
     * closed in front, Android would bring forward the task next in line, which can be another
     * app's (the device's settings, left by an earlier test), and the next test would wait for the
     * home screen behind it.
     */
    @After
    fun goHome() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressHome()
        waitForHomeScreen()
    }

    @After
    fun restore() {
        // The process outlives the test, unlike under Robolectric.
        application.graph = AppGraph(application)
        folder.deleteRecursively()
        images.deleteRecursively()
        copies.deleteRecursively()
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

    /** Checks [condition] on the view [view] matches, retrying while a window comes up, for [timeoutMs] at most. */
    private fun waitUntil(view: Matcher<View>, condition: Matcher<View>, timeoutMs: Long = TIMEOUT_MS) = waitUntilMatches(view, condition, timeoutMs)

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

    // An app that opens the picture, then never sends it: after the library's time limit, the
    // window says it can't read it.
    @Test
    fun aShareThatNeverComes_saysItCantReadIt_afterTheTimeLimit() {
        share(
            Intent(Intent.ACTION_SEND).setType("image/jpeg")
                .putExtra(Intent.EXTRA_STREAM, StalledPictures.silent("cloud.jpg"))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )

        waitForFocus(PictureReceivedActivity::class.java)
        waitUntilSeen(application.getString(R.string.settings_pictures_cancel), focused = true)
        waitUntilSeen("Android/media/${application.packageName}", timeoutMs = STALLED_TIMEOUT_MS)
        waitUntil(withText(R.string.settings_pictures_ok), hasFocus())
        press(KeyEvent.KEYCODE_DPAD_CENTER)

        waitForFocus(HomeActivity::class.java)
    }

    // An app that never answers the open, the one call Luncher can't stop: Cancel leaves the
    // home screen at once, and the next share is added, as nothing waits behind the stuck call.
    @Test
    fun cancel_leavesAShareThatNeverOpens_andTheNextShareIsAdded() {
        share(
            Intent(Intent.ACTION_SEND).setType("image/jpeg")
                .putExtra(Intent.EXTRA_STREAM, StalledPictures.unopened("cloud.jpg"))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        waitForFocus(PictureReceivedActivity::class.java)
        waitUntilSeen(application.getString(R.string.settings_pictures_cancel), focused = true)

        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitForFocus(HomeActivity::class.java)

        shareSample("progressive.jpg")
        waitForFocus(PictureReceivedActivity::class.java)
        waitUntil(withId(R.id.settings_received_title), withText(R.string.settings_received_saved))
    }

    // A gallery's share of three pictures, which only the access it gives opens: Luncher passes
    // that access on to its window, which adds them all, and says so.
    @Test
    fun aShareOfSeveralPictures_fromAnotherApp_addsThemAll() {
        val pictures = arrayListOf(GrantedPictures.uri("progressive.jpg"), GrantedPictures.uri("baseline.jpg"), GrantedPictures.uri("exif6.jpg"))
        val testPackage = InstrumentationRegistry.getInstrumentation().context.packageName
        application.startActivity(
            Intent().setClassName(testPackage, ShareSender::class.java.name)
                .putParcelableArrayListExtra(ShareSender.EXTRA_PICTURES, pictures)
                .putExtra(ShareSender.EXTRA_TARGET, ComponentName(application, SharePictureActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

        waitForFocus(PictureReceivedActivity::class.java)
        waitUntil(withId(R.id.settings_received_title), withText(application.resources.getQuantityString(R.plurals.settings_received_saved_count, 3, 3)))
        waitUntil(withText(R.string.settings_pictures_ok), hasFocus())
        press(KeyEvent.KEYCODE_DPAD_CENTER)

        waitForFocus(HomeActivity::class.java)
    }

    // A share that arrives while the window adds another app's picture, which never opens: it
    // joins the window's session rather than replacing it, which used to cancel the first picture
    // (the same window; on API 29, where Android has just closed Luncher's task, a new one that
    // takes the session over). Once the first has given up (the library's time limit), the window
    // says how both went.
    @Test
    fun aShareWhileAnotherIsAdded_joinsTheSameWindow() {
        share(
            Intent(Intent.ACTION_SEND).setType("image/jpeg")
                .putExtra(Intent.EXTRA_STREAM, StalledPictures.unopened("cloud.jpg"))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        waitForFocus(PictureReceivedActivity::class.java)
        waitUntilSeen(application.getString(R.string.settings_pictures_cancel), focused = true)

        shareSample("progressive.jpg")

        waitUntilSeen(application.getString(R.string.settings_received_adding_count, 1, 2))
        waitUntilSeen(application.resources.getQuantityString(R.plurals.settings_received_saved_count, 1, 1), timeoutMs = STALLED_TIMEOUT_MS)
        waitUntil(withId(R.id.settings_received_text), withText(application.resources.getQuantityString(R.plurals.settings_received_unreadable_count, 1, 1)))
        waitUntil(withText(R.string.settings_pictures_ok), hasFocus())
        press(KeyEvent.KEYCODE_DPAD_CENTER)

        waitForFocus(HomeActivity::class.java)
    }

    // Home while the window adds another app's picture, which never opens, and a sample after it:
    // the window, left for the stock launcher (the home app while the tests run), stops the
    // adding as Cancel does, so the sample is never added, not even once the first picture has
    // given up (the library's time limit). Android leaves the window alive there, in the
    // background, unlike while Luncher is the home app, where Home closes it.
    @Test
    fun home_whileAdding_stopsThePicturesStillToAdd() {
        share(
            Intent(Intent.ACTION_SEND).setType("image/jpeg")
                .putExtra(Intent.EXTRA_STREAM, StalledPictures.unopened("cloud.jpg"))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        waitForFocus(PictureReceivedActivity::class.java)
        waitUntilSeen(application.getString(R.string.settings_pictures_cancel), focused = true)
        shareSample("progressive.jpg")
        waitUntilSeen(application.getString(R.string.settings_received_adding_count, 1, 2))

        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressHome()
        waitForHomeScreen()
        SystemClock.sleep(STALLED_TIMEOUT_MS)

        var added = emptyList<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { added = application.graph.pictures.read().map { it.name } }
        assertEquals(emptyList<String>(), added)
    }

    // Two shares alike in a row (a SEND of the same type), each sent to a task of its own, as
    // the tests send them: the second opens its window too, though the first one's home screen is
    // still in front. Before SharePictureActivity had no task affinity, Android only brought back
    // the task the first share made, and the second picture never arrived (its manifest).
    @Test
    fun twoSharesAlikeInARow_eachOpensTheWindow() {
        shareSample("progressive.jpg")
        waitForFocus(PictureReceivedActivity::class.java)
        waitUntil(withId(R.id.settings_received_text), withText("progressive.jpg"))
        press(KeyEvent.KEYCODE_BACK)
        waitForFocus(HomeActivity::class.java)

        shareSample("baseline.jpg")

        waitForFocus(PictureReceivedActivity::class.java)
        waitUntil(withId(R.id.settings_received_text), withText("baseline.jpg"))
    }

    /** Shares the sample [name], a JPEG, as a gallery does, with read access. */
    private fun shareSample(name: String) {
        share(
            Intent(Intent.ACTION_SEND).setType("image/jpeg")
                .putExtra(Intent.EXTRA_STREAM, SamplePictures.uri(name))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    @Test
    fun aShareOfAPath_saysToUseTheDropFolder() {
        share(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, Uri.parse("file:///sdcard/Pictures/holiday.jpg")))

        waitForFocus(PictureReceivedActivity::class.java)
        waitUntil(withId(R.id.settings_received_text), withText(containsString("Android/media/${application.packageName}")))
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L

        /** For a share that never comes: the library's time limit (15 s), and the usual margin. */
        const val STALLED_TIMEOUT_MS = 25_000L
    }
}
