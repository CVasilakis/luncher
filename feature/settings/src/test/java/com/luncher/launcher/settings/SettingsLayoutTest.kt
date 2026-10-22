package com.luncher.launcher.settings

import android.app.Activity
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.SAMPLE_WALLPAPER
import com.luncher.domain.appearance.appearanceOptions
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.launcher.testing.TV_SCREENS
import com.luncher.launcher.testing.TV_SCREENS_IN_EVERY_LANGUAGE
import com.luncher.launcher.testing.TvScreen
import com.luncher.launcher.testing.assertApart
import com.luncher.launcher.testing.assertWhole
import com.luncher.launcher.testing.bounds
import java.util.Locale
import org.junit.After
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

/**
 * The settings panels' layout on every screen of [TV_SCREENS], in every language: each panel
 * within the screen, a margin from its edges, and no text cut. Hide apps lists 12 apps, more than
 * fit, the last one hidden. The appearance editor shows every value of every option, and its
 * reset question fits.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // real text widths; without it a character is 1 px wide
@Config(sdk = [33, Config.TARGET_SDK])    // 33 for API 22 to 33, which scale large text more (TvDevice.kt)
class SettingsLayoutTest(private val screen: TvScreen) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens() = TV_SCREENS_IN_EVERY_LANGUAGE.map { arrayOf<Any>(it) }
    }

    private val apps = (1..11).map { "app%02d".format(it) } + "zz last app"

    private val defaultLocale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    @Before
    fun useScreenAndFakes() {
        RuntimeEnvironment.setFontScale(screen.fontScale)
        RuntimeEnvironment.setQualifiers(screen.qualifiers)
        // The language formats the clock and sorts the apps too, through the default Locale, which
        // Robolectric sets from a test's @Config only.
        Locale.setDefault(RuntimeEnvironment.getApplication().resources.configuration.locales[0])
        val application = RuntimeEnvironment.getApplication() as SettingsTestApplication
        application.graph = object : TestSettingsGraph() {
            override val installedApps = FakeInstalledApps(apps.map(::app))
            override val appArrangements = FakeAppArrangements(AppArrangement(order = null, hidden = listOf(app("zz last app").launchable)))
            // A wallpaper, so the Background entry names a picture, not None.
            override val appearanceSettings = FakeAppearanceSettings(Appearance(background = Background(picture = SAMPLE_WALLPAPER)))
        }
    }

    /**
     * Checks that [activity]'s side sheet fits the screen less its margin on each side, and that
     * its content, measured at the sheet's width as large as it is, fits the sheet's height. Last in
     * a test: measuring again changes the views' text layouts, and the list's rows.
     */
    private fun View.forceLayoutAll() {
        forceLayout()
        if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).forceLayoutAll()
    }

    private fun assertPanelFits(activity: Activity) {
        val window = activity.window.attributes
        val metrics = activity.resources.displayMetrics
        val margin = activity.resources.getDimensionPixelSize(R.dimen.settings_sheet_margin)
        assertTrue(
            "sheet ${window.width} x ${window.height} on $screen, ${metrics.widthPixels} x ${metrics.heightPixels} less $margin around",
            window.width <= metrics.widthPixels - 2 * margin && window.height <= metrics.heightPixels - 2 * margin,
        )
        val panel = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(window.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        assertTrue("content ${panel.measuredHeight} px in a sheet ${window.height} px tall on $screen", panel.measuredHeight <= window.height)
    }

    @Test
    fun `the settings panel fits`() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val entries = activity.findViewById<LinearLayout>(R.id.settings_entries)

        for (i in 0 until entries.childCount) {
            assertWhole(screen, entries.getChildAt(i).findViewById(R.id.settings_entry_title))
        }
        assertPanelFits(activity)
    }

    @Test
    fun `Hide apps fits, its list filling the sheet when the apps don't all fit`() {
        val activity = Robolectric.buildActivity(HideAppsActivity::class.java).setup().get()
        val list = activity.findViewById<ListView>(R.id.settings_apps)
        val row = activity.resources.getDimensionPixelSize(R.dimen.settings_row_height) + activity.resources.getDimensionPixelSize(R.dimen.settings_entry_gap)
        val rowsHeight = list.layoutParams.height - list.paddingTop - list.paddingBottom

        // All of them where they fit (a screen as tall as 1080p at 160 dpi's 1080 dp); else down to the sheet's padding.
        if (rowsHeight != apps.size * row) {
            val window = activity.window.attributes
            val panel = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            panel.forceLayoutAll()   // measured already with the same specs, before the list had its height
            panel.measure(
                View.MeasureSpec.makeMeasureSpec(window.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            assertEquals("the panel as tall as the sheet on $screen", window.height, panel.measuredHeight)
        }
        assertTrue("list shorter than a row on $screen", rowsHeight > row)
        val rows = (0 until list.childCount).map { list.getChildAt(it) }
        for (view in rows) assertWhole(screen, view.findViewById(R.id.settings_app_label))
        list.setSelection(apps.lastIndex)   // the hidden app, "Hidden" after its name
        list.measure(
            View.MeasureSpec.makeMeasureSpec(list.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(list.height, View.MeasureSpec.EXACTLY),
        )
        list.layout(list.left, list.top, list.right, list.bottom)
        val last = (0 until list.childCount).map { list.getChildAt(it) }.last()
        assertEquals("Zz last app", last.findViewById<TextView>(R.id.settings_app_label).text.toString())
        assertWhole(screen, last.findViewById(R.id.settings_app_label))
        assertWhole(screen, last.findViewById(R.id.settings_app_hidden))
        assertPanelFits(activity)
    }

    // With a picture, the longer of its texts.
    @Test
    fun `the reset question fits`() {
        val activity = Robolectric.buildActivity(AppearanceActivity::class.java).setup().get()

        activity.findViewById<View>(R.id.settings_appearance_reset).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertDialogFits(screen, activity)
    }

    @Test
    fun `the appearance editor fits, with every value of every option`() {
        val activity = Robolectric.buildActivity(AppearanceActivity::class.java).setup().get()
        val metrics = activity.resources.displayMetrics
        val margin = activity.resources.getDimension(R.dimen.settings_appearance_margin_horizontal)
        val title = activity.findViewById<TextView>(R.id.settings_appearance_title)
        val preview = activity.findViewById<View>(R.id.settings_appearance_preview)
        val options = activity.findViewById<ViewGroup>(R.id.settings_appearance_options)
        val children = (0 until options.childCount).map { options.getChildAt(it) }
        val groups = children.filter { it.findViewById<View>(R.id.settings_group_title) != null }

        assertWhole(screen, title)
        val container = bounds(activity.findViewById(R.id.settings_appearance_frame))
        assertEquals("the groups' container's top on $screen", bounds(title).top, container.top, 1f)
        assertApart(screen, "title and preview", bounds(title), bounds(preview))
        assertApart(screen, "preview and options", bounds(preview), container)
        // Below the preview, inside the overscan margin, their texts whole.
        for (id in listOf(R.id.settings_appearance_background, R.id.settings_appearance_reset)) {
            val button = activity.findViewById<TextView>(id)
            assertWhole(screen, button)
            assertApart(screen, "preview and ${button.text}", bounds(preview), bounds(button))
            val area = bounds(button)
            assertTrue("${button.text} at $area outside the overscan margin on $screen", area.left >= margin && area.bottom <= metrics.heightPixels - activity.resources.getDimension(R.dimen.settings_appearance_margin_vertical))
        }
        assertTrue("preview ${preview.width} px wide on $screen", preview.width >= metrics.widthPixels / 3)
        assertTrue("preview ${preview.height} px tall on $screen", preview.height >= metrics.heightPixels / 4)
        // Each group open in turn, each of its options through its values, in order, ending at
        // the last: the tiles end at the bottom, where the full bottom row can be changed. Unless
        // the last leaves a later option that can't be changed (the time hidden, its order with
        // the date): then around to the first. The next value is toward the arrow on its side:
        // Left when mirrored.
        val next = if (options.layoutDirection == View.LAYOUT_DIRECTION_RTL) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        // The background's group is one entry, edited in its own screen (BackgroundLayoutTest).
        val catalog = appearanceOptions().groups.filter { !it.editedApart }
        for ((index, group) in groups.withIndex()) {
            assertTrue(group.requestFocus())
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
            shadowOf(Looper.getMainLooper()).idle()   // the layout pass, for the options it shows
            val shown = children.filter { it.visibility == View.VISIBLE }
            for (child in shown) {
                val area = bounds(child)
                assertTrue("$area outside the overscan margin on $screen", area.left >= margin && area.right <= metrics.widthPixels - margin)
                if (child is TextView) assertWhole(screen, child)
            }
            assertWhole(screen, group.findViewById(R.id.settings_group_title))
            val rows = shown.filter { it.findViewById<View>(R.id.settings_option_value) != null }
            for ((i, pair) in rows.zip(catalog[index].options).withIndex()) {
                val (row, option) = pair
                assertTrue("${row.label().text} can't take the focus on $screen", row.requestFocus())
                repeat(option.values.size) { step ->
                    assertWhole(screen, row.label())
                    assertWhole(screen, row.value())
                    if (step < option.values.lastIndex) {
                        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, next))
                        shadowOf(Looper.getMainLooper()).idle()   // the layout pass
                    }
                }
                if (rows.drop(i + 1).any { !it.isFocusable }) {
                    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, next))
                    shadowOf(Looper.getMainLooper()).idle()
                }
            }
        }
    }

    private fun View.label() = findViewById<TextView>(R.id.settings_option_label)

    private fun View.value() = findViewById<TextView>(R.id.settings_option_value)
}
