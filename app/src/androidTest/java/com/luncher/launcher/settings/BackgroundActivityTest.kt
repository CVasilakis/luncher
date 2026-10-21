package com.luncher.launcher.settings

import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withChild
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.FakePreparedPictures
import com.luncher.domain.pictures.PreparedPictures
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.FakePictures.Companion.picture
import com.luncher.domain.pictures.FolderPictures
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.pictures.PictureImport
import com.luncher.domain.pictures.Pictures
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.pictures.CachedPictureImages
import com.luncher.launcher.pictures.WorkingCopyImages
import com.luncher.launcher.pictures.FilesPictures
import com.luncher.launcher.pictures.MediaFolderPictures
import com.luncher.launcher.testing.FailureCapture
import com.luncher.launcher.testing.RetryWhenCovered
import com.luncher.launcher.testing.longPressOk
import com.luncher.launcher.testing.matchesNow
import com.luncher.launcher.testing.waitForFocus
import com.luncher.launcher.testing.waitForHomeScreen
import com.luncher.launcher.testing.waitUntilDestroyed
import com.luncher.launcher.testing.waitUntilMatches
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The background editor and Add from folder on a real device, driven by real key events. */
@RunWith(AndroidJUnit4::class)
class BackgroundActivityTest {

    @get:Rule
    val retryWhenCovered = RetryWhenCovered()

    private val application = ApplicationProvider.getApplicationContext<LuncherApplication>()

    /** The test's own library and drop folder, deleted after it: the device keeps what it had. */
    private val folder = File(application.filesDir, "pictures-test")
    private val images = File(application.cacheDir, "pictures-test")
    private val copies = File(application.filesDir, "picture-copies-test")

    @Suppress("DEPRECATION")
    private val dropFolder = File(application.externalMediaDirs.first(), "pictures-test")

