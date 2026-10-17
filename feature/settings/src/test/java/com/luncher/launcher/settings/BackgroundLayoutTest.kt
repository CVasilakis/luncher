package com.luncher.launcher.settings

import android.app.Activity
import android.graphics.RectF
import android.net.Uri
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
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
import com.luncher.launcher.testing.TV_SCREENS
import com.luncher.launcher.testing.TV_SCREENS_IN_EVERY_LANGUAGE
import com.luncher.launcher.testing.TvScreen
import com.luncher.launcher.testing.assertApart
import com.luncher.launcher.testing.assertInside
import com.luncher.launcher.testing.assertWhole
import com.luncher.launcher.testing.bounds
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

/**
 * The background editor and Add from folder on every screen of [TV_SCREENS], in every language:
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
    private fun start(vararg pictures: com.luncher.domain.pictures.Picture, intent: android.content.Intent? = null, results: Map<Uri, ImportResult> = emptyMap()): BackgroundActivity {
        val graph = TestPicturesGraph(FakePictures(*pictures, results = results.toMutableMap()))
        pictures.firstOrNull()?.let { graph.appearanceSettings.appearance = Appearance(background = Background(it.id)) }
        application.graph = graph
        val activity = Robolectric.buildActivity(BackgroundActivity::class.java, intent).setup().get()
        idle()
        graph.jobs.runAll()
        idle()
        return activity
    }

    private fun Activity.children(id: Int): List<View> = findViewById<ViewGroup>(id).let { group -> (0 until group.childCount).map(group::getChildAt) }

    @Test
    fun `the background editor fits, with every value of every option`() {
        val activity = start(*library)
        val preview = activity.findViewById<View>(R.id.settings_background_preview)
        val end = activity.findViewById<View>(R.id.settings_background_end)
        val metrics = activity.resources.displayMetrics

        activity.assertShown("preview", preview)
        activity.assertShown("Save", activity.findViewById(R.id.settings_background_save))
        activity.assertShown("hint", activity.findViewById(R.id.settings_background_hint))
        assertApart(screen, "preview and pictures", bounds(preview), bounds(end))
        assertTrue("preview ${preview.width} px wide on $screen", preview.width >= metrics.widthPixels / 3)
        for (row in activity.children(R.id.settings_background_list)) {
            activity.assertAcross("a picture's row", row)
            row.findViewById<TextView>(R.id.settings_background_chosen)?.let { activity.assertAcross("its check", it) }
        }
        // Luncher's own names whole: No picture, first, and Add from folder, last.
        val rows = activity.children(R.id.settings_background_list)
        for (row in listOf(rows.first(), rows.last())) assertWhole(screen, row.findViewById(R.id.settings_background_name))
        // Each option through its values, in order; the next value is toward the arrow on its
        // side: Left when mirrored.
        val options = activity.children(R.id.settings_background_options)
        val next = if (options.first().layoutDirection == View.LAYOUT_DIRECTION_RTL) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        val catalog = appearanceOptions().groups.single { it.editedApart }.options
        for ((row, option) in options.zip(catalog)) {
            activity.assertShown("an option", row)
            if (!row.requestFocus()) continue   // the position, with the picture stretched
            repeat(option.values.size) { step ->
                assertWhole(screen, row.findViewById(R.id.settings_option_label))
                assertWhole(screen, row.findViewById(R.id.settings_option_value))
                if (step < option.values.lastIndex) {
                    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, next))
                    idle()
                }
            }
        }
    }

    @Test
    fun `the background editor fits while the library is empty`() {
        val activity = start()
        val rows = activity.children(R.id.settings_background_list)

        for (row in rows) activity.assertAcross("a row of the list", row)
        assertWhole(screen, rows.last() as TextView)   // how to add a picture
    }

    @Test
    fun `the delete question and a message fit`() {
        val activity = start(*library)
        activity.children(R.id.settings_background_list)[1].performLongClick()
        idle()
        assertDialogFits(activity)

        val shared = Uri.parse("content://com.example/x")
        val other = start(*library, intent = android.content.Intent().setData(shared), results = mapOf(shared to ImportResult.Unreadable))
        assertDialogFits(other)
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
        assertWhole(screen, dialog.findViewById(R.id.settings_pictures_dialog_text))
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
        assertTrue("note below the screen on $screen", bounds(activity.findViewById(R.id.settings_pictures_folder_note)).bottom <= metrics.heightPixels - margin)
        for (i in 0 until list.childCount) activity.assertShown("a picture's details", list.getChildAt(i).findViewById(R.id.settings_pictures_folder_details))
    }
}
