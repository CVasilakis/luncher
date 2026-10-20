package com.luncher.launcher.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.FakePictures.Companion.picture
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.wallpaper.Wallpaper
import com.luncher.domain.wallpaper.WallpaperDrawing
import com.luncher.launcher.testing.TV_1080P
import com.luncher.launcher.ui.WorkingDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // Wider and taller than the 1080p screen: filled, cut at the sides, or at the top and bottom.
    private val panorama = picture("panorama.jpg", added = 4, width = 6000, height = 1000)
    private val portrait = picture("portrait.jpg", added = 5, width = 3024, height = 4032)

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

    /** OK on [label]'s row: the list of its values, which shows. */
    private fun Activity.open(label: String): PopupWindow {
        assertTrue("$label can't take the focus", option(label).requestFocus())
        option(label).performClick()
        idle()
        val list = shadowOf(application).latestPopupWindow
        assertTrue("$label's list shows", list.isShowing)
        return list
    }

    private fun PopupWindow.entries(): List<TextView> {
        val list = contentView as LinearLayout
        return (0 until list.childCount).map { list.getChildAt(it) as TextView }
    }

    /** The entry of the list that has the focus. */
    private fun PopupWindow.focused() = entries().single { it.isFocused }.text.toString()

    /** The values [label]'s list offers, in order. */
    private fun Activity.values(label: String): List<String> {
        val list = open(label)
        val values = list.entries().map { it.text.toString() }
        list.dismiss()
        idle()
        return values
    }

    /** [label]'s next value, after its last the first, chosen in its list: focused, then OK. */
    private fun Activity.step(label: String) {
        val entries = open(label).entries()
        val next = entries[(entries.indexOfFirst { it.isFocused } + 1) % entries.size]
        next.requestFocus()
        next.performClick()
        idle()
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

    /** A long press of OK on [name]'s row, then [action] in what it offers. */
    private fun Activity.longPress(name: String, action: String) {
        row(name).performLongClick()
        idle()
        answer(action)
    }

    /** The rename dialog's field. */
    private fun field() = ShadowDialog.getLatestDialog().findViewById<EditText>(R.id.settings_pictures_dialog_field)

    /** [name] typed in the rename dialog, then Rename. */
    private fun rename(name: String) {
        field().setText(name)
        answer("Rename")
    }

    @Test
    fun `lists No picture, the pictures newest first, then Add from folder, the stored one chosen`() {
        graph(old, beach, sunset, background = Background(beach.id))

        val activity = start().get()

        assertEquals(listOf("No picture", "sunset.jpg", "beach.png ✓", "old.jpg", "Add from folder"), activity.list())
        assertEquals("Hold OK on a picture to rename or delete it", activity.hint())
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
    fun `Dim's values are said in words, as Blur's`() {
        graph(beach, background = Background(beach.id))
        val activity = start().get()

        assertEquals(listOf("Off", "Light", "Medium", "Strong"), activity.values("Dim"))
        assertEquals(listOf("Off", "Light", "Medium", "Strong"), activity.values("Blur"))
    }

    @Test
    fun `Left and Right on an option move the focus, to the pictures, and change nothing`() {
        graph(beach, background = Background(beach.id))
        val activity = start().get()
        val dim = activity.option("Dim")
        dim.requestFocus()

        for (key in listOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT)) {
            assertFalse(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key)))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
        }

        assertEquals("Off", activity.value("Dim"))
        assertTrue("a picture's row right of it", dim.focusSearch(View.FOCUS_RIGHT) in activity.listRows())
        assertNull("no list", shadowOf(activity.application).latestPopupWindow?.takeIf { it.isShowing })
    }

    @Test
    fun `OK on an option lists its values, the one it has focused and in the accent color`() {
        graph(beach, background = Background(beach.id, dim = 40))
        val activity = start().get()

        val list = activity.open("Dim")

        assertEquals("Medium", list.focused())
        val accent = activity.getColor(UiR.color.accent)
        assertEquals(listOf("Medium"), list.entries().filter { it.currentTextColor == accent }.map { it.text.toString() })
    }

    @Test
    fun `the preview shows the value with the focus in the list, and OK keeps it`() {
        graph(beach, background = Background(beach.id))
        val activity = start().get()
        val preview = activity.findViewById<AppearancePreviewView>(R.id.settings_background_preview)
        val list = activity.open("Blur")

        list.entries()[2].requestFocus()
        idle()
        assertEquals(com.luncher.domain.pictures.Blur.MEDIUM, preview.appearance.background.blur)
        assertEquals("Medium", activity.value("Blur"))
        list.entries()[2].performClick()
        idle()

        assertFalse(list.isShowing)
        assertEquals(com.luncher.domain.pictures.Blur.MEDIUM, preview.appearance.background.blur)
        assertTrue("the focus back on the option", activity.option("Blur").isFocused)
        assertEquals("nothing stored", Background(beach.id), stored)
    }

    @Test
    fun `Back closes the list, the value as before`() {
        graph(beach, background = Background(beach.id, dim = 20))
        val activity = start().get()
        val list = activity.open("Dim")
        list.entries()[3].requestFocus()
        idle()
        assertEquals("Strong", activity.value("Dim"))

        val window = list.contentView.rootView
        window.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        window.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        idle()

        assertFalse(list.isShowing)
        assertFalse("the editor stays", activity.isFinishing)
        assertEquals("Light", activity.value("Dim"))
        assertEquals(20, activity.findViewById<AppearancePreviewView>(R.id.settings_background_preview).appearance.background.dim)
    }

    @Test
    fun `OK on a picture and the options change the draft and the preview, and store nothing`() {
        graph(beach, sunset, background = Background(beach.id))
        val activity = start().get()

        activity.ok("sunset.jpg")
        activity.step("Dim")
        activity.step("Blur")

        assertEquals(listOf("No picture", "sunset.jpg ✓", "beach.png", "Add from folder"), activity.list())
        assertEquals("Light", activity.value("Dim"))
        assertEquals("Light", activity.value("Blur"))
        assertEquals(Background(sunset.id, dim = 20, blur = com.luncher.domain.pictures.Blur.LIGHT), activity.findViewById<AppearancePreviewView>(R.id.settings_background_preview).appearance.background)
        assertEquals("nothing stored", Background(beach.id), stored)
    }

    @Test
    fun `stretched, the position is dimmed`() {
        graph(panorama, background = Background(panorama.id, fit = Fit.STRETCH))

        val activity = start().get()

        assertFalse(activity.option("Position").isFocusable)
        assertTrue(activity.option("Fit").isFocusable)
    }

    @Test
    fun `a picture wider than the screen offers the middle, left and right`() {
        graph(panorama, background = Background(panorama.id))
        val activity = start().get()

        assertEquals(listOf("Center", "Left", "Right"), activity.values("Position"))
        assertTrue(activity.option("Fit").isFocusable)
    }

    @Test
    fun `a picture taller than the screen offers the middle, top and bottom`() {
        graph(portrait, background = Background(portrait.id))
        val activity = start().get()

        assertEquals(listOf("Center", "Top", "Bottom"), activity.values("Position"))
    }

    // The library's default picture is 1920 x 1080, as the screen: filling or stretched, it looks the same.
    @Test
    fun `a picture of the screen's shape dims the fit and the position, and the focus passes them by`() {
        graph(sunset, background = Background(sunset.id))
        val activity = start().get()

        for (label in listOf("Fit", "Position")) {
            assertFalse(label, activity.option(label).isFocusable)
            assertTrue(label, activity.option(label).alpha < 1f)
        }
        for (label in listOf("Dim", "Blur")) assertTrue(label, activity.option(label).isFocusable)
        val dimmed = setOf(activity.option("Fit"), activity.option("Position"))
        assertFalse("up from Dim", activity.option("Dim").focusSearch(View.FOCUS_UP) in dimmed)
        assertFalse("from the pictures", activity.row("No picture").focusSearch(View.FOCUS_LEFT) in dimmed)
        assertSame(activity.option("Blur"), activity.option("Dim").focusSearch(View.FOCUS_DOWN))
    }

    @Test
    fun `a picture of the screen's shape keeps a stored stretch, dimmed`() {
        graph(sunset, background = Background(sunset.id, fit = Fit.STRETCH))
        val activity = start().get()

        assertEquals("Stretch", activity.value("Fit"))
        assertFalse(activity.option("Fit").isFocusable)
    }

    @Test
    fun `a picture at the top, then a wide one chosen, shows and saves the middle`() {
        graph(portrait, panorama, background = Background(portrait.id))
        val activity = start().get()
        activity.step("Position")
        assertEquals("Top", activity.value("Position"))

        activity.ok("panorama.jpg")
        assertEquals("Center", activity.value("Position"))
        activity.ok("portrait.jpg")
        assertEquals("the middle kept, not the top again", "Center", activity.value("Position"))
        activity.ok("panorama.jpg")
        activity.save()
        drawInTheBackground()

        assertEquals(Background(panorama.id), stored)
    }

    @Test
    fun `opened with a position its picture doesn't offer, shows the middle, and Save without changes closes without drawing`() {
        graph(panorama, background = Background(panorama.id, position = PicturePosition.TOP, dim = 20))
        val activity = start().get()

        assertEquals("Center", activity.value("Position"))
        activity.save()

        assertTrue(activity.isFinishing)
        assertNull(dialog())
        graph.jobs.runAll()
        assertEquals("no wallpaper drawn", 0, graph.storedWallpaper.stored.size)
        assertEquals("nothing stored", Background(panorama.id, position = PicturePosition.TOP, dim = 20), stored)
    }

    @Test
    fun `opened with a picture received, starts with a position it offers`() {
        graph(portrait, panorama, background = Background(portrait.id, position = PicturePosition.BOTTOM))

        val activity = start(Intent().putExtra(BackgroundActivity.EXTRA_PICTURE, panorama.id.value)).get()

        assertEquals("Center", activity.value("Position"))
        assertEquals(PicturePosition.CENTER, activity.findViewById<AppearancePreviewView>(R.id.settings_background_preview).appearance.background.position)
    }

    @Test
    fun `a draft kept across a configuration change holds a position its picture offers`() {
        graph(portrait, panorama, background = Background(portrait.id))
        val controller = start()
        controller.get().step("Position")
        val state = android.os.Bundle()
        controller.saveInstanceState(state)
        assertEquals("Top", controller.get().value("Position"))

        // Its picture now cut at the sides: another library, the same draft.
        graph(panorama.copy(id = portrait.id, name = portrait.name))
        val restored = Robolectric.buildActivity(BackgroundActivity::class.java).setup(state).get()
        idle()

        assertEquals("Center", restored.value("Position"))
    }

    @Test
    fun `a picture added from the folder starts from a position it offers`() {
        val added = picture("holiday.jpg", added = 9, width = 6000, height = 1000)
        graph(portrait, background = Background(portrait.id, position = PicturePosition.BOTTOM))
        val activity = start().get()

        activity.ok("Add from folder")
        graph.library.pictures += added
        @Suppress("DEPRECATION")
        shadowOf(activity).receiveResult(
            Intent(activity, AddFromFolderActivity::class.java),
            Activity.RESULT_OK,
            Intent().putExtra(AddFromFolderActivity.EXTRA_ADDED, added.id.value),
        )
        idle()

        assertEquals("Center", activity.value("Position"))
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
        assertEquals("Processing background…", dialog()!!.first)
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
    fun `a long press of OK on a picture offers to rename or delete it, under its name`() {
        graph(beach, sunset)
        val activity = start().get()

        activity.row("beach.png").performLongClick()
        idle()

        val (title, text, entries) = dialog()!!
        assertEquals("beach.png", title)
        assertEquals("1920 × 1080 · 1.0 MB", text)
        assertEquals(listOf("Rename", "Delete"), entries.map { it.text.toString() })
        assertTrue(entries[0].isFocused)
    }

    @Test
    fun `Rename asks for the new name, the cursor after the old one, and the row takes it, keeping its place and thumbnail`() {
        graph(beach, sunset)
        graph.thumbnails.cache[beach.id to PictureStyle()] = sample("kept", 192, 108)
        val activity = start().get()
        activity.row("beach.png").requestFocus()
        val thumbnail = activity.row("beach.png").findViewById<ImageView>(R.id.settings_background_thumbnail).drawable

        activity.longPress("beach.png", "Rename")
        assertEquals("Rename picture", dialog()!!.first)
        assertEquals("beach.png", field().text.toString())
        assertEquals(9 to 9, field().selectionStart to field().selectionEnd)
        rename("  Sea  ")

        assertNull(dialog())
        assertEquals(listOf(beach.copy(name = "Sea"), sunset), graph.library.pictures)
        assertEquals(listOf("No picture ✓", "sunset.jpg", "Sea", "Add from folder"), activity.list())
        assertEquals("Sea", activity.focused())
        assertSame(thumbnail, activity.row("Sea").findViewById<ImageView>(R.id.settings_background_thumbnail).drawable)
        drawInTheBackground()
        assertTrue("nothing drawn again", graph.thumbnails.rendered.none { it.first == beach.id })
    }

    @Test
    fun `the keyboard's Done renames too`() {
        graph(beach)
        val activity = start().get()

        activity.longPress("beach.png", "Rename")
        field().setText("Sea")
        field().onEditorAction(EditorInfo.IME_ACTION_DONE)
        idle()

        assertNull(dialog())
        assertEquals(listOf("No picture ✓", "Sea", "Add from folder"), activity.list())
    }

    @Test
    fun `a name another picture has, or none, is refused, saying why, and the dialog stays`() {
        graph(beach, sunset)
        val activity = start().get()

        activity.longPress("beach.png", "Rename")
        rename("sunset.jpg")
        assertEquals("Name already used in Luncher's pictures", dialog()!!.second)
        rename(" ")
        assertEquals("Type a name", dialog()!!.second)

        assertEquals(listOf(beach, sunset), graph.library.pictures)
        answer("Cancel")
        assertNull(dialog())
        assertEquals(listOf("No picture ✓", "sunset.jpg", "beach.png", "Add from folder"), activity.list())
    }

    @Test
    fun `the wallpaper's picture renamed stays the background, chosen in the draft`() {
        graph(beach, background = Background(beach.id, dim = 40))
        val activity = start().get()

        activity.longPress("beach.png", "Rename")
        rename("Sea")

        assertEquals(Background(beach.id, dim = 40), stored)
        assertEquals(listOf("No picture", "Sea ✓", "Add from folder"), activity.list())
        activity.row("Sea").performLongClick()
        idle()
        assertEquals("the new name, not the one the row was made with", "Sea", dialog()!!.first)
    }

    @Test
    fun `Delete asks to delete it, Cancel first, and Delete deletes it`() {
        graph(beach, sunset)
        val activity = start().get()

        activity.longPress("beach.png", "Delete")
        val (title, text, entries) = dialog()!!
        assertEquals("Delete beach.png?", title)
        assertEquals("The picture is deleted from Luncher's pictures only.", text)
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

        activity.longPress("beach.png", "Delete")
        assertEquals(
            "Used as the background: the home screen will have no picture. The picture is deleted from Luncher's pictures only.",
            dialog()!!.second,
        )
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

        activity.longPress("beach.png", "Delete")
        answer("Cancel")

        assertEquals(listOf(beach), graph.library.pictures)
    }

    private fun Activity.deleteAll() = findViewById<View>(R.id.settings_background_delete_all)

    @Test
    fun `Delete all, beside Save, asks with how many, Cancel first, and deletes every picture`() {
        graph(beach, sunset, old)
        val activity = start().get()
        assertTrue(activity.deleteAll().isFocusable)

        activity.deleteAll().performClick()
        idle()
        val (title, text, entries) = dialog()!!
        assertEquals("Delete all pictures?", title)
        assertEquals("The 3 pictures are deleted from Luncher's pictures only.", text)
        assertEquals(listOf("Cancel", "Delete"), entries.map { it.text.toString() })
        assertTrue("Cancel focused", entries[0].isFocused)
        answer("Delete")

        assertEquals(emptyList<Picture>(), graph.library.pictures)
        assertEquals(listOf("No picture ✓", "Add from folder"), activity.list())
        assertFalse("dimmed and passed by, with none left", activity.deleteAll().isFocusable)
        assertTrue("the focus on Save", activity.findViewById<View>(R.id.settings_background_save).isFocused)
    }

    @Test
    fun `Delete all with the wallpaper's picture says so, and leaves no wallpaper at once`() {
        graph(beach, sunset, background = Background(beach.id, dim = 40))
        graph.storedWallpaper.kept = WallpaperDrawing(beach.id, PictureStyle(dim = 40), 1920, 1080) to sample("kept", 4, 4)
        val activity = start().get()

        activity.deleteAll().performClick()
        idle()
        assertEquals(
            "One is used as the background: the home screen will have no picture. The 2 pictures are deleted from Luncher's pictures only.",
            dialog()!!.second,
        )
        answer("Delete")

        assertEquals(Background(dim = 40), stored)
        drawInTheBackground()
        assertNull(graph.storedWallpaper.kept)
    }

    @Test
    fun `Delete all of one picture says it as for one`() {
        graph(beach)
        val activity = start().get()

        activity.deleteAll().performClick()
        idle()

        assertEquals("The picture is deleted from Luncher's pictures only.", dialog()!!.second)
    }

    @Test
    fun `Cancel on Delete all deletes nothing`() {
        graph(beach, sunset)
        val activity = start().get()

        activity.deleteAll().performClick()
        idle()
        answer("Cancel")

        assertEquals(listOf(beach, sunset), graph.library.pictures)
    }

    @Test
    fun `Delete all is dimmed and passed by while the library is empty`() {
        graph()

        val activity = start().get()

        assertFalse(activity.deleteAll().isFocusable)
        assertTrue(activity.deleteAll().alpha < 1f)
    }

    @Test
    fun `the pictures Add all added are focused, the first of them in the list, none chosen`() {
        val holiday = picture("holiday.jpg", added = 9)
        val logo = picture("logo.png", added = 10)
        graph(sunset, background = Background(sunset.id))
        val activity = start().get()

        activity.ok("Add from folder")
        graph.library.pictures += listOf(holiday, logo)   // what Add all added, in this order
        @Suppress("DEPRECATION")
        shadowOf(activity).receiveResult(
            Intent(activity, AddFromFolderActivity::class.java),
            Activity.RESULT_OK,
            Intent().putExtra(AddFromFolderActivity.EXTRA_ADDED_ALL, arrayOf(holiday.id.value, logo.id.value)),
        )
        idle()

        assertEquals(listOf("No picture", "logo.png", "holiday.jpg", "sunset.jpg ✓", "Add from folder"), activity.list())
        assertEquals("logo.png", activity.focused())
    }

    @Test
    fun `Add from folder opens the drop folders, and the picture it added is chosen and focused`() {
        val added = picture("holiday.jpg", added = 9)
        graph(sunset)
        val activity = start().get()

        activity.ok("Add from folder")
        assertEquals(AddFromFolderActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        graph.library.pictures += added   // what Add from folder added
        @Suppress("DEPRECATION")
        shadowOf(activity).receiveResult(
            Intent(activity, AddFromFolderActivity::class.java),
            Activity.RESULT_OK,
            Intent().putExtra(AddFromFolderActivity.EXTRA_ADDED, added.id.value),
        )
        idle()

        assertEquals(listOf("No picture", "holiday.jpg ✓", "sunset.jpg", "Add from folder"), activity.list())
        assertEquals("holiday.jpg", activity.focused())
        assertEquals("Hold OK on a picture to rename or delete it", activity.hint())
    }

    @Test
    fun `opened with a picture just received, starts with it chosen, not saved`() {
        graph(beach, sunset, background = Background(beach.id, dim = 20))

        val activity = start(Intent().putExtra(BackgroundActivity.EXTRA_PICTURE, sunset.id.value)).get()

        assertEquals(listOf("No picture", "sunset.jpg ✓", "beach.png", "Add from folder"), activity.list())
        assertEquals("the stored options kept", "Light", activity.value("Dim"))
        assertEquals(Background(beach.id, dim = 20), stored)
    }

    @Test
    fun `shows the kept thumbnails at once, and draws the others in the background`() {
        graph(beach, sunset)
        graph.thumbnails.cache[beach.id to PictureStyle()] = sample("kept", 192, 108)

        val activity = start().get()
        assertTrue((activity.row("beach.png").findViewById<ImageView>(R.id.settings_background_thumbnail)).drawable != null)
        assertNull(activity.row("sunset.jpg").findViewById<ImageView>(R.id.settings_background_thumbnail).drawable)

        drawInTheBackground()

        assertTrue(activity.row("sunset.jpg").findViewById<ImageView>(R.id.settings_background_thumbnail).drawable != null)
        assertTrue(graph.thumbnails.rendered.any { it.first == sunset.id })
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
        assertEquals("Light", controller.get().value("Dim"))
        assertEquals(Background(beach.id), stored)
    }

    @Test
    fun `follows the library while it shows, a picture deleted elsewhere leaving the list`() {
        graph(beach, sunset)
        val activity = start().get()

        graph.library.delete(setOf(beach.id))

        assertEquals(listOf("No picture ✓", "sunset.jpg", "Add from folder"), activity.list())
    }

    @Test
    fun `prepares the pictures that aren't at its start, while a window says which of how many, then draws their thumbnails`() {
        graph(beach, sunset, old)
        graph.preparedPictures.missing += setOf(beach.id, old.id)
        val activity = start().get()

        assertEquals("Preparing picture 1 of 2…", dialog()!!.first)
        assertEquals(emptyList<Any>(), graph.thumbnails.rendered.filter { it.first == beach.id || it.first == old.id })

        graph.jobs.runAll()
        idle()
        assertEquals(setOf(beach.id, old.id), graph.preparedPictures.prepared.toSet())
        assertTrue("the window shows half a second at least", dialog() != null)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WorkingDialog.SHORTEST))
        assertNull(dialog())
        drawInTheBackground()
        for (picture in listOf(beach, sunset, old)) {
            assertTrue(picture.name, activity.row(picture.name).findViewById<ImageView>(R.id.settings_background_thumbnail).drawable != null)
        }
    }

    @Test
    fun `says which picture of how many it prepares as each is done`() {
        graph(beach, sunset)
        graph.preparedPictures.missing += setOf(beach.id, sunset.id)
        start()

        graph.jobs.runNext()
        idle()

        assertEquals("Preparing picture 2 of 2…", dialog()!!.first)
    }

    @Test
    fun `says it prepares the picture, when there's one`() {
        graph(beach, sunset)
        graph.preparedPictures.missing += beach.id

        start()

        assertEquals("Preparing the picture…", dialog()!!.first)
    }

    @Test
    fun `shows no window when every picture is prepared`() {
        graph(beach, sunset)

        start()

        assertNull(dialog())
    }

    @Test
    fun `Cancel while it prepares closes the editor, nothing chosen, and stops the rest`() {
        graph(beach, sunset, background = Background(beach.id))
        graph.preparedPictures.missing += setOf(beach.id, sunset.id)
        val activity = start().get()

        val window = ShadowDialog.getLatestDialog()
        assertEquals("Cancel", window.findViewById<TextView>(UiR.id.working_cancel).text.toString())
        window.findViewById<TextView>(UiR.id.working_cancel).performClick()
        idle()

        assertTrue(activity.isFinishing)
        assertFalse(window.isShowing)
        graph.jobs.runAll()
        assertEquals(emptyList<Any>(), graph.preparedPictures.prepared)
        assertEquals(Background(beach.id), stored)
    }

    @Test
    fun `hidden while it prepares, it stops, and prepares what's left when it comes back`() {
        graph(beach, sunset)
        graph.preparedPictures.missing += setOf(beach.id, sunset.id)
        val controller = start()

        controller.pause().stop()
        assertNull(dialog())
        graph.jobs.runAll()
        assertEquals(emptyList<Any>(), graph.preparedPictures.prepared)

        controller.restart().start().resume()
        idle()
        assertEquals("Preparing picture 1 of 2…", dialog()!!.first)
        graph.jobs.runAll()
        idle()
        assertEquals(setOf(beach.id, sunset.id), graph.preparedPictures.prepared.toSet())
    }
}