    /** New at each launch: [RetryWhenCovered] runs a covered test again on the same instance. */
    private lateinit var fake: FakePictures<Uri>
    private lateinit var appearance: FakeAppearanceSettings

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
        copies.deleteRecursively()
        dropFolder.deleteRecursively()
    }

    /** The background editor with these [pictures], in memory, and [background] stored. */
    private fun launchWith(
        vararg pictures: com.luncher.domain.pictures.Picture,
        background: Background = Background(),
    ): ActivityScenario<BackgroundActivity> {
        fake = FakePictures(*pictures)
        appearance = FakeAppearanceSettings(Appearance(background = background))
        application.graph = object : AppGraph(application) {
            override val pictures: Pictures = this@BackgroundActivityTest.fake
            override val pictureImport: PictureImport<Uri> = this@BackgroundActivityTest.fake
            override val pictureImages: PictureImages<Bitmap> = FakePictureImages { _, width, height, _ ->
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }
            }
            override val thumbnails: PictureImages<Bitmap> get() = pictureImages
            // In memory, every picture prepared: no window over the editor as it opens.
            override val preparedPictures: PreparedPictures = FakePreparedPictures()
            override val appearanceSettings = this@BackgroundActivityTest.appearance
            override val folderPictures: FolderPictures<Uri, Bitmap> by lazy { MediaFolderPictures(application, jobs, folders = { listOf(dropFolder) }) }
        }
        return ActivityScenario.launch(BackgroundActivity::class.java)
    }

    /** The real library and drop folder, but the test's own folders. */
    private fun useTheTestsFolders() {
        val graph = object : AppGraph(application) {
            private val library by lazy { FilesPictures(application, jobs, outsideCalls, folder, images, copies = copies) }
            private val workingCopies by lazy { WorkingCopyImages(application, folder, copies) }
            override val pictures: Pictures get() = library
            override val pictureImport: PictureImport<Uri> get() = library
            override val folderPictures: FolderPictures<Uri, Bitmap> by lazy { MediaFolderPictures(application, jobs, folders = { listOf(dropFolder) }) }
            override val pictureImages: PictureImages<Bitmap> get() = workingCopies
            override val thumbnails: PictureImages<Bitmap> by lazy { CachedPictureImages(application, workingCopies, images) }
            override val preparedPictures: PreparedPictures get() = workingCopies
            override val appearanceSettings = FakeAppearanceSettings()
        }
        application.graph = graph
    }

    private fun press(keyCode: Int) {
        onView(isRoot()).perform(pressKey(keyCode))
    }

    private fun pressInDialog(keyCode: Int) {
        onView(isRoot()).inRoot(isDialog()).perform(pressKey(keyCode))
    }

    private fun pressInList(keyCode: Int) {
        onView(isRoot()).inRoot(isPlatformPopup()).perform(pressKey(keyCode))
    }

    /** The background's option named [label], with [value]. */
    private fun option(label: Int, value: Int) = allOf(
        withChild(withText(label)),
        withChild(allOf(withId(R.id.settings_option_value), withText(value))),
    )

    /** The list's row named [name]. */
    private fun row(name: String) = withChild(allOf(withId(R.id.settings_background_name), withText(name)))

    private fun row(name: Int) = withChild(allOf(withId(R.id.settings_background_name), withText(name)))

    /**
     * Checks [condition] on the view [view] matches, in a dialog ([inDialog]) or an option's list
     * ([inList]), retrying while a window comes up ([waitUntilMatches]). Not on the editor while a
     * window over it has the focus: Espresso waits for the focus to come back.
     */
    private fun waitUntil(view: Matcher<View>, condition: Matcher<View>, inDialog: Boolean = false, inList: Boolean = false) =
        waitUntilMatches(view, condition, TIMEOUT_MS, root = if (inDialog) isDialog() else if (inList) isPlatformPopup() else null)

    @Test
    fun arrowsMoveAlongTheList_aLongPressOfOkOnAPictureDeletesItAfterAsking() {
        launchWith(picture("old.jpg", added = 1), picture("beach.png", added = 2), picture("sunset.jpg", added = 3)).use {
            waitUntil(row(R.string.settings_pictures_no_picture), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(row("beach.png"), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(row("old.jpg"), hasFocus())
            waitForFocus(BackgroundActivity::class.java)
            longPressOk()

            waitUntil(withText(R.string.settings_pictures_rename), hasFocus(), inDialog = true)
            pressInDialog(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(withText(R.string.settings_pictures_delete), hasFocus(), inDialog = true)
            pressInDialog(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(withText(R.string.settings_pictures_cancel), hasFocus(), inDialog = true)
            pressInDialog(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(withText(R.string.settings_pictures_delete), hasFocus(), inDialog = true)
            pressInDialog(KeyEvent.KEYCODE_DPAD_CENTER)

            waitUntil(row("sunset.jpg"), hasFocus())
            onView(row("old.jpg")).check(doesNotExist())
            assertEquals(listOf("beach.png", "sunset.jpg"), fake.pictures.map { it.name }.sorted())
        }
    }

    @Test
    fun aLongPressOfOkOnAPictureRenamesIt_refusingAnotherPicturesName() {
        launchWith(picture("beach.png", added = 1), picture("sunset.jpg", added = 2)).use {
            waitUntil(row(R.string.settings_pictures_no_picture), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(row("beach.png"), hasFocus())
            waitForFocus(BackgroundActivity::class.java)
            longPressOk()

            waitUntil(withText(R.string.settings_pictures_rename), hasFocus(), inDialog = true)
            pressInDialog(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(withId(R.id.settings_pictures_dialog_field), hasFocus(), inDialog = true)
            // Typed without the keyboard, which then goes, so the arrows reach the dialog. The
            // cursor put at the end, as after typing: on API 22 and 24 setting the text leaves it
            // before the end, and Down there only moves it to the end.
            onView(withId(R.id.settings_pictures_dialog_field)).inRoot(isDialog()).perform(replaceText("sunset.jpg"), closeSoftKeyboard())
            pressInDialog(KeyEvent.KEYCODE_MOVE_END)
            pressInDialog(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(withText(R.string.settings_pictures_rename), hasFocus(), inDialog = true)
            pressInDialog(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(withId(R.id.settings_pictures_dialog_text), withText(R.string.settings_pictures_folder_name_taken), inDialog = true)

            // Its new name moves it after sunset.jpg, the focus with it.
            onView(withId(R.id.settings_pictures_dialog_field)).inRoot(isDialog()).perform(replaceText("waves.png"), pressImeActionButton())
            waitUntil(row("waves.png"), hasFocus())
            assertEquals(listOf("sunset.jpg", "waves.png"), fake.pictures.map { it.name }.sorted())
        }
    }

    @Test
    fun rightOnAnOptionGoesToThePictures_okListsItsValues_okKeepsOne_backTheOneBefore() {
        val wide = picture("wide.jpg", added = 1, width = 6000, height = 1000)
        launchWith(wide, background = Background(wide.id)).use {
            waitUntil(row("wide.jpg"), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_LEFT)
            waitUntil(option(R.string.settings_appearance_fit, R.string.settings_appearance_fill), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            waitUntil(withId(R.id.settings_background_list), hasFocus())   // the row beside Fit
            onView(option(R.string.settings_appearance_fit, R.string.settings_appearance_fill)).check(matches(isDisplayed()))

            press(KeyEvent.KEYCODE_DPAD_LEFT)
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(option(R.string.settings_appearance_position, R.string.settings_appearance_center), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(withText(R.string.settings_appearance_center), hasFocus(), inList = true)
            pressInList(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(withText(R.string.settings_appearance_left), hasFocus(), inList = true)
            pressInList(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(option(R.string.settings_appearance_position, R.string.settings_appearance_left), hasFocus())

            press(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(withText(R.string.settings_appearance_left), hasFocus(), inList = true)
            pressInList(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(withText(R.string.settings_appearance_right), hasFocus(), inList = true)
            pressInList(KeyEvent.KEYCODE_BACK)
            waitUntil(option(R.string.settings_appearance_position, R.string.settings_appearance_left), hasFocus())
            assertEquals("nothing stored", Background(wide.id), appearance.appearance.background)
        }
    }

    @Test
    fun downPastTheLastEntryOfTheList_staysThere_notOnSave() {
        launchWith(picture("beach.png", added = 1), picture("sunset.jpg", added = 2)).use {
            waitUntil(row(R.string.settings_pictures_no_picture), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(row("beach.png"), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(row("sunset.jpg"), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(row(R.string.settings_pictures_add_from_folder), hasFocus())

            press(KeyEvent.KEYCODE_DPAD_DOWN)

            waitUntil(row(R.string.settings_pictures_add_from_folder), hasFocus())
            onView(withId(R.id.settings_background_save)).check(matches(not(hasFocus())))
            onView(withId(R.id.settings_background_delete_all)).check(matches(not(hasFocus())))
        }
    }

    @Test
    fun downPastTheLastPictureOfAddFromFolder_staysThere_notOnAddAll() {
        useTheTestsFolders()
        dropFolder.mkdirs()
        InstrumentationAssets.copy("pictures/baseline.jpg", File(dropFolder, "holiday.jpg"))
        InstrumentationAssets.copy("pictures/progressive.jpg", File(dropFolder, "logo.jpg"))

        ActivityScenario.launch(BackgroundActivity::class.java).use {
            waitUntil(row(R.string.settings_pictures_no_picture), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(row(R.string.settings_pictures_add_from_folder), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil(withText(containsString("logo.jpg")), isDisplayed())
            waitUntil(withId(R.id.settings_pictures_folder_list), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_DOWN)

            press(KeyEvent.KEYCODE_DPAD_DOWN)

            waitUntil(withId(R.id.settings_pictures_folder_list), hasFocus())
            onView(withId(R.id.settings_pictures_folder_add_all)).check(matches(not(hasFocus())))
        }
    }

    @Test
    fun addFromFolder_addsAPictureFromTheDropFolder_andChoosesIt() {
        useTheTestsFolders()
        dropFolder.mkdirs()
        InstrumentationAssets.copy("pictures/baseline.jpg", File(dropFolder, "holiday.jpg"))

        ActivityScenario.launch(BackgroundActivity::class.java).use {
            waitUntil(row(R.string.settings_pictures_no_picture), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(row(R.string.settings_pictures_add_from_folder), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)

            waitUntil(withText(containsString("holiday.jpg")), isDisplayed())
            waitUntil(withId(R.id.settings_pictures_folder_list), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)

            waitUntilDestroyed(AddFromFolderActivity::class.java)   // it closes once the picture is added
            waitUntil(row("holiday.jpg"), hasFocus())
            waitUntil(allOf(withId(R.id.settings_background_chosen), isDisplayed()), isDisplayed())
        }
    }

    @Test
    fun backFromAddFromFolder_leavesTheFocusOnIt() {
        val sunset = picture("sunset.jpg", added = 2)
        launchWith(picture("beach.png", added = 1), sunset, background = Background(sunset.id)).use { scenario ->
            openAddFromFolder(scenario)
            press(KeyEvent.KEYCODE_BACK)

            waitUntilDestroyed(AddFromFolderActivity::class.java)
            waitUntil(row(R.string.settings_pictures_add_from_folder), hasFocus())
            onView(allOf(withId(R.id.settings_background_chosen), isDisplayed())).check(matches(isDescendantOfA(row("sunset.jpg"))))
        }
    }

    @Test
    fun aPictureDeletedWhileAddFromFolderShows_backLeavesTheFocusOnAddFromFolder() {
        val beach = picture("beach.png", added = 1)
        val sunset = picture("sunset.jpg", added = 2)
        launchWith(beach, sunset, background = Background(sunset.id)).use { scenario ->
            openAddFromFolder(scenario)
            // As another screen would: the editor reads the library again as it comes back.
            InstrumentationRegistry.getInstrumentation().runOnMainSync { fake.delete(setOf(beach.id)) }
            press(KeyEvent.KEYCODE_BACK)

            waitUntilDestroyed(AddFromFolderActivity::class.java)
            waitUntil(row(R.string.settings_pictures_add_from_folder), hasFocus())
            onView(row("beach.png")).check(doesNotExist())
        }
    }

    /**
     * From the chosen picture, the last of two by name, down to Add from folder, and OK on it: its list
     * shows, with the drop folder's picture, and the editor is stopped under it. Android stops it
     * only once Add from folder is idle, up to 10 s later; until then Back finds the editor merely
     * paused, which it comes back from without reading the library again.
     */
    private fun openAddFromFolder(scenario: ActivityScenario<BackgroundActivity>) {
        dropFolder.mkdirs()
        InstrumentationAssets.copy("pictures/baseline.jpg", File(dropFolder, "holiday.jpg"))
        waitUntil(row("sunset.jpg"), hasFocus())
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        waitUntil(row(R.string.settings_pictures_add_from_folder), hasFocus())
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitUntil(withText(containsString("holiday.jpg")), isDisplayed())
        waitUntil(withId(R.id.settings_pictures_folder_list), hasFocus())
        waitUntilStopped(scenario)
    }

    /** Waits until the editor is stopped, under another screen. */
    private fun waitUntilStopped(scenario: ActivityScenario<BackgroundActivity>) {
        val deadline = SystemClock.uptimeMillis() + STOPPED_TIMEOUT_MS
        while (scenario.state != Lifecycle.State.CREATED) {
            if (SystemClock.uptimeMillis() > deadline) {
                throw AssertionError("the editor wasn't stopped in ${STOPPED_TIMEOUT_MS / 1000} s: it's ${scenario.state}").also(FailureCapture::dump)
            }
            SystemClock.sleep(50)
        }
    }

    @Test
    fun deleteAll_besideSave_deletesEveryPictureAfterAsking_cancelFirst() {
        val sunset = picture("sunset.jpg", added = 2)
        launchWith(picture("beach.png", added = 1), sunset, background = Background(sunset.id)).use {
            waitUntil(row("sunset.jpg"), hasFocus())
            // Left, to the options, and down them to Save; Right, beside it, Delete all.
            press(KeyEvent.KEYCODE_DPAD_LEFT)
            repeat(5) { if (!hasFocus(withId(R.id.settings_background_save))) press(KeyEvent.KEYCODE_DPAD_DOWN) }
            waitUntil(withId(R.id.settings_background_save), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            waitUntil(withId(R.id.settings_background_delete_all), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)

            waitUntil(withText(R.string.settings_pictures_cancel), hasFocus(), inDialog = true)
            pressInDialog(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(withText(R.string.settings_pictures_delete), hasFocus(), inDialog = true)
            pressInDialog(KeyEvent.KEYCODE_DPAD_CENTER)

            waitUntil(withId(R.id.settings_background_save), hasFocus())
            assertEquals(emptyList<Any>(), fake.pictures)
            assertEquals("the background without its picture, at once", null, appearance.appearance.background.picture)
        }
    }

    @Test
    fun addAll_addsEveryPictureOfTheDropFolder_thenFocusesTheFirstOfThem() {
        useTheTestsFolders()
        dropFolder.mkdirs()
        InstrumentationAssets.copy("pictures/baseline.jpg", File(dropFolder, "holiday.jpg"))
        InstrumentationAssets.copy("pictures/progressive.jpg", File(dropFolder, "logo.jpg"))

        ActivityScenario.launch(BackgroundActivity::class.java).use {
            waitUntil(row(R.string.settings_pictures_no_picture), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            waitUntil(row(R.string.settings_pictures_add_from_folder), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)

            waitUntil(withText(containsString("logo.jpg")), isDisplayed())
            waitUntil(withId(R.id.settings_pictures_folder_list), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_RIGHT)
            waitUntil(withId(R.id.settings_pictures_folder_add_all), hasFocus())
            press(KeyEvent.KEYCODE_DPAD_CENTER)

            val added = application.resources.getQuantityString(R.plurals.settings_received_saved_count, 2, 2)
            waitUntil(withText(added), isDisplayed(), inDialog = true)
            waitUntil(withText(R.string.settings_pictures_ok), hasFocus(), inDialog = true)
            pressInDialog(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntilDestroyed(AddFromFolderActivity::class.java)

            // The first of them by name focused; the background still No picture.
            waitUntil(row("holiday.jpg"), hasFocus())
            onView(row("logo.jpg")).check(matches(isDisplayed()))
            onView(allOf(withId(R.id.settings_background_chosen), isDisplayed())).check(matches(isDescendantOfA(row(R.string.settings_pictures_no_picture))))
        }
    }

    private fun hasFocus(view: Matcher<View>): Boolean = matchesNow(view, hasFocus())

    private companion object {
        const val TIMEOUT_MS = 10_000L

        /** Android's 10 s for a screen to go idle before it stops the one under it anyway, and a margin. */
        const val STOPPED_TIMEOUT_MS = 20_000L
    }
}

/** The test package's assets. */
private object InstrumentationAssets {
    fun copy(asset: String, to: File) {
        InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use { input ->
            to.outputStream().use { input.copyTo(it) }
        }
    }
}
