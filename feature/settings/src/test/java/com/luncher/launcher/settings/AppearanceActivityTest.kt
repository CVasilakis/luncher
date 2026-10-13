package com.luncher.launcher.settings

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.HorizontalAlignment
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.Side
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.TileStyle
import com.luncher.domain.appearance.TopBarAppearance
import com.luncher.domain.appearance.VerticalAlignment
import com.luncher.launcher.testing.TV_1080P
import com.luncher.launcher.testing.bounds
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
import org.robolectric.Shadows.shadowOf
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

    /** The options' rows, shown or not. */
    private fun AppearanceActivity.rows() = children().filter { it.findViewById<View>(R.id.settings_option_value) != null }

    /** What shows: each group's title, "open" after it while its options show; each option as "Label: value"; the last entry. */
    private fun AppearanceActivity.lines() = children().filter { it.visibility == View.VISIBLE }.map { child ->
        val title = child.findViewById<TextView>(R.id.settings_group_title)
        val value = child.findViewById<TextView>(R.id.settings_option_value)
        when {
            title != null -> if (child.findViewById<View>(R.id.settings_group_arrow).rotation != 0f) "${title.text} open" else title.text.toString()
            value != null -> "${child.findViewById<TextView>(R.id.settings_option_label).text}: ${value.text}"
            else -> (child as TextView).text.toString()
        }
    }

    private fun AppearanceActivity.group(title: String) =
        children().single { it.findViewById<TextView>(R.id.settings_group_title)?.text?.toString() == title }

    private fun AppearanceActivity.row(label: String) =
        rows().single { it.findViewById<TextView>(R.id.settings_option_label).text.toString() == label }

    private fun AppearanceActivity.value(label: String) = row(label).findViewById<TextView>(R.id.settings_option_value).text.toString()

    private fun AppearanceActivity.press(keyCode: Int) {
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    /** OK on the group titled [title]. */
    private fun AppearanceActivity.toggle(title: String) {
        assertTrue(group(title).requestFocus())
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        shadowOf(Looper.getMainLooper()).idle()   // the layout pass, for the options it shows
    }

    /** Opens the group titled [title], and puts the focus on its option labeled [label]. */
    private fun AppearanceActivity.focus(title: String, label: String) {
        toggle(title)
        assertTrue(row(label).requestFocus())
    }

    @Test
    fun `lists the groups, their options hidden, then the reset entry`() {
        assertEquals(listOf("Tiles", "Top bar", "Reset to defaults"), start().lines())
    }

    @Test
    fun `starts with the first group focused`() {
        assertTrue(start().group("Tiles").isFocused)
    }

    @Test
    fun `OK on a group shows its options, each with its value, and hides another group's`() {
        val activity = start()

        activity.toggle("Tiles")
        assertEquals(
            listOf(
                "Tiles open", "Vertical alignment: Top", "Horizontal alignment: Left", "Full bottom row: Off",
                "Corner radius: 8 dp", "Top bar", "Reset to defaults",
            ),
            activity.lines(),
        )
        assertTrue(activity.group("Tiles").isFocused)

        activity.toggle("Top bar")
        assertEquals(
            listOf("Tiles", "Top bar open", "Time: Shown", "Date: Shown", "Side: Left", "Order: Time first", "Reset to defaults"),
            activity.lines(),
        )

        activity.toggle("Top bar")
        assertEquals(listOf("Tiles", "Top bar", "Reset to defaults"), activity.lines())
    }

    @Test
    fun `shows the stored values`() {
        settings.appearance = Appearance(
            TileGeometry(verticalAlignment = VerticalAlignment.BOTTOM, horizontalAlignment = HorizontalAlignment.RIGHT, fullBottomRow = true),
            TileStyle(Corners.Radius(12)),
            TopBarAppearance(clockSide = Side.RIGHT, timeSide = Side.RIGHT),
        )

        val activity = start()

        assertEquals("Bottom", activity.value("Vertical alignment"))
        assertEquals("Right", activity.value("Horizontal alignment"))
        assertEquals("On", activity.value("Full bottom row"))
        assertEquals("12 dp", activity.value("Corner radius"))
        assertEquals("Right", activity.value("Side"))
        assertEquals("Date first", activity.value("Order"))
        assertEquals(settings.appearance, activity.findViewById<AppearancePreviewView>(R.id.settings_appearance_preview).appearance)
    }

    @Test
    fun `Right steps to the next value and stores it at once, Left back`() {
        val activity = start()
        activity.focus("Tiles", "Vertical alignment")

        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("Center", activity.value("Vertical alignment"))
        assertEquals(VerticalAlignment.CENTER, settings.appearance.tileGeometry.verticalAlignment)

        activity.press(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("Top", activity.value("Vertical alignment"))
        assertEquals(Appearance(), settings.appearance)
        assertTrue(activity.row("Vertical alignment").isFocused)
    }

    @Test
    fun `the horizontal alignment steps from left through center to right`() {
        val activity = start()
        activity.focus("Tiles", "Horizontal alignment")

        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("Center", activity.value("Horizontal alignment"))
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("Right", activity.value("Horizontal alignment"))
        assertEquals(HorizontalAlignment.RIGHT, settings.appearance.tileGeometry.horizontalAlignment)
    }

    @Test
    fun `the preview follows each step`() {
        val activity = start()
        activity.focus("Tiles", "Vertical alignment")

        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)

        assertEquals(settings.appearance, activity.findViewById<AppearancePreviewView>(R.id.settings_appearance_preview).appearance)
    }

    @Test
    fun `steps around from the last value to the first, and back`() {
        val activity = start()
        activity.focus("Tiles", "Vertical alignment")

        activity.press(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("Bottom", activity.value("Vertical alignment"))

        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("Top", activity.value("Vertical alignment"))
        assertTrue(activity.row("Vertical alignment").isFocused)
    }

    @Test
    fun `nothing is stored when nothing changes`() {
        val activity = start()
        val before = settings.appearance
        val reset = activity.children().last()

        assertTrue(reset.requestFocus())
        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)   // the defaults already

        assertSame(before, settings.appearance)
    }

    // It only does something with the rows centered or at the bottom.
    @Test
    fun `with the rows at the top, the full bottom row is dimmed and the focus passes it by`() {
        val activity = start()
        activity.focus("Tiles", "Horizontal alignment")
        val fullRow = activity.row("Full bottom row")

        assertFalse(fullRow.isFocusable)
        assertTrue(fullRow.alpha < 1f)
        assertSame(activity.row("Corner radius"), activity.row("Horizontal alignment").focusSearch(View.FOCUS_DOWN))
    }

    @Test
    fun `with the rows centered, the full bottom row can be changed`() {
        val activity = start()
        activity.focus("Tiles", "Vertical alignment")
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)   // center
        val fullRow = activity.row("Full bottom row")

        assertTrue(fullRow.isFocusable)
        assertEquals(1f, fullRow.alpha)
        assertTrue(activity.row("Horizontal alignment").requestFocus())
        assertTrue(activity.row("Horizontal alignment").focusSearch(View.FOCUS_DOWN)!!.requestFocus())
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("On", activity.value("Full bottom row"))
        assertTrue(settings.appearance.tileGeometry.fullBottomRow)
    }

    @Test
    fun `hides the date, and dims the order, which needs both`() {
        val activity = start()
        activity.focus("Top bar", "Date")

        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)

        assertEquals("Hidden", activity.value("Date"))
        assertFalse(settings.appearance.topBar.showDate)
        assertTrue(activity.row("Side").isFocusable)
        assertFalse(activity.row("Order").isFocusable)
        assertTrue(activity.row("Order").alpha < 1f)
    }

    // The settings gear alone then, at the right: the side would change nothing.
    @Test
    fun `with the time and the date hidden, the side and the order are dimmed and the focus passes them by`() {
        settings.appearance = Appearance(topBar = TopBarAppearance(showTime = false, showDate = false))
        val activity = start()
        activity.focus("Top bar", "Date")

        assertEquals("Hidden", activity.value("Time"))
        assertEquals("Hidden", activity.value("Date"))
        assertFalse(activity.row("Side").isFocusable)
        assertFalse(activity.row("Order").isFocusable)
        assertSame(activity.children().last(), activity.row("Date").focusSearch(View.FOCUS_DOWN))
    }

    @Test
    fun `OK on the reset entry puts every option back to its default, and stores it`() {
        settings.appearance = Appearance(TileGeometry(verticalAlignment = VerticalAlignment.BOTTOM), TileStyle(Corners.Pill))
        val activity = start()
        val reset = activity.children().last()

        assertTrue(reset.requestFocus())
        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertEquals(Appearance(), settings.appearance)
        assertEquals("Top", activity.value("Vertical alignment"))
        assertEquals("8 dp", activity.value("Corner radius"))
    }

    @Test
    fun `the title is right above the preview, and the preview can't take the focus`() {
        val activity = start()

        val title = activity.findViewById<View>(R.id.settings_appearance_title)
        val preview = activity.findViewById<View>(R.id.settings_appearance_preview)
        assertEquals(title.left, preview.left)
        val gap = activity.resources.getDimensionPixelSize(R.dimen.settings_title_gap)
        assertEquals(title.bottom + gap, preview.top)
        assertFalse(preview.isFocusable)
    }

    @Test
    fun `the options start level with the title`() {
        val activity = start()

        val title = bounds(activity.findViewById(R.id.settings_appearance_title))
        val first = bounds(activity.group("Tiles"))
        assertEquals(title.centerY(), first.centerY(), 1f)
    }

    @Test
    fun `the corner radius's last value is a pill`() {
        settings.appearance = Appearance(tileStyle = TileStyle(Corners.Pill))

        assertEquals("Pill", start().value("Corner radius"))
    }

    // A TV switched from 1080p to 720p (HDMI), or a new language, recreates the editor.
    @Test
    fun `after a configuration change, shows the values as stored`() {
        val controller = Robolectric.buildActivity(AppearanceActivity::class.java).setup()
        controller.get().focus("Tiles", "Vertical alignment")
        controller.get().press(KeyEvent.KEYCODE_DPAD_RIGHT)

        RuntimeEnvironment.setQualifiers("+tvdpi")   // 960x540 dp at 720p
        controller.configurationChange().visible()   // Robolectric shows the new window only when told

        assertEquals("Center", controller.get().value("Vertical alignment"))
    }

    // Like the other settings panels (HideAppsActivityTest): the options at the left, each value at
    // the left of its name, and Left steps toward the arrow on that side, the next value.
    @Test
    @Config(qualifiers = "ar-rEG-$TV_1080P")
    fun `mirrors in right-to-left languages, Left stepping to the next value`() {
        val activity = start()
        activity.focus("المربعات", "المحاذاة العمودية")

        val preview = bounds(activity.findViewById(R.id.settings_appearance_preview))
        val options = bounds(activity.findViewById(R.id.settings_appearance_scroll))
        assertTrue("options at $options, preview at $preview", options.right <= preview.left)
        val row = activity.rows().first()
        val label = row.findViewById<TextView>(R.id.settings_option_label)
        val value = row.findViewById<View>(R.id.settings_option_value)
        val next = row.findViewById<View>(R.id.settings_option_next)
        assertTrue("value at ${value.right}, label at ${label.left}", value.right <= label.left)
        assertTrue("next arrow at ${next.right}, value at ${value.left}", next.right <= value.left)
        assertEquals(label.layout.width.toFloat(), label.layout.getLineRight(0))

        activity.press(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals(VerticalAlignment.CENTER, settings.appearance.tileGeometry.verticalAlignment)
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(VerticalAlignment.TOP, settings.appearance.tileGeometry.verticalAlignment)
    }
}
