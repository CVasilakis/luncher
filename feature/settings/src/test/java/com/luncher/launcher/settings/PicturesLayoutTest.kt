package com.luncher.launcher.settings

import android.app.Activity
import android.graphics.RectF
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
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
 * The pictures' screens on every screen of [TV_SCREENS], in every language: everything inside the
 * TV's overscan margin across, no text of Luncher's own cut, the dialogs a margin from the
 * screen's edges. A picture's name may end in "…": it's the user's, and can be any length.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // real text widths; without it a character is 1 px wide
@Config(sdk = [33, Config.TARGET_SDK])    // 33 for API 22 to 33, which scale large text more (TvDevice.kt)
class PicturesLayoutTest(private val screen: TvScreen) {

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

    private fun start(vararg pictures: com.luncher.domain.pictures.Picture, results: Map<Uri, ImportResult> = emptyMap()): PicturesActivity {
        val graph = TestPicturesGraph(FakePictures(*pictures, results = results.toMutableMap()))
        application.graph = graph
        val activity = Robolectric.buildActivity(PicturesActivity::class.java).setup().get()
        idle()
        graph.jobs.runAll()
        idle()
        return activity
    }

    @Test
    fun `Pictures fits, with every tile inside the margins`() {
        val activity = start(*library)
        val grid = activity.findViewById<ViewGroup>(R.id.settings_pictures_grid)

        activity.assertShown("title", activity.findViewById(R.id.settings_pictures_title))
        activity.assertShown("status", activity.findViewById(R.id.settings_pictures_status))
        val rows = (0 until grid.childCount).map { grid.getChildAt(it) as ViewGroup }
        val tiles = rows.flatMap { row -> (0 until row.childCount).map(row::getChildAt) }
        for (tile in tiles) {
            activity.assertShown("tile", tile)
            tile.findViewById<TextView>(R.id.settings_pictures_details)?.let { activity.assertShown("details", it) }
        }
        for (pair in tiles.zipWithNext()) assertApart(screen, "tiles", bounds(pair.first), bounds(pair.second))
        val add = tiles.last() as LinearLayout
        for (i in 0 until add.childCount) activity.assertShown("Add from folder", add.getChildAt(i))
        activity.assertShown("Delete unused", activity.findViewById(R.id.settings_pictures_delete_unused))
    }

    @Test
    fun `choosing the wallpaper fits`() {
        application.graph = TestPicturesGraph(FakePictures(*library))
        val activity = Robolectric.buildActivity(ChooseWallpaperActivity::class.java).setup().get()
        idle()
        val first = (activity.findViewById<ViewGroup>(R.id.settings_pictures_grid).getChildAt(0) as ViewGroup).getChildAt(0) as ViewGroup

        activity.assertShown("title", activity.findViewById(R.id.settings_pictures_title))
        activity.assertShown("status", activity.findViewById(R.id.settings_pictures_status))
        activity.assertShown("No picture", first.getChildAt(0))
    }

    @Test
    fun `Pictures fits while empty`() {
        val activity = start()
        val empty = activity.findViewById<ViewGroup>(R.id.settings_pictures_empty)

        activity.assertShown("status", activity.findViewById(R.id.settings_pictures_status))
        for (i in 0 until empty.childCount) activity.assertShown("how to add", empty.getChildAt(i))
    }

    @Test
    fun `a picture's actions and a message fit`() {
        val activity = start(*library)
        val first = (activity.findViewById<ViewGroup>(R.id.settings_pictures_grid).getChildAt(0) as ViewGroup).getChildAt(0)
        first.performClick()
        idle()
        assertDialogFits(activity)

        val shared = Uri.parse("content://com.example/x")
        val graph = TestPicturesGraph(FakePictures(*library, results = mutableMapOf(shared to ImportResult.Unreadable)))
        application.graph = graph
        val other = Robolectric.buildActivity(PicturesActivity::class.java, android.content.Intent().setData(shared)).setup().get()
        idle()
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
