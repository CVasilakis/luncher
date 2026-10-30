package com.luncher.launcher.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.AppCustomization
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeAppCustomizations
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.LaunchableApp
import com.luncher.domain.apps.TileImage
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.appearance.Background
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.FakePictures.Companion.picture
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.wallpaper.Wallpaper
import com.luncher.domain.wallpaper.WallpaperDrawing
import com.luncher.launcher.testing.TV_1080P
import com.luncher.launcher.testing.bounds
import com.luncher.launcher.ui.WorkingDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
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

    /** What the user set for each app, the pictures on tiles among it: [graph] gives it. */
    private var customizations = FakeAppCustomizations()

    /**
     * A library of [pictures], the background [background] as stored; [apps] installed, those
     * [hidden] hidden, and the pictures [onTiles] on the tiles of apps, by name, installed or not.
     */
    private fun graph(
        vararg pictures: Picture,
        background: Background = Background(),
        apps: List<String> = emptyList(),
        hidden: List<String> = emptyList(),
        onTiles: Map<String, Picture> = emptyMap(),
    ): TestPicturesGraph {
        customizations = FakeAppCustomizations(onTiles.entries.associate { (app, picture) -> app(app).launchable to AppCustomization(image = TileImage(picture.id)) })
        graph = object : TestPicturesGraph(FakePictures(*pictures)) {
            override val installedApps = FakeInstalledApps(*apps.map { app(it) }.toTypedArray())
            override val appArrangements = FakeAppArrangements(AppArrangement(order = null, hidden = hidden.map { app(it).launchable }))
            override val appCustomizations = this@BackgroundActivityTest.customizations
        }
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

    private fun Activity.press(keyCode: Int) {
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    /** The background thread's jobs run, and their results shown. */
    private fun drawInTheBackground() {
        graph.jobs.runAll()
        idle()
    }

    private val stored get() = graph.appearanceSettings.appearance.background

    private fun Activity.listRows(): List<View> {
        val list = findViewById<LinearLayout>(R.id.settings_pictures_list)
        return (0 until list.childCount).map(list::getChildAt).filter { it.findViewById<View>(R.id.settings_pictures_name) != null }
    }

    private fun View.name() = findViewById<TextView>(R.id.settings_pictures_name).text.toString()

    private fun View.chosen() = findViewById<View>(R.id.settings_pictures_chosen).visibility == View.VISIBLE

    /** The list's names, the chosen one ending in " ✓". */
    private fun Activity.list(): List<String> = listRows().map { if (it.chosen()) "${it.name()} ✓" else it.name() }

    private fun Activity.row(name: String) = listRows().single { it.name() == name }

    /** The name of the row that has the focus. Not currentFocus: under Robolectric the window never gets focus. */
    private fun Activity.focused() = window.decorView.findFocus()?.findViewById<TextView>(R.id.settings_pictures_name)?.text?.toString()

    private fun Activity.hint() = findViewById<TextView>(R.id.settings_pictures_hint).text.toString()

    /** The options' fields, in order: two a row. */
    private fun Activity.options(): List<View> {
        val options = findViewById<LinearLayout>(R.id.settings_picture_editor_options)
        return (0 until options.childCount).map(options::getChildAt).flatMap { line -> (0 until (line as LinearLayout).childCount).map(line::getChildAt) }
    }

    private fun Activity.option(label: String): View =
        options().single { it.findViewById<TextView>(R.id.settings_option_label).text.toString() == label }

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

    private fun PopupWindow.entries(): List<View> {
        val list = contentView as LinearLayout
        return (0 until list.childCount).map { list.getChildAt(it) }
    }

    /** [keyCode] pressed and released on the focused value of [list]. */
    private fun press(list: PopupWindow, keyCode: Int) {
        val window = list.contentView.rootView
        window.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        window.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        idle()
    }

    /** A value's name, in an option's list. */
    private val View.text: CharSequence get() = findViewById<TextView>(R.id.settings_option_list_value).text

    /** Whether a value of an option's list is checked: the option's value. */
    private fun View.checked() = findViewById<View>(R.id.settings_option_list_check).visibility == View.VISIBLE

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
        findViewById<View>(R.id.settings_picture_editor_save).performClick()
        idle()
    }

    /** A dialog showing: its title, text and entries; the spinner window's message as its title. */
    private fun dialog(): Triple<String, String, List<TextView>>? {
        val dialog = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing } ?: return null
        dialog.findViewById<TextView>(UiR.id.working_message)?.let { return Triple(it.text.toString(), "", emptyList()) }
        val entries = dialog.findViewById<LinearLayout>(UiR.id.dialog_entries)
        return Triple(
            dialog.findViewById<TextView>(UiR.id.dialog_title).text.toString(),
            dialog.findViewById<TextView>(UiR.id.dialog_text).text.toString(),
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
    private fun field() = ShadowDialog.getLatestDialog().findViewById<EditText>(UiR.id.dialog_field)

    /** [name] typed in the rename dialog, then Rename. */
    private fun rename(name: String) {
        field().setText(name)
        answer("Rename")
    }

    // A picture's rename's keyboard: kept from sliding the editor up for the focused picture (NameDialog moves instead).
    @Test
    fun `the keyboard doesn't move the editor`() {
        val mode = start().get().window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST

        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING, mode)
    }

    @Test
    fun `lists No picture, the pictures by name, then Add from folder, the stored one chosen`() {
        graph(old, sunset, beach, background = Background(beach.id))

        val activity = start().get()

        assertEquals(listOf("No picture", "beach.png ✓", "old.jpg", "sunset.jpg", "Add from folder"), activity.list())
        assertEquals("Hold OK on a picture to rename or delete it", activity.hint())
        assertEquals("the chosen one focused", "beach.png", activity.focused())
    }

    @Test
    fun `without a picture, No picture is chosen, and the options are dimmed and passed by`() {
        graph(sunset)

        val activity = start().get()

        assertEquals(listOf("No picture ✓", "sunset.jpg", "Add from folder"), activity.list())
        for (label in listOf("Fit", "Position", "Dim", "Blur")) assertFalse(label, activity.option(label).isFocusable)
        assertTrue(activity.findViewById<View>(R.id.settings_picture_editor_save).isFocusable)
    }

    @Test
    fun `with the options and Delete all dimmed, Left from the pictures goes to Save, and Up from Save to the pictures`() {
        graph()

        val activity = start().get()

        val save = activity.findViewById<View>(R.id.settings_picture_editor_save)
        assertSame("not the scroll around the options", save, activity.row("No picture").focusSearch(View.FOCUS_LEFT))
        assertTrue("a picture's row, the nearest above", save.focusSearch(View.FOCUS_UP) in activity.listRows())
    }

    @Test
    fun `while the library is empty, says how to add a picture`() {
        graph()

        val activity = start().get()

        assertEquals(listOf("No picture ✓", "Add from folder"), activity.list())
        val list = activity.findViewById<LinearLayout>(R.id.settings_pictures_list)
        val path = list.getChildAt(list.childCount - 1).findViewById<TextView>(R.id.settings_pictures_how_path)
        assertEquals("Android/media/${activity.packageName}", path.text.toString())
    }

    @Test
    fun `how to add a picture is in the middle of the room below Add from folder`() {
        graph()

        val activity = start().get()

        val list = activity.findViewById<LinearLayout>(R.id.settings_pictures_list)
        val howTo = list.getChildAt(list.childCount - 1) as ViewGroup
        val add = list.getChildAt(list.childCount - 2)
        val room = list.height - list.paddingBottom - add.bottom
        val content = howTo.getChildAt(howTo.childCount - 1).bottom - howTo.getChildAt(0).top
        assertEquals(add.bottom, howTo.top)
        assertTrue("how to add a picture, $content px, in $room px", content < room)
        val above = howTo.top + howTo.getChildAt(0).top - add.bottom
        val below = list.height - list.paddingBottom - (howTo.top + howTo.getChildAt(howTo.childCount - 1).bottom)
        assertTrue("$above px above how to add a picture, $below px below", kotlin.math.abs(above - below) <= 1)
    }

    // Down to the bottom margin however few the pictures, as the appearance editor's options.
    @Test
    fun `the pictures' container reaches the bottom margin, with a single picture`() {
        graph(beach, background = Background(beach.id))

        val activity = start().get()

        val scroll = activity.findViewById<View>(R.id.settings_pictures_list_scroll)
        val container = scroll.parent as View
        val column = activity.findViewById<View>(R.id.settings_pictures_column)
        val rows = activity.findViewById<ViewGroup>(R.id.settings_pictures_list)
        val end = rows.getChildAt(rows.childCount - 1).bottom
        assertTrue("the pictures, $end px, fill their container", end < container.height)
        assertEquals(bounds(column).bottom - column.paddingBottom, bounds(container).bottom, 1f)
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
        graph(portrait, background = Background(portrait.id))
        val activity = start().get()
        val blur = activity.option("Blur")
        blur.requestFocus()

        for (key in listOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT)) {
            assertFalse(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key)))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
        }

        assertEquals("Off", activity.value("Blur"))
        assertSame("Position right of it, in the grid", activity.option("Position"), blur.focusSearch(View.FOCUS_RIGHT))
        assertTrue("a picture's row right of Position", activity.option("Position").focusSearch(View.FOCUS_RIGHT) in activity.listRows())
        assertNull("no list", shadowOf(activity.application).latestPopupWindow?.takeIf { it.isShowing })
    }

    @Test
    fun `Down past the end of the pictures stays there, not on an option, Save or Delete all, which Left still reaches`() {
        graph(beach, sunset, background = Background(beach.id))
        val activity = start().get()
        val list = activity.findViewById<LinearLayout>(R.id.settings_pictures_list)
        val last = list.getChildAt(list.childCount - 1)
        last.requestFocus()

        val below = FocusFinder.getInstance().findNextFocus(activity.window.decorView as ViewGroup, last, View.FOCUS_DOWN)

        assertTrue("Android alone leaves the list, for an option, Save or Delete all", below != null && below.parent !== list)
        assertSame(last, last.focusSearch(View.FOCUS_DOWN))
        val left = last.focusSearch(View.FOCUS_LEFT)
        assertTrue("an option, Save or Delete all left of it", left != null && left.parent !== list)
    }

    // The rows next to the focused one show whole, where there are: the next one shows before the focus gets there.
    // While the list slides, the list draws the focus where it stays, and the row it goes to looks
    // at rest: its own look would move with it.
    @Test
    fun `while the pictures slide, the focused one looks at rest until it's under the focus`() {
        graph(*Array(20) { picture("picture%02d.jpg".format(it), added = it.toLong()) })
        val activity = start().get()
        val scroll = activity.findViewById<ScrollView>(R.id.settings_pictures_list_scroll)
        val looper = shadowOf(Looper.getMainLooper())
        idle()
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        try {
            // Frame by frame: paused, a frame runs once per idle, however long.
            fun frames() = repeat(30) { looper.idleFor(Duration.ofMillis(16)) }
            while (!scroll.canScrollVertically(-1)) {
                activity.press(KeyEvent.KEYCODE_DPAD_DOWN)
                frames()
            }
            val focus = activity.window.decorView.findFocus().top - scroll.scrollY
            activity.press(KeyEvent.KEYCODE_DPAD_DOWN)
            val next = activity.window.decorView.findFocus()
            val start = next.top - scroll.scrollY
            assertTrue("the next picture below the focus", start > focus)
            repeat(30) { if (next.top - scroll.scrollY == start) looper.idleFor(Duration.ofMillis(16)) }

            assertTrue("the list on its way: ${next.top - scroll.scrollY}, from $start to $focus", next.top - scroll.scrollY in focus + 1 until start)
            assertFalse("the picture looks focused before it's under the focus", android.R.attr.state_focused in next.drawableState)

            frames()
            assertEquals("under the focus, where it stayed", focus, next.top - scroll.scrollY)
            assertTrue("the picture looks focused once there", android.R.attr.state_focused in next.drawableState)
        } finally {
            ShadowChoreographer.setPaused(false)
        }
    }

    @Test
    fun `the pictures scroll a row ahead of the focus, both ways, moving the focus or the list, never both`() {
        graph(*Array(20) { picture("picture%02d.jpg".format(it), added = it.toLong()) })
        val activity = start().get()
        val scroll = activity.findViewById<ScrollView>(R.id.settings_pictures_list_scroll)
        val list = activity.findViewById<LinearLayout>(R.id.settings_pictures_list)
        val rows = (0 until list.childCount).map(list::getChildAt)
        val pitch = rows[1].top - rows[0].top

        fun assertAhead(index: Int) {
            assertSame(rows[index], activity.window.decorView.findFocus())
            val shown = scroll.scrollY..scroll.scrollY + scroll.height
            for (i in (index - 1).coerceAtLeast(0)..(index + 1).coerceAtMost(rows.lastIndex)) {
                assertTrue("${rows[i].name()} cut, with ${rows[index].name()} focused", rows[i].top in shown && rows[i].bottom in shown)
            }
        }

        // One thing moves: the focus, or the list under it by a row, the focus where it was.
        fun pressAndCheck(keyCode: Int, index: Int) {
            val focused = activity.window.decorView.findFocus()
            val top = focused.top - scroll.scrollY
            val before = scroll.scrollY
            activity.press(keyCode)
            idle()
            val moved = rows[index].top - scroll.scrollY != top
            val slid = scroll.scrollY - before
            assertTrue("to ${rows[index].name()}: the focus moved ($moved) or the list slid ($slid px), one of them", moved != (slid != 0))
            if (slid != 0) assertEquals("the list slid by a row", pitch, kotlin.math.abs(slid))
            assertAhead(index)
        }

        assertTrue("the list scrolls", scroll.canScrollVertically(1))
        assertTrue("rows of one height", rows.all { it.height == rows[0].height })
        assertEquals("a window of whole rows", 0, (scroll.height - list.paddingTop - list.paddingBottom) % pitch)
        idle()
        assertAhead(0)
        for (index in 1..rows.lastIndex) pressAndCheck(KeyEvent.KEYCODE_DPAD_DOWN, index)
        assertFalse("Add from folder at the edge, no more below", scroll.canScrollVertically(1))
        for (index in rows.lastIndex - 1 downTo 0) pressAndCheck(KeyEvent.KEYCODE_DPAD_UP, index)
    }

    // As Add from folder's rows, without where: every picture here is in Luncher's own storage.
    // Native graphics, so the text is measured as on a TV.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a picture's name stays on one line, cut with an ellipsis where it doesn't fit`() {
        val long = picture("a holiday by the sea, the whole family on the beach at sunset, summer.jpg", added = 6)
        graph(beach, long)
        val activity = start().get()

        val name = activity.row(long.name).findViewById<TextView>(R.id.settings_pictures_name)
        assertEquals(1, name.layout.lineCount)
        assertTrue("\"${name.text}\" isn't cut", name.layout.getEllipsisCount(0) > 0)
        assertEquals(0, activity.row("beach.png").findViewById<TextView>(R.id.settings_pictures_name).layout.getEllipsisCount(0))
    }

    @Test
    fun `a picture's row says its size in px and its file's, No picture and Add from folder nothing`() {
        graph(portrait, background = Background(portrait.id))
        val activity = start().get()
        fun View.details() = findViewById<TextView>(R.id.settings_pictures_details)

        assertEquals("3024 × 4032 · 1.0 MB", activity.row("portrait.jpg").details().text.toString())
        assertEquals(View.VISIBLE, activity.row("portrait.jpg").details().visibility)
        assertEquals(View.GONE, activity.row("No picture").details().visibility)
        assertEquals(View.GONE, activity.row("Add from folder").details().visibility)
    }

    // A list's rows keep their size while focused; the fields, not in a list, zoom, as buttons do.
    @Test
    fun `the fields zoom while focused, the pictures' rows and an option's values don't`() {
        graph(portrait, background = Background(portrait.id))
        val activity = start().get()

        for (field in activity.options()) assertNotNull("a field zooms", field.stateListAnimator)
        for (row in activity.listRows()) assertNull("a picture's row doesn't zoom", row.stateListAnimator)
        val list = activity.open("Dim")
        for (entry in list.entries()) assertNull("an option's value doesn't zoom", entry.stateListAnimator)
        list.dismiss()
    }

    @Test
    fun `OK on an option lists its values, the one it has focused and checked`() {
        graph(beach, background = Background(beach.id, dim = 40))
        val activity = start().get()

        val list = activity.open("Dim")

        assertEquals("Medium", list.focused())
        assertEquals(listOf("Medium"), list.entries().filter { it.checked() }.map { it.text.toString() })
        assertTrue("the field shows its list open", activity.option("Dim").isActivated)
        list.dismiss()
        idle()
        assertFalse(activity.option("Dim").isActivated)
    }

    // As a dialog: a short fade in and out of Luncher's own, not none.
    @Test
    fun `an option's list comes and goes with a short fade`() {
        graph(beach, background = Background(beach.id))
        val list = start().get().open("Dim")

        assertEquals(UiR.style.Animation_Luncher_Panel, list.animationStyle)
        list.dismiss()
    }

    // Beside the field, so it covers neither the field nor the preview: a field at the start
    // (Blur, Dim) has its list over the other column, to its end, from its first value down, so
    // it covers what's below the field whole, not the preview above; a field at the end
    // (Position) over the pictures, its value level with it. Either moved up only as far as the
    // bottom margin needs.
    @Test
    fun `an option's list opens beside its field, a start field's from its first value, an end field's with its value level with the field`() {
        graph(portrait, background = Background(portrait.id, dim = 40, blur = com.luncher.domain.pictures.Blur.MEDIUM))
        val activity = start().get()
        val options = activity.findViewById<View>(R.id.settings_picture_editor_options)
        val optionsEnd = IntArray(2).also(options::getLocationInWindow)[0] + options.width
        val preview = activity.findViewById<View>(R.id.settings_background_preview)
        val previewBottom = IntArray(2).also(preview::getLocationInWindow)[1] + preview.height
        val margin = activity.resources.getDimensionPixelSize(R.dimen.settings_appearance_margin_vertical)

        // Each field, the value it has, and the value level with it.
        for ((label, value, level) in listOf(Triple("Blur", "Medium", "Off"), Triple("Dim", "Medium", "Off"), Triple("Position", "Center", "Center"))) {
            val list = activity.open(label)

            // Where the list's window is, on the editor's.
            val window = list.contentView.rootView.layoutParams as android.view.WindowManager.LayoutParams
            val field = activity.option(label)
            val fieldAt = IntArray(2).also(field::getLocationInWindow)
            val fieldEnd = fieldAt[0] + field.width
            assertTrue("$label's list at ${window.x}, its field ends at $fieldEnd", window.x >= fieldEnd)
            if (label != "Position") {
                assertTrue("$label's list at ${window.x}, over the other column (the options end at $optionsEnd)", window.x < optionsEnd)
                // Covering the other column whole, to the options' end.
                assertTrue("$label's list ends at ${window.x + list.width}, before the options' end at $optionsEnd", window.x + list.width >= optionsEnd)
                assertTrue("$label's list at ${window.y}, over the preview, which ends at $previewBottom", window.y >= previewBottom)
            }
            else assertTrue("Position's list at ${window.x}, beside the options (they end at $optionsEnd)", window.x >= optionsEnd)
            assertEquals(value, list.entries().single { it.checked() }.text.toString())
            assertEquals(value, list.focused())
            val leveled = list.entries().single { it.text.toString() == level }
            val leveledAt = IntArray(2).also(leveled::getLocationInWindow)
            val levelY = fieldAt[1] + field.height / 2 - leveledAt[1] - leveled.height / 2
            val lowest = activity.window.decorView.height - margin - list.contentView.height
            // Within a dp: halves of odd heights round to whole pixels.
            assertEquals("$label's list", minOf(levelY, lowest).toFloat(), window.y.toFloat(), activity.resources.displayMetrics.density)
            list.dismiss()
            idle()
        }
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

    // Left goes back toward the field: it keeps the value, as OK, which Back doesn't.
    @Test
    fun `Left closes the list, keeping the value with the focus, as OK`() {
        graph(panorama, background = Background(panorama.id, dim = 20))
        val activity = start().get()

        for (label in listOf("Dim", "Position")) {
            val list = activity.open(label)
            list.entries().last().requestFocus()
            idle()
            val focused = list.focused()
            press(list, KeyEvent.KEYCODE_DPAD_LEFT)

            assertFalse("$label's list closed", list.isShowing)
            assertEquals(focused, activity.value(label))
            assertTrue("the focus back on $label", activity.option(label).isFocused)
        }
        assertFalse("the editor stays", activity.isFinishing)
        assertEquals("nothing stored", Background(panorama.id, dim = 20), stored)
    }

    // The list is at the field's left there: Right goes back toward it, Left goes nowhere.
    @Test
    @Config(qualifiers = "ar-rEG-$TV_1080P")
    fun `in a right-to-left language, Right closes the list, keeping the value, and Left doesn't`() {
        graph(beach, background = Background(beach.id))
        val activity = start().get()
        val dim = activity.options()[2]
        assertTrue(dim.requestFocus())
        dim.performClick()
        idle()
        val list = shadowOf(application).latestPopupWindow
        list.entries()[2].requestFocus()
        idle()

        press(list, KeyEvent.KEYCODE_DPAD_LEFT)
        assertTrue("Left leaves the list open", list.isShowing)
        press(list, KeyEvent.KEYCODE_DPAD_RIGHT)

        assertFalse(list.isShowing)
        assertEquals(40, activity.findViewById<AppearancePreviewView>(R.id.settings_background_preview).appearance.background.dim)
    }

    @Test
    fun `OK on a picture and the options change the draft and the preview, and store nothing`() {
        graph(beach, sunset, background = Background(beach.id))
        val activity = start().get()

        activity.ok("sunset.jpg")
        activity.step("Dim")
        activity.step("Blur")

        assertEquals(listOf("No picture", "beach.png", "sunset.jpg ✓", "Add from folder"), activity.list())
        assertEquals("Light", activity.value("Dim"))
        assertEquals("Light", activity.value("Blur"))
        assertEquals(Background(sunset.id, dim = 20, blur = com.luncher.domain.pictures.Blur.LIGHT), activity.findViewById<AppearancePreviewView>(R.id.settings_background_preview).appearance.background)
        assertEquals("nothing stored", Background(beach.id), stored)
    }

    @Test
    fun `another picture chosen shows as it is, in the middle, neither dimmed nor blurred, the fit kept`() {
        // Tall, as the portrait: Top would move it too.
        val tower = picture("tower.jpg", added = 6, width = 3000, height = 4000)
        graph(portrait, tower, background = Background(portrait.id))
        val activity = start().get()
        activity.step("Position")
        activity.step("Dim")
        activity.step("Blur")
        activity.step("Fit")

        activity.ok("tower.jpg")

        assertEquals(Background(tower.id, Framing(Fit.STRETCH)), activity.findViewById<AppearancePreviewView>(R.id.settings_background_preview).appearance.background)
        assertEquals("Off", activity.value("Dim"))
        assertEquals("Off", activity.value("Blur"))
        assertEquals("Center", activity.value("Position"))
        assertEquals("Stretch", activity.value("Fit"))
    }

    @Test
    fun `the stored picture chosen again shows as it's stored, and Save then closes at once`() {
        val saved = Background(portrait.id, framing = Framing(position = PicturePosition.BOTTOM), dim = 40, blur = com.luncher.domain.pictures.Blur.MEDIUM)
        graph(portrait, beach, background = saved)
        val activity = start().get()

        activity.ok("beach.png")
        activity.step("Dim")
        activity.ok("portrait.jpg")

        assertEquals(saved, activity.findViewById<AppearancePreviewView>(R.id.settings_background_preview).appearance.background)
        activity.save()
        assertTrue(activity.isFinishing)
        graph.jobs.runAll()
        assertEquals("no wallpaper drawn", 0, graph.storedWallpaper.stored.size)
    }

    @Test
    fun `OK on the chosen picture keeps its options`() {
        graph(beach, sunset, background = Background(beach.id))
        val activity = start().get()
        activity.ok("sunset.jpg")
        activity.step("Dim")

        activity.ok("sunset.jpg")

        assertEquals("Light", activity.value("Dim"))
    }

    @Test
    fun `stretched, the position is dimmed`() {
        graph(panorama, background = Background(panorama.id, framing = Framing(Fit.STRETCH)))

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
        assertFalse("right from Blur", activity.option("Blur").focusSearch(View.FOCUS_RIGHT) in dimmed)
        assertFalse("right from Dim", activity.option("Dim").focusSearch(View.FOCUS_RIGHT) in dimmed)
        assertFalse("from the pictures", activity.row("No picture").focusSearch(View.FOCUS_LEFT) in dimmed)
        assertSame(activity.option("Dim"), activity.option("Blur").focusSearch(View.FOCUS_DOWN))
    }

    @Test
    fun `a picture of the screen's shape keeps a stored stretch, dimmed`() {
        graph(sunset, background = Background(sunset.id, framing = Framing(Fit.STRETCH)))
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
        assertEquals("the stored middle, not the draft's top", "Center", activity.value("Position"))
        activity.ok("panorama.jpg")
        activity.save()
        drawInTheBackground()

        assertEquals(Background(panorama.id), stored)
    }

    @Test
    fun `opened with a position its picture doesn't offer, shows the middle, and Save without changes closes without drawing`() {
        graph(panorama, background = Background(panorama.id, framing = Framing(position = PicturePosition.TOP), dim = 20))
        val activity = start().get()

        assertEquals("Center", activity.value("Position"))
        activity.save()

        assertTrue(activity.isFinishing)
        assertNull(dialog())
        graph.jobs.runAll()
        assertEquals("no wallpaper drawn", 0, graph.storedWallpaper.stored.size)
        assertEquals("nothing stored", Background(panorama.id, framing = Framing(position = PicturePosition.TOP), dim = 20), stored)
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
        graph(portrait, background = Background(portrait.id, framing = Framing(position = PicturePosition.BOTTOM)))
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

    // The changes would be lost: asked first, Cancel focused, so a repeated Back loses nothing.
    @Test
    fun `Back with the draft changed asks first, Cancel first, and Discard leaves the background as it was`() {
        graph(beach, sunset, background = Background(beach.id))
        val activity = start().get()
        activity.ok("sunset.jpg")
        activity.step("Dim")

        @Suppress("DEPRECATION")
        activity.onBackPressed()
        assertFalse("asks first", activity.isFinishing)
        val (title, text, entries) = dialog()!!
        assertEquals("Discard changes?", title)
        assertEquals("The changes to the background aren't saved.", text)
        assertEquals(listOf("Cancel", "Discard"), entries.map { it.text.toString() })
        answer("Discard")

        assertTrue(activity.isFinishing)
        assertEquals(Background(beach.id), stored)
        assertEquals(0, graph.storedWallpaper.stored.size)
    }

    @Test
    fun `Cancel on the question keeps the editor and its draft`() {
        graph(beach, sunset, background = Background(beach.id))
        val activity = start().get()
        activity.ok("sunset.jpg")

        @Suppress("DEPRECATION")
        activity.onBackPressed()
        answer("Cancel")

        assertFalse(activity.isFinishing)
        assertNull(dialog())
        assertEquals(listOf("No picture", "beach.png", "sunset.jpg ✓", "Add from folder"), activity.list())
        assertEquals(Background(beach.id), stored)
    }

    // Changed and changed back is unchanged; so is a stored position the draft shows as the middle.
    @Test
    fun `Back with nothing changed closes at once`() {
        graph(sunset, panorama, background = Background(panorama.id, framing = Framing(position = PicturePosition.TOP)))
        val activity = start().get()
        activity.ok("sunset.jpg")
        activity.ok(panorama.name)

        @Suppress("DEPRECATION")
        activity.onBackPressed()

        assertTrue(activity.isFinishing)
        assertNull(dialog())
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
        graph.storedWallpaper.kept = WallpaperDrawing(sunset.id, PictureStyle(dim = 20), 1920, 1080) to withFrost(sample("kept", 4, 4))
        val activity = start().get()

        activity.ok("No picture")
        activity.save()

        assertTrue(activity.isFinishing)
        assertNull(dialog())
        assertEquals(Background(), stored)
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
        assertTrue("the screen finishes, the window going with its exit (Handover)", activity.isFinishing)
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
    fun `Rename asks for the new name, the cursor after the old one, and the row takes it, moving to its place by name with the focus and its thumbnail`() {
        graph(beach, sunset)
        graph.thumbnails.cache[beach.id to PictureStyle()] = sample("kept", 192, 108)
        val activity = start().get()
        activity.row("beach.png").requestFocus()
        val thumbnail = activity.row("beach.png").findViewById<ImageView>(R.id.settings_pictures_thumbnail).drawable

        activity.longPress("beach.png", "Rename")
        assertEquals("Rename picture", dialog()!!.first)
        assertEquals("beach.png", field().text.toString())
        assertEquals(9 to 9, field().selectionStart to field().selectionEnd)
        rename("  Waves  ")

        assertNull(dialog())
        assertEquals(listOf(beach.copy(name = "Waves"), sunset), graph.library.pictures)
        assertEquals(listOf("No picture ✓", "sunset.jpg", "Waves", "Add from folder"), activity.list())
        assertEquals("Waves", activity.focused())
        assertSame(thumbnail, activity.row("Waves").findViewById<ImageView>(R.id.settings_pictures_thumbnail).drawable)
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
        assertEquals(listOf("No picture ✓", "beach.png", "sunset.jpg", "Add from folder"), activity.list())
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
        graph.storedWallpaper.kept = WallpaperDrawing(beach.id, PictureStyle(dim = 40), 1920, 1080) to withFrost(sample("kept", 4, 4))
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
    fun `deleting a picture on tiles says on how many, counting installed apps only, and puts every one back to its app's own image first`() {
        graph(beach, sunset, apps = listOf("movies", "music", "news"), hidden = listOf("music"), onTiles = mapOf("movies" to beach, "music" to beach, "away" to beach, "news" to sunset))
        customizations.customizations += app("movies").launchable to AppCustomization(name = "Films", image = TileImage(beach.id))
        val activity = start().get()
        var namedWhenDeleted: List<PictureId>? = null
        graph.library.addListener { namedWhenDeleted = customizations.customizations.values.mapNotNull { it.image?.picture } }

        activity.longPress("beach.png", "Delete")
        assertEquals(
            "Used on 2 tiles: they will show their apps' own images again. The picture is deleted from Luncher's pictures only.",
            dialog()!!.second,
        )
        answer("Delete")

        assertEquals(
            "the hidden app's and the one not installed's too; its name kept",
            mapOf(app("movies").launchable to AppCustomization(name = "Films"), app("news").launchable to AppCustomization(image = TileImage(sunset.id))),
            customizations.customizations,
        )
        assertEquals("the tiles first: when the library said so, nothing named it", listOf(sunset.id), namedWhenDeleted)
        assertEquals(listOf(sunset), graph.library.pictures)
    }

    @Test
    fun `deleting the wallpaper's picture on a tile says both, a sentence each, and leaves neither`() {
        graph(beach, sunset, background = Background(beach.id, dim = 40), apps = listOf("movies"), onTiles = mapOf("movies" to beach))
        val activity = start().get()

        activity.longPress("beach.png", "Delete")
        assertEquals(
            "Used as the background: the home screen will have no picture. Used on a tile: it will show its app's own image again. " +
                "The picture is deleted from Luncher's pictures only.",
            dialog()!!.second,
        )
        answer("Delete")

        assertEquals(Background(dim = 40), stored)
        assertEquals(emptyMap<LaunchableApp, AppCustomization>(), customizations.customizations)
    }

    @Test
    fun `deleting a picture no installed app's tile shows says nothing of tiles, and stores nothing of the apps`() {
        graph(beach, sunset, apps = listOf("movies"), onTiles = mapOf("movies" to sunset))
        var saved = 0
        customizations.addListener { saved++ }
        val activity = start().get()

        activity.longPress("beach.png", "Delete")
        assertEquals("The picture is deleted from Luncher's pictures only.", dialog()!!.second)
        answer("Delete")

        assertEquals(0, saved)
        assertEquals(listOf(sunset), graph.library.pictures)
    }

    @Test
    fun `Cancel deletes nothing`() {
        graph(beach)
        val activity = start().get()

        activity.longPress("beach.png", "Delete")
        answer("Cancel")

        assertEquals(listOf(beach), graph.library.pictures)
    }

    private fun Activity.deleteAll() = findViewById<View>(R.id.settings_picture_editor_delete_all)

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
        assertTrue("the focus on Save", activity.findViewById<View>(R.id.settings_picture_editor_save).isFocused)
    }

    @Test
    fun `Delete all with the wallpaper's picture says so, and leaves no wallpaper at once`() {
        graph(beach, sunset, background = Background(beach.id, dim = 40))
        graph.storedWallpaper.kept = WallpaperDrawing(beach.id, PictureStyle(dim = 40), 1920, 1080) to withFrost(sample("kept", 4, 4))
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
    fun `Delete all counts the tiles of every picture, and puts them all back to their app's own image`() {
        graph(beach, sunset, old, background = Background(sunset.id), apps = listOf("movies", "news", "music"), onTiles = mapOf("movies" to beach, "news" to sunset, "away" to old))
        val activity = start().get()

        activity.deleteAll().performClick()
        idle()
        assertEquals(
            "One is used as the background: the home screen will have no picture. Used on 2 tiles: they will show their apps' own images again. " +
                "The 3 pictures are deleted from Luncher's pictures only.",
            dialog()!!.second,
        )
        answer("Delete")

        assertEquals(emptyMap<LaunchableApp, AppCustomization>(), customizations.customizations)
        assertEquals(Background(), stored)
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

        assertEquals(listOf("No picture", "holiday.jpg", "logo.png", "sunset.jpg ✓", "Add from folder"), activity.list())
        assertEquals("holiday.jpg", activity.focused())
    }

    @Test
    fun `the first of Add all's pictures in the list is focused when its result comes before the editor starts again`() {
        val added = (1..5).map { picture("added-$it.jpg", added = 10L + it) }
        graph(sunset, background = Background(sunset.id))
        val controller = start()
        val activity = controller.get()

        activity.ok("Add from folder")
        // Under Add from folder the editor is stopped; on a device the result comes before onStart.
        controller.pause().stop()
        graph.library.pictures += added   // what Add all added, in this order
        @Suppress("DEPRECATION")
        shadowOf(activity).receiveResult(
            Intent(activity, AddFromFolderActivity::class.java),
            Activity.RESULT_OK,
            Intent().putExtra(AddFromFolderActivity.EXTRA_ADDED_ALL, added.map { it.id.value }.toTypedArray()),
        )
        controller.restart().start().resume()
        idle()

        assertEquals("added-1.jpg", activity.focused())
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
    fun `shows the kept thumbnails at once, and draws the others in the background`() {
        graph(beach, sunset)
        graph.thumbnails.cache[beach.id to PictureStyle()] = sample("kept", 192, 108)

        val activity = start().get()
        assertTrue((activity.row("beach.png").findViewById<ImageView>(R.id.settings_pictures_thumbnail)).drawable != null)
        assertNull(activity.row("sunset.jpg").findViewById<ImageView>(R.id.settings_pictures_thumbnail).drawable)

        drawInTheBackground()

        assertTrue(activity.row("sunset.jpg").findViewById<ImageView>(R.id.settings_pictures_thumbnail).drawable != null)
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

        assertEquals(listOf("No picture", "beach.png", "sunset.jpg ✓", "Add from folder"), controller.get().list())
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
            assertTrue(picture.name, activity.row(picture.name).findViewById<ImageView>(R.id.settings_pictures_thumbnail).drawable != null)
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
