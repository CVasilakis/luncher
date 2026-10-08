package com.luncher.launcher.settings

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.launcher.testing.TV_SCREENS
import com.luncher.launcher.testing.TV_SCREENS_IN_EVERY_LANGUAGE
import com.luncher.launcher.testing.TvScreen
import com.luncher.launcher.testing.assertWhole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The settings panels' layout on every screen of [TV_SCREENS], in every language: each panel
 * within the screen, a margin from its edges, and no text cut. Hide apps lists 12 apps, more than
 * fit, the last one hidden.
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

    @Before
    fun useScreenAndFakes() {
        RuntimeEnvironment.setFontScale(screen.fontScale)
        RuntimeEnvironment.setQualifiers(screen.qualifiers)
        val application = RuntimeEnvironment.getApplication() as SettingsTestApplication
        application.graph = object : TestSettingsGraph() {
            override val installedApps = FakeInstalledApps(apps.map(::app))
            override val appArrangements = FakeAppArrangements(AppArrangement(order = null, hidden = listOf(app("zz last app").launchable)))
        }
    }

    /**
     * Measures [activity]'s panel as its floating window does, as large as its content, and checks
     * that it fits the screen less the margin on each side. Last in a test: measuring again changes
     * the views' text layouts, and the list's rows.
     */
    private fun assertPanelFits(activity: Activity) {
        val panel = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        // The size its layout sets (the width), else as large as its content (the height).
        fun spec(size: Int) = View.MeasureSpec.makeMeasureSpec(size.coerceAtLeast(0), if (size >= 0) View.MeasureSpec.EXACTLY else View.MeasureSpec.UNSPECIFIED)
        panel.measure(spec(panel.layoutParams.width), spec(panel.layoutParams.height))
        val metrics = activity.resources.displayMetrics
        val margin = activity.resources.getDimensionPixelSize(R.dimen.settings_panel_margin)
        assertTrue(
            "panel ${panel.measuredWidth} x ${panel.measuredHeight} on $screen, ${metrics.widthPixels} x ${metrics.heightPixels} less $margin around",
            panel.measuredWidth <= metrics.widthPixels - 2 * margin && panel.measuredHeight <= metrics.heightPixels - 2 * margin,
        )
    }

    @Test
    fun `the settings panel fits`() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val entries = activity.findViewById<LinearLayout>(R.id.settings_entries)

        for (i in 0 until entries.childCount) assertWhole(screen, entries.getChildAt(i) as TextView)
        assertPanelFits(activity)
    }

    @Test
    fun `Hide apps fits, ending on half a row`() {
        val activity = Robolectric.buildActivity(HideAppsActivity::class.java).setup().get()
        val list = activity.findViewById<ListView>(R.id.settings_apps)
        val row = activity.resources.getDimensionPixelSize(R.dimen.settings_entry_height)

        assertEquals("list ${list.layoutParams.height} px, rows $row px, on $screen", row / 2, list.layoutParams.height % row)
        assertTrue("list shorter than a row on $screen", list.layoutParams.height > row)
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
}
