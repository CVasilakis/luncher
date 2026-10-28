package com.luncher.launcher.settings

import android.app.Activity
import android.graphics.RectF
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import com.luncher.domain.apps.AppCustomization
import com.luncher.domain.apps.FakeAppCustomizations
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.TileBackdrop
import com.luncher.domain.apps.TileFocusShape
import com.luncher.domain.apps.TileImage
import com.luncher.domain.apps.TileLook
import com.luncher.domain.apps.tileImageOptions
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.FakePictures.Companion.picture
import com.luncher.domain.pictures.Picture
import com.luncher.launcher.testing.TV_SCREENS
import com.luncher.launcher.testing.TV_SCREENS_IN_EVERY_LANGUAGE
import com.luncher.launcher.testing.TvScreen
import com.luncher.launcher.testing.assertApart
import com.luncher.launcher.testing.assertDialogFits
import com.luncher.launcher.testing.assertInside
import com.luncher.launcher.testing.assertWhole
import com.luncher.launcher.testing.assertZoomShown
import com.luncher.launcher.testing.bounds
import com.luncher.launcher.testing.floatResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import kotlin.math.abs
import com.luncher.launcher.ui.R as UiR

/**
 * The tile banner picker on every screen of [TV_SCREENS], in every language: everything inside
 * the TV's overscan margin across, no text of Luncher's own cut (a picture's name may end in "…":
 * it's the user's, as is the app's), the preview a tile's shape, the dialogs a margin from the
 * screen's edges.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // real text widths; without it a character is 1 px wide
@Config(sdk = [33, Config.TARGET_SDK])    // 33 for API 22 to 33, which scale large text more (TvDevice.kt)
class TileBannerLayoutTest(private val screen: TvScreen) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens() = TV_SCREENS_IN_EVERY_LANGUAGE.map { arrayOf<Any>(it) }
    }

    private val application get() = RuntimeEnvironment.getApplication() as SettingsTestApplication

    private val library = (1..6).map { picture("picture $it.jpg", added = it.toLong(), width = 4000 + it, height = 3000, bytes = 12_345_678) }.toTypedArray()

    // Cut at the sides and at the top and bottom in a tile: between them, they offer every position.
    // See-through, so the tile's look takes the focus and offers its values too.
    private val panorama = picture("panorama.png", added = 7, width = 6000, height = 1000).copy(seeThrough = true)
    private val portrait = picture("portrait.png", added = 8, width = 1000, height = 4000).copy(seeThrough = true)

    private val movies = app("movies")

    @Before
    fun useScreen() {
        RuntimeEnvironment.setFontScale(screen.fontScale)
        RuntimeEnvironment.setQualifiers(screen.qualifiers)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** The screen less the overscan margin at its sides, the whole height (the screen scrolls). */
    private fun Activity.inside(): RectF {
        val metrics = resources.displayMetrics
        val margin = resources.getDimension(R.dimen.settings_pictures_margin_horizontal)
        return RectF(margin, 0f, metrics.widthPixels - margin, Float.MAX_VALUE)
    }

    private fun Activity.assertShown(what: String, view: View) {
        assertInside(screen, inside(), what, bounds(view))
        if (view is TextView) assertWhole(screen, view)
    }

    private fun Activity.assertAcross(what: String, view: View) {
        val area = bounds(view)
        assertTrue("$what at $area, outside the margins ${inside()} on $screen", area.left >= inside().left && area.right <= inside().right)
    }

    /** The picker for the movies app's tile, showing [chosen] as stored, with [pictures], every thumbnail drawn. */
    private fun start(vararg pictures: Picture, chosen: Picture? = pictures.firstOrNull(), look: TileLook = TileLook()): TileBannerActivity {
        val graph = object : TestPicturesGraph(FakePictures(*pictures)) {
            override val installedApps = FakeInstalledApps(movies)
            override val appCustomizations = FakeAppCustomizations(
                chosen?.let { mapOf(movies.launchable to AppCustomization(image = TileImage(it.id, look = look))) } ?: emptyMap(),
            )
        }
        application.graph = graph
        val activity = Robolectric.buildActivity(TileBannerActivity::class.java, TileBannerActivity.intent(application, movies.launchable)).setup().get()
        idle()
        graph.jobs.runAll()
        idle()
        return activity
    }

    private fun Activity.children(id: Int): List<View> = findViewById<ViewGroup>(id).let { group -> (0 until group.childCount).map(group::getChildAt) }

    private fun Activity.fields(): List<View> =
        children(R.id.settings_picture_editor_options).flatMap { line -> (0 until (line as ViewGroup).childCount).map(line::getChildAt) }

    // Every position: left and right with a wide picture, top and bottom with a tall one.
    @Test
    fun `the picker fits, with every value of every option`() {
        assertPickerFits(start(panorama, *library))
        assertPickerFits(start(portrait, *library))
    }

    /** The picker inside the margins, its texts whole, each of its options at each value it offers. */
    private fun assertPickerFits(activity: TileBannerActivity) {
        val preview = activity.findViewById<View>(R.id.settings_tile_banner_preview)
        val end = activity.findViewById<View>(R.id.settings_pictures_column)
        val metrics = activity.resources.displayMetrics

        val shows = preview.visibility == View.VISIBLE
        if (shows) activity.assertShown("preview", preview)
        activity.assertShown("the app's name", activity.findViewById(R.id.settings_tile_banner_name))
        activity.assertShown("Save", activity.findViewById(R.id.settings_picture_editor_save))
        activity.assertShown("hint", activity.findViewById(R.id.settings_pictures_hint))
        // The two panels' shape, to a pixel's rounding, as tall as the room left; or gone (below).
        if (shows) {
            assertApart(screen, "preview and pictures", bounds(preview), bounds(end))
            val shape = (preview as TilePreviewView).shape()
            assertTrue(
                "preview ${preview.width} x ${preview.height} on $screen",
                abs(preview.width * shape.height - preview.height * shape.width) < shape.width + shape.height,
            )
        }
        // Save above the overscan margin without scrolling, where the screen is as tall as 1080p's
        // 540 dp or more; a shorter one scrolls the column instead.
        if (metrics.heightPixels / metrics.density >= 540) {
            val margin = activity.resources.getDimension(R.dimen.settings_appearance_margin_vertical)
            val save = bounds(activity.findViewById(R.id.settings_picture_editor_save))
            assertTrue("Save at $save, below the margin on $screen", save.bottom <= metrics.heightPixels - margin + 1)
        }
        val entryZoom = preview.floatResource(UiR.dimen.entry_zoom)
        val buttonZoom = preview.floatResource(UiR.dimen.button_zoom)
        val lift = activity.resources.getDimension(UiR.dimen.button_lift)
        for ((what, id) in listOf("Save" to R.id.settings_picture_editor_save, "Delete all" to R.id.settings_picture_editor_delete_all)) {
            val button = activity.findViewById<View>(id)
            assertTrue("$what can't take the focus on $screen", button.requestFocus())
            idle()
            assertZoomShown(screen, what, button, buttonZoom, lift)
        }
        for (field in activity.fields()) {
            field.requestFocus()
            idle()
            assertZoomShown(screen, "an option", field, entryZoom)
        }
        for (row in activity.children(R.id.settings_pictures_list)) {
            activity.assertAcross("a picture's row", row)
            row.findViewById<View>(R.id.settings_pictures_chosen)?.let { activity.assertAcross("its check", it) }
        }
        // Luncher's own names whole: Original banner, first, and Add from folder, last.
        val rows = activity.children(R.id.settings_pictures_list)
        for (row in listOf(rows.first(), rows.last())) assertWhole(screen, row.findViewById(R.id.settings_pictures_name))
        // Each option's list whole on the screen, inside the margins, and each value it offers
        // chosen in turn, the one it showed last again, so the fit is back to filling for the position.
        for ((row, option) in activity.fields().zip(tileImageOptions)) {
            activity.assertShown("an option", row)
            val value = row.findViewById<TextView>(R.id.settings_option_value)
            val first = value.text.toString()
            val list = activity.openList(row, option)
            val count = list.childCount
            val shown = (0 until count).single { list.getChildAt(it).findViewById<TextView>(R.id.settings_option_list_value).text.toString() == first }
            shadowOf(activity.application).latestPopupWindow.dismiss()   // as Back
            for (index in (0 until count).filter { it != shown } + shown) {
                val entry = activity.openList(row, option).getChildAt(index)
                entry.requestFocus()
                entry.performClick()
                idle()
                assertWhole(screen, row.findViewById(R.id.settings_option_label))
                assertWhole(screen, value)
            }
            assertEquals("$option back at its value on $screen", first, value.text.toString())
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
        for (index in 0 until list.childCount) assertWhole(screen, list.getChildAt(index).findViewById(R.id.settings_option_list_value))
        return list
    }

    /** Where each field is laid out: its row's top, then its own left, top and width in the row; its zoom aside. */
    private fun Activity.fieldPlaces(): List<List<Int>> = fields().map { listOf((it.parent as View).top, it.left, it.top, it.width) }

    // Each field as wide as its widest value: two share a row only where every value of both fits,
    // so the fields are where they are whatever values the picker opens on (where Light glass doesn't
    // fit half the column, Backdrop and Focus are a row each, whatever the backdrop).
    @Test
    fun `the options are where they are, whatever values they show`() {
        val places = TileBackdrop.entries.flatMap { backdrop ->
            TileFocusShape.entries.map { focus -> start(panorama, *library, look = TileLook(backdrop, focus)).fieldPlaces() }
        }

        assertEquals("on $screen", 1, places.distinct().size)
    }

    // As Add from folder's preview: shorter than a row of the list, the panels would show nothing,
    // and they go, the others getting their room; the app's name stays.
    @Test
    fun `the preview is as tall as a row of the list at least, or gone`() {
        val activity = start(panorama, *library)
        val preview = activity.findViewById<View>(R.id.settings_tile_banner_preview)
        val least = activity.resources.getDimensionPixelSize(R.dimen.settings_pictures_preview_least)

        if (preview.visibility == View.VISIBLE) {
            assertTrue("preview ${preview.height} px tall on $screen, $least at least", preview.height >= least)
        } else {
            assertEquals("gone, not invisible, on $screen", View.GONE, preview.visibility)
            // Not for want of room it would have had: shown, it would have been shorter than that.
            val column = activity.findViewById<View>(R.id.settings_tile_banner_start)
            val content = (column as ViewGroup).getChildAt(0)
            val room = column.height - column.paddingTop - column.paddingBottom
            assertTrue("gone with $room px of room and ${content.height} taken on $screen", room - content.height < least)
        }
        activity.assertShown("the app's name", activity.findViewById(R.id.settings_tile_banner_name))
        // Laid out again, it stays as it is: its going, which leaves more room, doesn't bring it back.
        val column = activity.findViewById<View>(R.id.settings_tile_banner_start)
        val before = visibility(preview.visibility)
        repeat(2) {
            column.requestLayout()
            idle()
            assertEquals("laid out again on $screen", before, visibility(preview.visibility))
        }
        assertFalse("still laying out on $screen", column.isLayoutRequested)
    }

    private fun visibility(value: Int) = if (value == View.VISIBLE) "shown" else "gone"

    // The screens a TV most often is: the panels show.
    @Test
    fun `the preview shows on a 1080p screen and on 720p`() {
        if (screen.toString().substringBefore(",") !in setOf("1080p", "720p")) return

        val preview = start(panorama, *library).findViewById<View>(R.id.settings_tile_banner_preview)

        assertEquals("on $screen", View.VISIBLE, preview.visibility)
    }

    @Test
    fun `the picker fits while the library is empty`() {
        val activity = start()
        val rows = activity.children(R.id.settings_pictures_list)

        for (row in rows) activity.assertAcross("a row of the list", row)
        assertWhole(screen, rows.first().findViewById(R.id.settings_pictures_name))   // Original banner
        assertWhole(screen, rows.last() as TextView)   // how to add a picture
    }

    @Test
    fun `the question Back asks with the draft changed fits`() {
        val activity = start(panorama, *library)
        activity.children(R.id.settings_pictures_list)[2].performClick()

        @Suppress("DEPRECATION")
        activity.onBackPressed()
        idle()

        assertDialogFits(screen, activity)
    }
}
