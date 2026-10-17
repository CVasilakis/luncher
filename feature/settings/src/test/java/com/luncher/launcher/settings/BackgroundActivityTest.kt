package com.luncher.launcher.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.FakePictures.Companion.picture
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.wallpaper.Wallpaper
import com.luncher.domain.wallpaper.WallpaperDrawing
import com.luncher.launcher.testing.TV_1080P
import com.luncher.launcher.ui.WorkingDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.time.Duration
import com.luncher.launcher.ui.R as UiR

/** The background editor: the pictures to choose from, the draft, Save, deleting and adding pictures. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class BackgroundActivityTest {

    private val application = RuntimeEnvironment.getApplication() as SettingsTestApplication

    private val beach = picture("beach.png", added = 2)
    private val sunset = picture("sunset.jpg", added = 3)
    private val old = picture("old.jpg", added = 1)

    private lateinit var graph: TestPicturesGraph

    /** A library of [pictures], the background [background] as stored. */
    private fun graph(vararg pictures: Picture, background: Background = Background()): TestPicturesGraph {
        graph = TestPicturesGraph(FakePictures(*pictures))
        graph.appearanceSettings.appearance = Appearance(background = background)
        application.graph = graph
        return graph
    }

    // A remote's keys, not a touchscreen: the focus shows only out of touch mode, where Robolectric starts.
    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun start(intent: Intent? = null): ActivityController<BackgroundActivity> {
        val controller = Robolectric.buildActivity(BackgroundActivity::class.java, intent).setup()
        idle()
        return controller
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** The background thread's jobs run, and their results shown. */
    private fun drawInTheBackground() {
        graph.jobs.runAll()
        idle()
    }

    private val stored get() = graph.appearanceSettings.appearance.background

    private fun Activity.listRows(): List<View> {
        val list = findViewById<LinearLayout>(R.id.settings_background_list)
        return (0 until list.childCount).map(list::getChildAt).filter { it.findViewById<View>(R.id.settings_background_name) != null }
    }

    private fun View.name() = findViewById<TextView>(R.id.settings_background_name).text.toString()

    private fun View.chosen() = findViewById<View>(R.id.settings_background_chosen).visibility == View.VISIBLE

    /** The list's names, the chosen one ending in " ✓". */
    private fun Activity.list(): List<String> = listRows().map { if (it.chosen()) "${it.name()} ✓" else it.name() }

    private fun Activity.row(name: String) = listRows().single { it.name() == name }

    /** The name of the row that has the focus. Not currentFocus: under Robolectric the window never gets focus. */
    private fun Activity.focused() = window.decorView.findFocus()?.findViewById<TextView>(R.id.settings_background_name)?.text?.toString()

    private fun Activity.hint() = findViewById<TextView>(R.id.settings_background_hint).text.toString()

    private fun Activity.option(label: String): View {
        val options = findViewById<LinearLayout>(R.id.settings_background_options)
        return (0 until options.childCount).map(options::getChildAt).single { it.findViewById<TextView>(R.id.settings_option_label).text.toString() == label }
    }

    private fun Activity.value(label: String) = option(label).findViewById<TextView>(R.id.settings_option_value).text.toString()

    private fun Activity.step(label: String) {
        assertTrue("$label can't take the focus", option(label).requestFocus())
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    private fun Activity.ok(name: String) {
        row(name).performClick()
        idle()
    }

    private fun Activity.save() {
        findViewById<View>(R.id.settings_background_save).performClick()
        idle()
    }

    /** A dialog showing: its title, text and entries; the spinner window's message as its title. */
    private fun dialog(): Triple<String, String, List<TextView>>? {
        val dialog = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing } ?: return null
        dialog.findViewById<TextView>(UiR.id.working_message)?.let { return Triple(it.text.toString(), "", emptyList()) }
        val entries = dialog.findViewById<LinearLayout>(R.id.settings_pictures_dialog_entries)
        return Triple(
            dialog.findViewById<TextView>(R.id.settings_pictures_dialog_title).text.toString(),
            dialog.findViewById<TextView>(R.id.settings_pictures_dialog_text).text.toString(),
            (0 until entries.childCount).map { entries.getChildAt(it) as TextView },
        )
    }

    private fun answer(label: String) {
        dialog()!!.third.single { it.text == label }.performClick()
        idle()
    }

    @Test
    fun `lists No picture, the pictures newest first, then Add from folder, the stored one chosen`() {
        graph(old, beach, sunset, background = Background(beach.id))

        val activity = start().get()

        assertEquals(listOf("No picture", "sunset.jpg", "beach.png ✓", "old.jpg", "Add from folder"), activity.list())
        assertEquals("Hold OK on a picture to delete it", activity.hint())
        assertEquals("the chosen one focused", "beach.png", activity.focused())
    }

    @Test
    fun `without a picture, No picture is chosen, and the options are dimmed and passed by`() {
        graph(sunset)

        val activity = start().get()

        assertEquals(listOf("No picture ✓", "sunset.jpg", "Add from folder"), activity.list())
        for (label in listOf("Fit", "Position", "Dim", "Blur")) assertFalse(label, activity.option(label).isFocusable)
        assertTrue(activity.findViewById<View>(R.id.settings_background_save).isFocusable)
    }

    @Test
    fun `while the library is empty, says how to add a picture`() {
        graph()

        val activity = start().get()

        assertEquals(listOf("No picture ✓", "Add from folder"), activity.list())
        val list = activity.findViewById<LinearLayout>(R.id.settings_background_list)
        val howTo = list.getChildAt(list.childCount - 1) as TextView
        assertTrue(howTo.text.contains("Android/media/${activity.packageName}"))
    }

    @Test
    fun `OK on a picture and the options change the draft and the preview, and store nothing`() {
        graph(beach, sunset, background = Background(beach.id))
        val activity = start().get()

        activity.ok("sunset.jpg")
        activity.step("Dim")
        activity.step("Blur")

        assertEquals(listOf("No picture", "sunset.jpg ✓", "beach.png", "Add from folder"), activity.list())
        assertEquals("20 %", activity.value("Dim"))
        assertEquals("Light", activity.value("Blur"))
        assertEquals(Background(sunset.id, dim = 20, blur = com.luncher.domain.pictures.Blur.LIGHT), activity.findViewById<AppearancePreviewView>(R.id.settings_background_preview).appearance.background)
        assertEquals("nothing stored", Background(beach.id), stored)
    }

    @Test
    fun `stretched, the position is dimmed`() {
        graph(sunset, background = Background(sunset.id, fit = Fit.STRETCH))

        val activity = start().get()

        assertFalse(activity.option("Position").isFocusable)
        assertTrue(activity.option("Fit").isFocusable)
    }

    @Test
    fun `Back leaves the background as it was`() {
        graph(beach, sunset, background = Background(beach.id))
        val activity = start().get()
        activity.ok("sunset.jpg")
        activity.step("Dim")

        @Suppress("DEPRECATION")
        activity.onBackPressed()

        assertTrue(activity.isFinishing)
        assertEquals(Background(beach.id), stored)
        assertEquals(0, graph.storedWallpaper.stored.size)
    }

    @Test
    fun `Save with nothing changed closes at once`() {
        graph(sunset, background = Background(sunset.id))
        val activity = start().get()

        activity.save()

        assertTrue(activity.isFinishing)
        assertNull(dialog())
        graph.jobs.runAll()
        assertEquals("no wallpaper drawn", 0, graph.storedWallpaper.stored.size)
    }

    @Test
    fun `Save with no picture stores it at once, deletes the wallpaper kept, and closes`() {
        graph(sunset, background = Background(sunset.id, dim = 20))
        graph.storedWallpaper.kept = WallpaperDrawing(sunset.id, PictureStyle(dim = 20), 1920, 1080) to sample("kept", 4, 4)
        val activity = start().get()

        activity.ok("No picture")
        activity.save()

        assertTrue(activity.isFinishing)
        assertNull(dialog())
        assertEquals(Background(dim = 20), stored)
        drawInTheBackground()
        assertNull(graph.storedWallpaper.kept)
    }

    @Test
    fun `Save draws the wallpaper while a window says so, stores it once drawn, then closes`() {
        graph(beach, sunset, background = Background(beach.id))
        val activity = start().get()
        activity.ok("sunset.jpg")

        activity.save()
        assertEquals("Applying effects…", dialog()!!.first)
        assertEquals(Wallpaper.State.Drawing(com.luncher.domain.wallpaper.DrawingCause.SAVING), graph.wallpaper.state)
        assertEquals("not stored before it's drawn", Background(beach.id), stored)
        assertFalse(activity.isFinishing)

        drawInTheBackground()
        assertEquals(Background(sunset.id), stored)
        assertEquals(WallpaperDrawing(sunset.id, PictureStyle(), 1920, 1080), graph.storedWallpaper.kept?.first)
        assertFalse("the window shows half a second at least", activity.isFinishing)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WorkingDialog.SHORTEST))
        assertNull(dialog())
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `the window over Save takes every key, Back too`() {
        graph(sunset)
        val activity = start().get()
        activity.ok("sunset.jpg")
        activity.save()
        val working = ShadowDialog.getLatestDialog()

        @Suppress("DEPRECATION")   // what Back does to a dialog without predictive Back
        working.onBackPressed()

        assertTrue(working.isShowing)
        assertFalse(activity.isFinishing)
    }

    @Test
    fun `a long press of OK on a picture asks to delete it, Cancel first, and Delete deletes it`() {
        graph(beach, sunset)
        val activity = start().get()

        activity.row("beach.png").performLongClick()
        idle()
        val (title, _, entries) = dialog()!!
        assertEquals("Delete beach.png?", title)
        assertEquals(listOf("Cancel", "Delete"), entries.map { it.text.toString() })
        answer("Delete")

        assertEquals(listOf("No picture ✓", "sunset.jpg", "Add from folder"), activity.list())
        assertEquals(listOf(sunset), graph.library.pictures)
    }

    @Test
    fun `deleting the wallpaper's picture says so, and leaves no wallpaper at once`() {
        graph(beach, sunset, background = Background(beach.id, dim = 40))
        graph.storedWallpaper.kept = WallpaperDrawing(beach.id, PictureStyle(dim = 40), 1920, 1080) to sample("kept", 4, 4)
        val activity = start().get()

        activity.row("beach.png").performLongClick()
        idle()
        assertTrue(dialog()!!.second.contains("It's the wallpaper"))
        answer("Delete")

        assertEquals(Background(dim = 40), stored)
        assertEquals(listOf("No picture ✓", "sunset.jpg", "Add from folder"), activity.list())
        drawInTheBackground()
        assertNull(graph.storedWallpaper.kept)
    }

    @Test
    fun `Cancel deletes nothing`() {
        graph(beach)
        val activity = start().get()

        activity.row("beach.png").performLongClick()
        idle()
        answer("Cancel")

        assertEquals(listOf(beach), graph.library.pictures)
    }

    @Test
    fun `Add from folder opens the drop folders, and the picture it returns is added, chosen and focused`() {
        val added = picture("holiday.jpg", added = 9)
        val source = Uri.parse("file:///storage/emulated/0/Android/media/x/holiday.jpg")
        graph(sunset)
        graph.library.results[source] = ImportResult.Added(added)
        graph.library.answerLater = true
        val activity = start().get()

        activity.ok("Add from folder")
        assertEquals(AddFromFolderActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        @Suppress("DEPRECATION")
        shadowOf(activity).receiveResult(Intent(activity, AddFromFolderActivity::class.java), Activity.RESULT_OK, Intent().setData(source))
        assertEquals("Adding a picture…", activity.hint())
        graph.library.finishAdding()
        idle()

        assertEquals(listOf("No picture", "holiday.jpg ✓", "sunset.jpg", "Add from folder"), activity.list())
        assertEquals("holiday.jpg", activity.focused())
        assertEquals("Hold OK on a picture to delete it", activity.hint())
    }

    @Test
    fun `opened with a picture just received, starts with it chosen, not saved`() {
        graph(beach, sunset, background = Background(beach.id, dim = 20))

        val activity = start(Intent().putExtra(BackgroundActivity.EXTRA_PICTURE, sunset.id.value)).get()

        assertEquals(listOf("No picture", "sunset.jpg ✓", "beach.png", "Add from folder"), activity.list())
        assertEquals("the stored options kept", "20 %", activity.value("Dim"))
        assertEquals(Background(beach.id, dim = 20), stored)
    }

    @Test
    fun `shows the kept thumbnails at once, and draws the others in the background`() {
        graph(beach, sunset)
        graph.pictureImages.cache[beach.id to PictureStyle()] = sample("kept", 192, 108)

        val activity = start().get()
        assertTrue((activity.row("beach.png").findViewById<ImageView>(R.id.settings_background_thumbnail)).drawable != null)
        assertNull(activity.row("sunset.jpg").findViewById<ImageView>(R.id.settings_background_thumbnail).drawable)

        drawInTheBackground()

        assertTrue(activity.row("sunset.jpg").findViewById<ImageView>(R.id.settings_background_thumbnail).drawable != null)
        assertTrue(graph.pictureImages.rendered.any { it.first == sunset.id })
    }

    @Test
    fun `the preview shows the draft's wallpaper once it's drawn, the latest one`() {
        graph(sunset, background = Background(sunset.id))
        val activity = start().get()
        activity.step("Dim")
        activity.step("Dim")

        drawInTheBackground()

        val drawn = graph.pictureImages.rendered.filter { it.first == sunset.id && it.second != PictureStyle() }.map { it.second.dim }
        assertEquals(listOf(40), drawn)
    }

    @Test
    fun `after a configuration change, the draft stays`() {
        graph(beach, sunset, background = Background(beach.id))
        val controller = start()
        controller.get().ok("sunset.jpg")
        controller.get().step("Dim")

        RuntimeEnvironment.setQualifiers("+tvdpi")
        controller.configurationChange()
        idle()

        assertEquals(listOf("No picture", "sunset.jpg ✓", "beach.png", "Add from folder"), controller.get().list())
        assertEquals("20 %", controller.get().value("Dim"))
        assertEquals(Background(beach.id), stored)
    }

    @Test
    fun `follows the library while it shows, a picture deleted elsewhere leaving the list`() {
        graph(beach, sunset)
        val activity = start().get()

        graph.library.delete(setOf(beach.id))

        assertEquals(listOf("No picture ✓", "sunset.jpg", "Add from folder"), activity.list())
    }
}
