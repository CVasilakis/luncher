package com.luncher.launcher.settings

import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.Side
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.TilePlacement
import com.luncher.domain.appearance.TileStyle
import com.luncher.domain.appearance.TopBarAppearance
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class AppearanceActivityTest {

    private val settings = FakeAppearanceSettings()

    @Before
    fun useFakes() {
        val application = RuntimeEnvironment.getApplication() as SettingsTestApplication
        application.graph = object : TestSettingsGraph() {
            override val appearanceSettings = this@AppearanceActivityTest.settings
        }
    }

    private fun start() = Robolectric.buildActivity(AppearanceActivity::class.java).setup().get()

    private fun AppearanceActivity.list() = findViewById<LinearLayout>(R.id.settings_appearance_options)

    private fun AppearanceActivity.children() = list().let { list -> (0 until list.childCount).map { list.getChildAt(it) } }

    /** The options' rows. */
    private fun AppearanceActivity.rows() = children().filter { it.findViewById<View>(R.id.settings_option_value) != null }

    /** Each option as "Label: value", each group title alone, then the last entry. */
    private fun AppearanceActivity.lines() = children().drop(1).map { child ->
        val value = child.findViewById<TextView>(R.id.settings_option_value)
        if (value == null) (child as TextView).text.toString()
        else "${child.findViewById<TextView>(R.id.settings_option_label).text}: ${value.text}"
    }

    private fun AppearanceActivity.row(label: String) =
        rows().single { it.findViewById<TextView>(R.id.settings_option_label).text.toString() == label }

    private fun AppearanceActivity.value(label: String) = row(label).findViewById<TextView>(R.id.settings_option_value).text.toString()

    private fun AppearanceActivity.press(keyCode: Int) {
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    @Test
    fun `lists the options in groups, each with its value, then the reset entry`() {
        assertEquals(
            listOf(
                "Tiles",
                "Placement: Top left",
                "Full bottom row: Off",
                "Corner radius: 0 dp",
                "Top bar",
                "Clock: Left",
                "Time beside the date: Left",
                "Reset to defaults",
            ),
            start().lines(),
        )
    }

    @Test
    fun `shows the stored values`() {
        settings.appearance = Appearance(
            TileGeometry(placement = TilePlacement.BOTTOM_RIGHT, fullBottomRow = true),
            TileStyle(cornerRadiusDp = 12),
            TopBarAppearance(clockSide = Side.RIGHT, timeSide = Side.RIGHT),
        )

        val activity = start()

        assertEquals("Bottom right", activity.value("Placement"))
        assertEquals("On", activity.value("Full bottom row"))
        assertEquals("12 dp", activity.value("Corner radius"))
        assertEquals("Right", activity.value("Clock"))
        assertEquals("Right", activity.value("Time beside the date"))
        assertEquals(settings.appearance, activity.findViewById<AppearancePreviewView>(R.id.settings_appearance_preview).appearance)
    }

    @Test
    fun `starts with the first option focused`() {
        assertTrue(start().row("Placement").isFocused)
    }

    @Test
    fun `Right steps to the next value and stores it at once, Left back`() {
        val activity = start()

        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("Top right", activity.value("Placement"))
        assertEquals(TilePlacement.TOP_RIGHT, settings.appearance.tileGeometry.placement)

        activity.press(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("Top left", activity.value("Placement"))
        assertEquals(Appearance(), settings.appearance)
        assertTrue(activity.row("Placement").isFocused)
    }

    @Test
    fun `the preview follows each step`() {
        val activity = start()

        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)

        assertEquals(settings.appearance, activity.findViewById<AppearancePreviewView>(R.id.settings_appearance_preview).appearance)
    }

    @Test
    fun `stops at the first and the last value, keeping the focus, its arrow dimmed`() {
        val activity = start()
        val row = activity.row("Placement")
        val previous = row.findViewById<View>(R.id.settings_option_previous)
        val next = row.findViewById<View>(R.id.settings_option_next)
        assertTrue(previous.alpha < 1f)
        assertEquals(1f, next.alpha)

        activity.press(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("Top left", activity.value("Placement"))

        repeat(5) { activity.press(KeyEvent.KEYCODE_DPAD_RIGHT) }
        assertEquals("Bottom right", activity.value("Placement"))
        assertEquals(1f, previous.alpha)
        assertTrue(next.alpha < 1f)
        assertTrue(row.isFocused)
    }

    @Test
    fun `a write happens only when a value changes`() {
        val activity = start()
        val before = settings.appearance

        activity.press(KeyEvent.KEYCODE_DPAD_LEFT)   // at the first value already

        assertSame(before, settings.appearance)
    }

    // It only does something with the tiles at the bottom.
    @Test
    fun `with the tiles at the top, the full bottom row is dimmed and the focus passes it by`() {
        val activity = start()
        val fullRow = activity.row("Full bottom row")

        assertFalse(fullRow.isFocusable)
        assertTrue(fullRow.alpha < 1f)
        assertSame(activity.row("Corner radius"), activity.row("Placement").focusSearch(View.FOCUS_DOWN))
    }

    @Test
    fun `with the tiles at the bottom, the full bottom row can be changed`() {
        val activity = start()
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)   // bottom left
        val fullRow = activity.row("Full bottom row")

        assertTrue(fullRow.isFocusable)
        assertEquals(1f, fullRow.alpha)
        assertTrue(activity.row("Placement").focusSearch(View.FOCUS_DOWN)!!.requestFocus())
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("On", activity.value("Full bottom row"))
        assertTrue(settings.appearance.tileGeometry.fullBottomRow)
    }

    @Test
    fun `OK on the reset entry puts every option back to its default, and stores it`() {
        settings.appearance = Appearance(TileGeometry(placement = TilePlacement.BOTTOM_LEFT), TileStyle(cornerRadiusDp = 8))
        val activity = start()
        val reset = activity.children().last()

        assertTrue(reset.requestFocus())
        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertEquals(Appearance(), settings.appearance)
        assertEquals("Top left", activity.value("Placement"))
        assertEquals("0 dp", activity.value("Corner radius"))
    }

    @Test
    fun `the preview can't take the focus`() {
        assertFalse(start().findViewById<View>(R.id.settings_appearance_preview).isFocusable)
    }

    // A TV switched from 1080p to 720p (HDMI), or a new language, recreates the editor.
    @Test
    fun `after a configuration change, shows the values as stored`() {
        val controller = Robolectric.buildActivity(AppearanceActivity::class.java).setup()
        controller.get().press(KeyEvent.KEYCODE_DPAD_RIGHT)

        RuntimeEnvironment.setQualifiers("+tvdpi")   // 960x540 dp at 720p
        controller.configurationChange().visible()   // Robolectric shows the new window only when told

        assertEquals("Top right", controller.get().value("Placement"))
    }

    // Unlike the other settings panels (HideAppsActivityTest), it doesn't mirror: the preview stays
    // at the left, the options at the right, Left still the previous value. Only texts line up at
    // the right.
    @Test
    @Config(qualifiers = "ar-rEG-$TV_1080P")
    fun `stays left to right in right-to-left languages, its texts at the right`() {
        val activity = start()

        val preview = activity.findViewById<View>(R.id.settings_appearance_preview)
        val options = activity.findViewById<View>(R.id.settings_appearance_scroll)
        assertTrue("preview at ${preview.right}, options at ${options.left}", preview.right <= options.left)
        val row = activity.rows().first()
        val label = row.findViewById<TextView>(R.id.settings_option_label)
        val value = row.findViewById<View>(R.id.settings_option_value)
        assertTrue("label at ${label.left}, value at ${value.left}", label.right <= value.left)
        assertEquals(label.layout.width.toFloat(), label.layout.getLineRight(0))
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(TilePlacement.TOP_RIGHT, settings.appearance.tileGeometry.placement)
    }
}
