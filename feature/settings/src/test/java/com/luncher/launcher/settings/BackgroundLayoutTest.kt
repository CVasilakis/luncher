package com.luncher.launcher.settings

import android.app.Activity
import android.graphics.RectF
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.appearanceOptions
import com.luncher.domain.pictures.FakeFolderPictures
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.FakePictures.Companion.picture
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.PictureFormat
import com.luncher.domain.pictures.SizeLimit
import com.luncher.launcher.testing.TV_SCREENS
import com.luncher.launcher.testing.TV_SCREENS_IN_EVERY_LANGUAGE
import com.luncher.launcher.testing.TvScreen
import com.luncher.launcher.testing.assertApart
import com.luncher.launcher.testing.assertInside
import com.luncher.launcher.testing.assertWhole
import com.luncher.launcher.testing.bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import kotlin.math.abs

/**
 * The background editor, Add from folder and the window after a share on every screen of
 * [TV_SCREENS], in every language:
 * everything inside the TV's overscan margin across, no text of Luncher's own cut, the dialogs a
 * margin from the screen's edges. A picture's name may end in "…": it's the user's, and can be any
 * length.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // real text widths; without it a character is 1 px wide
@Config(sdk = [33, Config.TARGET_SDK])    // 33 for API 22 to 33, which scale large text more (TvDevice.kt)
class BackgroundLayoutTest(private val screen: TvScreen) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens() = TV_SCREENS_IN_EVERY_LANGUAGE.map { arrayOf<Any>(it) }
    }

    private val application get() = RuntimeEnvironment.getApplication() as SettingsTestApplication

    private val library = (1..6).map { picture("picture $it.jpg", added = it.toLong(), width = 4000 + it, height = 3000, bytes = 12_345_678) }.toTypedArray()

    // Wider and taller than every screen of TV_SCREENS: between them, they offer every position.
    private val panorama = picture("panorama.jpg", added = 7, width = 6000, height = 1000)
    private val portrait = picture("portrait.jpg", added = 8, width = 1000, height = 4000)

    @Before
    fun useScreen() {
        RuntimeEnvironment.setFontScale(screen.fontScale)
        RuntimeEnvironment.setQualifiers(screen.qualifiers)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** The screen less the overscan margin at its sides, the whole height (the screens scroll). */
    private fun Activity.inside(): RectF {
        val metrics = resources.displayMetrics
        val margin = resources.getDimension(R.dimen.settings_pictures_margin_horizontal)
        return RectF(margin, 0f, metrics.widthPixels - margin, Float.MAX_VALUE)
    }

    private fun Activity.assertShown(what: String, view: View) {
        assertInside(screen, inside(), what, bounds(view))
        if (view is TextView) assertWhole(screen, view)
    }

    /** Inside the overscan margin across: a row of a list, which may be scrolled out of sight. */
    private fun Activity.assertAcross(what: String, view: View) {
        val area = bounds(view)
        assertTrue("$what at $area, outside the margins ${inside()} on $screen", area.left >= inside().left && area.right <= inside().right)
    }

    /** The background editor with [pictures], the first one the wallpaper, every thumbnail drawn. */
    private fun start(vararg pictures: com.luncher.domain.pictures.Picture): BackgroundActivity {
        val graph = TestPicturesGraph(FakePictures(*pictures))
        pictures.firstOrNull()?.let { graph.appearanceSettings.appearance = Appearance(background = Background(it.id)) }
        application.graph = graph
        val activity = Robolectric.buildActivity(BackgroundActivity::class.java).setup().get()
        idle()
        graph.jobs.runAll()
        idle()
        return activity
    }

    private fun Activity.children(id: Int): List<View> = findViewById<ViewGroup>(id).let { group -> (0 until group.childCount).map(group::getChildAt) }

    // Every position: left and right with a wide picture, top and bottom with a tall one.
    @Test
    fun `the background editor fits, with every value of every option`() {
        assertEditorFits(start(panorama, *library))
        assertEditorFits(start(portrait, *library))
    }

    /** The editor inside the margins, its texts whole, each of its options at each value it offers. */
    private fun assertEditorFits(activity: BackgroundActivity) {
        val preview = activity.findViewById<View>(R.id.settings_background_preview)
        val end = activity.findViewById<View>(R.id.settings_background_end)
        val metrics = activity.resources.displayMetrics

        activity.assertShown("preview", preview)
        activity.assertShown("Save", activity.findViewById(R.id.settings_background_save))
        activity.assertShown("hint", activity.findViewById(R.id.settings_background_hint))
        assertApart(screen, "preview and pictures", bounds(preview), bounds(end))
        assertTrue("preview ${preview.width} px wide on $screen", preview.width >= metrics.widthPixels / 3)
        // Save above the overscan margin without scrolling, where the screen is as tall as 1080p's
        // 540 dp or more; a shorter one scrolls the column instead.
        if (metrics.heightPixels / metrics.density >= 540) {
            val margin = activity.resources.getDimension(R.dimen.settings_appearance_margin_vertical)
            val save = bounds(activity.findViewById(R.id.settings_background_save))
            assertTrue("Save at $save, below the margin on $screen", save.bottom <= metrics.heightPixels - margin + 1)
        }
        for (row in activity.children(R.id.settings_background_list)) {
            activity.assertAcross("a picture's row", row)
            row.findViewById<TextView>(R.id.settings_background_chosen)?.let { activity.assertAcross("its check", it) }
        }
        // Luncher's own names whole: No picture, first, and Add from folder, last.
        val rows = activity.children(R.id.settings_background_list)
        for (row in listOf(rows.first(), rows.last())) assertWhole(screen, row.findViewById(R.id.settings_background_name))
        // Each option's list whole on the screen, inside the margins across, and each value it
        // offers chosen in turn, in order, the first last again, so the fit is back to filling for
        // the position.
        val options = activity.children(R.id.settings_background_options)
        val catalog = appearanceOptions().groups.single { it.editedApart }.options
        for ((row, option) in options.zip(catalog)) {
            activity.assertShown("an option", row)
            val value = row.findViewById<TextView>(R.id.settings_option_value)
            val first = value.text.toString()
            val count = activity.openList(row, option).childCount
            shadowOf(activity.application).latestPopupWindow.dismiss()   // as Back
            for (index in (1 until count) + 0) {
                val list = activity.openList(row, option)
                val entry = list.getChildAt(index)
                entry.requestFocus()
                entry.performClick()
                idle()
                assertWhole(screen, row.findViewById(R.id.settings_option_label))
                assertWhole(screen, value)
            }
            assertEquals("$option back at its first value on $screen", first, value.text.toString())
        }
    }

    /** OK on [option]'s [row]: its list, checked whole and inside the overscan margin. */
    private fun Activity.openList(row: View, option: Any): ViewGroup {
        assertTrue("$option can't take the focus on $screen", row.requestFocus())
        row.performClick()
        idle()
        val list = shadowOf(application).latestPopupWindow.contentView as ViewGroup
        val window = list.rootView.layoutParams as WindowManager.LayoutParams
        val metrics = resources.displayMetrics
        val area = RectF(window.x.toFloat(), window.y.toFloat(), (window.x + list.width).toFloat(), (window.y + list.height).toFloat())
        assertTrue("$option's list at $area, outside the margins ${inside()} on $screen", area.left >= inside().left && area.right <= inside().right)
        val margin = resources.getDimension(R.dimen.settings_appearance_margin_vertical)
        assertTrue("$option's list at $area, outside the margin on $screen", area.top >= margin - 1 && area.bottom <= metrics.heightPixels - margin + 1)
        for (index in 0 until list.childCount) assertWhole(screen, list.getChildAt(index) as TextView)
        return list
    }

    @Test
    fun `the background editor fits while the library is empty`() {
        val activity = start()
        val rows = activity.children(R.id.settings_background_list)

        for (row in rows) activity.assertAcross("a row of the list", row)
        assertWhole(screen, rows.last() as TextView)   // how to add a picture
    }

    @Test
    fun `what a long press offers fits, and the delete question, and the rename dialog saying why a name can't be`() {
        val activity = start(*library)
        val row = activity.children(R.id.settings_background_list)[1]
        fun offered(): ViewGroup {
            row.performLongClick()
            idle()
            return ShadowDialog.getLatestDialog().findViewById(R.id.settings_pictures_dialog_entries)
        }
        offered()
        assertDialogFits(activity)
        offered().getChildAt(1).performClick()   // Delete
        idle()
        assertDialogFits(activity)
        offered().getChildAt(0).performClick()   // Rename
        idle()
        val rename = ShadowDialog.getLatestDialog()
        rename.findViewById<EditText>(R.id.settings_pictures_dialog_field).setText(library.first().name)
        rename.findViewById<ViewGroup>(R.id.settings_pictures_dialog_entries).getChildAt(0).performClick()
        idle()
        assertDialogFits(activity)
    }

    /** The window after a share of [shared], which adds as [result]. */
    private fun received(shared: Uri, result: ImportResult): Activity {
        application.graph = TestPicturesGraph(FakePictures(results = mutableMapOf(shared to result)))
        val activity = Robolectric.buildActivity(PictureReceivedActivity::class.java, android.content.Intent().putParcelableArrayListExtra(PictureReceivedActivity.EXTRA_PICTURES, arrayListOf(shared))).setup().get()
        idle()
        return activity
    }

    @Test
    fun `the window after a share fits, the picture saved or not`() {
        val shared = Uri.parse("content://com.example/x")
        for (result in listOf(ImportResult.Added(library.first()), ImportResult.Unreadable, ImportResult.TooLarge(SizeLimit.BYTES))) {
            assertWindowFits(received(shared, result), text = result !is ImportResult.Added)
        }
    }

    /**
     * The window after a share of four pictures, the last not a picture: while the second is
     * added, with [adding]; else once all have their answer.
     */
    private fun receivedFour(adding: Boolean): Activity {
        val shared = (1..4).map { Uri.parse("content://com.example/$it") }
        val results = mutableMapOf<Uri, ImportResult>(
            shared[0] to ImportResult.Added(library[0]),
            shared[1] to ImportResult.AlreadyThere(library[0]),
            shared[2] to ImportResult.Added(library[1]),
        )
        val fake = FakePictures(results = results).apply { answerLater = adding }
        application.graph = TestPicturesGraph(fake)
        val activity = Robolectric.buildActivity(PictureReceivedActivity::class.java, android.content.Intent().putParcelableArrayListExtra(PictureReceivedActivity.EXTRA_PICTURES, ArrayList(shared))).setup().get()
        if (adding) fake.finishAdding()
        idle()
        return activity
    }

    @Test
    fun `the window after a share of several pictures fits, while they're added and after`() {
        assertWindowFits(receivedFour(adding = true), text = false)
        assertWindowFits(receivedFour(adding = false), text = true)
    }

    @Test
    fun `the window after a share of too many pictures fits`() {
        application.graph = TestPicturesGraph()
        val activity = Robolectric.buildActivity(PictureReceivedActivity::class.java, android.content.Intent().putExtra(PictureReceivedActivity.EXTRA_TOO_MANY, true)).setup().get()
        idle()
        assertWindowFits(activity, text = true)
    }

    /** The window a margin from the screen's edges, its title, its [text] if it shows one, and its entries whole. */
    private fun assertWindowFits(activity: Activity, text: Boolean) {
        val panel = activity.findViewById<View>(R.id.settings_received_panel)
        val metrics = activity.resources.displayMetrics
        val margin = activity.resources.getDimensionPixelSize(R.dimen.settings_panel_margin)
        assertTrue(
            "window ${panel.width} x ${panel.height} on $screen, ${metrics.widthPixels} x ${metrics.heightPixels} less $margin around",
            panel.width <= metrics.widthPixels - 2 * margin && panel.height <= metrics.heightPixels - 2 * margin,
        )
        assertWhole(screen, activity.findViewById(R.id.settings_received_title))
        if (text) assertWhole(screen, activity.findViewById(R.id.settings_received_text))
        for (entry in activity.children(R.id.settings_received_entries)) assertWhole(screen, entry as TextView)
    }

    /** The dialog showing: a margin from the screen's edges, its texts whole. */
    private fun assertDialogFits(activity: Activity) {
        val dialog = ShadowDialog.getLatestDialog()
        val panel = dialog.findViewById<View>(R.id.settings_pictures_dialog_panel)
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        panel.measure(View.MeasureSpec.makeMeasureSpec(panel.layoutParams.width, View.MeasureSpec.EXACTLY), unspecified)
        val metrics = activity.resources.displayMetrics
        val margin = activity.resources.getDimensionPixelSize(R.dimen.settings_pictures_dialog_margin)
        assertTrue(
            "dialog ${panel.measuredWidth} x ${panel.measuredHeight} on $screen, ${metrics.widthPixels} x ${metrics.heightPixels} less $margin around",
            panel.measuredWidth <= metrics.widthPixels - 2 * margin && panel.measuredHeight <= metrics.heightPixels - 2 * margin,
        )
        panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
        dialog.findViewById<TextView>(R.id.settings_pictures_dialog_text).takeIf { it.visibility == View.VISIBLE }?.let { assertWhole(screen, it) }
        val entries = dialog.findViewById<ViewGroup>(R.id.settings_pictures_dialog_entries)
        for (i in 0 until entries.childCount) assertWhole(screen, entries.getChildAt(i) as TextView)
        dialog.dismiss()
    }

    @Test
    fun `Add from folder fits, the preview beside the list`() {
        val graph = object : TestPicturesGraph() {
            override val folderPictures = FakeFolderPictures(
                folderPicture("holiday.jpg", removable = true),
                folderPicture("IMG_0001.HEIC", unsupported = PictureFormat.HEIF),
                draw = { picture, width, height -> sample(picture.name, width, height) },
            )
        }
        application.graph = graph
        val activity = Robolectric.buildActivity(AddFromFolderActivity::class.java).setup().get()
        idle()
        val list = activity.findViewById<ListView>(R.id.settings_pictures_folder_list)
        val preview = activity.findViewById<View>(R.id.settings_pictures_folder_preview)
        val metrics = activity.resources.displayMetrics
        val margin = activity.resources.getDimension(R.dimen.settings_pictures_margin_vertical)

        activity.assertShown("title", activity.findViewById(R.id.settings_pictures_folder_title))
        activity.assertShown("where", activity.findViewById(R.id.settings_pictures_folder_where))
        activity.assertShown("preview", preview)
        activity.assertShown("note", activity.findViewById(R.id.settings_pictures_folder_note))
        assertApart(screen, "list and preview", bounds(list), bounds(preview))
        // Of the TV's shape, as the wallpaper fills the screen, to a pixel's rounding.
        assertTrue(
            "preview ${preview.width} x ${preview.height} on $screen, ${metrics.widthPixels} x ${metrics.heightPixels}",
            abs(preview.width.toLong() * metrics.heightPixels - preview.height.toLong() * metrics.widthPixels) < metrics.widthPixels + metrics.heightPixels,
        )
        // On the smallest screens the column scrolls: to its end, the note, as Add all takes the focus.
        val addAll = activity.findViewById<TextView>(R.id.settings_pictures_folder_add_all)
        addAll.requestFocus()
        idle()
        activity.assertShown("Add all", addAll)
        assertTrue("Add all below the screen on $screen", bounds(addAll).bottom <= metrics.heightPixels - margin)
        assertTrue("note below the screen on $screen", bounds(activity.findViewById(R.id.settings_pictures_folder_note)).bottom <= metrics.heightPixels - margin)
        for (i in 0 until list.childCount) activity.assertShown("a picture's details", list.getChildAt(i).findViewById(R.id.settings_pictures_folder_details))
    }
}
