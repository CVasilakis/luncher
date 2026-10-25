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
import com.luncher.domain.apps.TileImage
import com.luncher.domain.apps.tileImageOptions
import com.luncher.domain.layout.TileShape
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
    private val panorama = picture("panorama.jpg", added = 7, width = 6000, height = 1000)
    private val portrait = picture("portrait.jpg", added = 8, width = 1000, height = 4000)

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
    private fun start(vararg pictures: Picture, chosen: Picture? = pictures.firstOrNull()): TileBannerActivity {
        val graph = object : TestPicturesGraph(FakePictures(*pictures)) {
            override val installedApps = FakeInstalledApps(movies)
            override val appCustomizations = FakeAppCustomizations(
                chosen?.let { mapOf(movies.launchable to AppCustomization(image = TileImage(it.id))) } ?: emptyMap(),
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

        activity.assertShown("preview", preview)
        activity.assertShown("the app's name", activity.findViewById(R.id.settings_tile_banner_name))
        activity.assertShown("Save", activity.findViewById(R.id.settings_picture_editor_save))
        activity.assertShown("hint", activity.findViewById(R.id.settings_pictures_hint))
        assertApart(screen, "preview and pictures", bounds(preview), bounds(end))
        // A tile's shape, to a pixel's rounding; as tall as the room left, a quarter of its height at the column's width at least.
        assertTrue(
            "preview ${preview.width} x ${preview.height} on $screen",
            abs(preview.width * TileShape.HEIGHT - preview.height * TileShape.WIDTH) < TileShape.WIDTH + TileShape.HEIGHT,
        )
        val column = activity.findViewById<View>(R.id.settings_tile_banner_start)
        val natural = (column.width - column.paddingLeft - column.paddingRight - 2 * activity.resources.getDimensionPixelSize(R.dimen.settings_container_padding)) * TileShape.HEIGHT / TileShape.WIDTH
        assertTrue("preview ${preview.height} px tall on $screen, ${natural / 4} at least", preview.height >= natural / 4)
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
        // chosen in turn, the first last again, so the fit is back to filling for the position.
        for ((row, option) in activity.fields().zip(tileImageOptions)) {
            activity.assertShown("an option", row)
            val value = row.findViewById<TextView>(R.id.settings_option_value)
            val first = value.text.toString()
            val count = activity.openList(row, option).childCount
            shadowOf(activity.application).latestPopupWindow.dismiss()   // as Back
            for (index in (1 until count) + 0) {
                val entry = activity.openList(row, option).getChildAt(index)
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
        for (index in 0 until list.childCount) assertWhole(screen, list.getChildAt(index).findViewById(R.id.settings_option_list_value))
        return list
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
