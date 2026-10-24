package com.luncher.launcher.settings

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.luncher.domain.appearance.AppName
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.HorizontalAlignment
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.Side
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.TileStyle
import com.luncher.domain.appearance.TopBarAppearance
import com.luncher.domain.appearance.VerticalAlignment
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.SAMPLE_WALLPAPER
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.launcher.testing.TV_1080P
import com.luncher.launcher.ui.R as UiR
import com.luncher.domain.appearance.AccentColor
import com.luncher.domain.appearance.ThemeAppearance
import com.luncher.domain.appearance.ThemeMode
import com.luncher.domain.appearance.colorRoles
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import com.luncher.launcher.testing.bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class AppearanceActivityTest {

    private val settings = FakeAppearanceSettings()

    private val application = RuntimeEnvironment.getApplication() as SettingsTestApplication

    @Before
    fun useFakes() {
        application.graph = object : TestSettingsGraph() {
            override val appearanceSettings = this@AppearanceActivityTest.settings
        }
    }

    private fun start() = Robolectric.buildActivity(AppearanceActivity::class.java).setup().get()

    private fun AppearanceActivity.list() = findViewById<LinearLayout>(R.id.settings_appearance_options)

    private fun AppearanceActivity.children() = list().let { list -> (0 until list.childCount).map { list.getChildAt(it) } }

    /** The options' rows, shown or not. */
    private fun AppearanceActivity.rows() = children().filter { it.findViewById<View>(R.id.settings_option_value) != null }

    /**
     * What shows: each group's title, "open" after it while its options show (its mark pointing
     * up); each option as "Label: value".
     */
    private fun AppearanceActivity.lines() = children().filter { it.visibility == View.VISIBLE }.map { child ->
        val title = child.findViewById<TextView>(R.id.settings_group_title)
        val value = child.findViewById<TextView>(R.id.settings_option_value)
        when {
            title != null -> {
                val arrow = shadowOf(child.findViewById<ImageView>(R.id.settings_group_arrow).drawable).createdFromResId
                if (arrow == UiR.drawable.icon_caret_up) "${title.text} open" else title.text.toString()
            }
            value != null -> "${child.findViewById<TextView>(R.id.settings_option_label).text}: ${value.text}"
            else -> error("an entry neither a group nor an option")
        }
    }

    /** The entry of the group titled [title]. */
    private fun AppearanceActivity.group(title: String) =
        children().single { child -> child.findViewById<TextView>(R.id.settings_group_title)?.text?.toString() == title }

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

    /** OK on Reset to defaults, below the preview. */
    private fun AppearanceActivity.reset() {
        assertTrue(findViewById<View>(R.id.settings_appearance_reset).requestFocus())
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** The question showing: its title, its text and its answers; null if none shows. */
    private fun question(): Triple<String, String, List<TextView>>? {
        val dialog = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing } ?: return null
        val entries = dialog.findViewById<LinearLayout>(UiR.id.dialog_entries)
        return Triple(
            dialog.findViewById<TextView>(UiR.id.dialog_title).text.toString(),
            dialog.findViewById<TextView>(UiR.id.dialog_text).text.toString(),
            (0 until entries.childCount).map { entries.getChildAt(it) as TextView },
        )
    }

    private fun answer(label: String) {
        question()!!.third.single { it.text == label }.performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Opens the group titled [title], and puts the focus on its option labeled [label]. */
    private fun AppearanceActivity.focus(title: String, label: String) {
        toggle(title)
        assertTrue(row(label).requestFocus())
    }

    @Test
    fun `lists the groups, their options hidden`() {
        assertEquals(listOf("Tiles", "Time and date", "Theme"), start().lines())
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
                "Tiles open", "Vertical alignment: Bottom", "Horizontal alignment: Center", "Full bottom row: On",
                "Corner radius: 16 dp", "Focus frame: On", "App name: Off", "Time and date", "Theme",
            ),
            activity.lines(),
        )
        assertTrue(activity.group("Tiles").isFocused)

        activity.toggle("Time and date")
        assertEquals(
            listOf("Tiles", "Time and date open", "Time: Shown", "Date: Shown", "Side: Left", "Order: Time first", "Theme"),
            activity.lines(),
        )

        activity.toggle("Time and date")
        assertEquals(listOf("Tiles", "Time and date", "Theme"), activity.lines())
    }

    @Test
    fun `Background, below the preview, opens the background editor, and the preview shows what it saved after`() {
        val controller = Robolectric.buildActivity(AppearanceActivity::class.java).setup()
        val activity = controller.get()

        activity.findViewById<View>(R.id.settings_appearance_background).performClick()

        assertEquals(BackgroundActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        settings.appearance = Appearance(background = Background(picture = SAMPLE_WALLPAPER))   // saved there
        controller.pause().resume()   // back before it was stopped, as a quick save can be
        assertEquals(SAMPLE_WALLPAPER, activity.findViewById<AppearancePreviewView>(R.id.settings_appearance_preview).appearance.background.picture)
    }

    @Test
    fun `the theme's group offers the accent color and the mode, orange and dark at first`() {
        val activity = start()

        activity.toggle("Theme")

        assertEquals(listOf("Tiles", "Time and date", "Theme open", "Accent color: Orange", "Mode: Dark"), activity.lines())
        assertEquals(View.VISIBLE, activity.row("Accent color").findViewById<View>(R.id.settings_option_swatch).visibility)
        assertEquals(View.GONE, activity.row("Mode").findViewById<View>(R.id.settings_option_swatch).visibility)
    }

    @Test
    fun `Right steps through the accent colors and stores each at once`() {
        val activity = start()
        activity.focus("Theme", "Accent color")

        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("Pink", activity.value("Accent color"))
        assertEquals(AccentColor.PINK, settings.appearance.theme.accent)
        activity.press(KeyEvent.KEYCODE_DPAD_LEFT)
        activity.press(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("Gray", activity.value("Accent color"))
    }

    // In place, while the editor shows: its window and its texts take the new theme's colors at once.
    @Test
    fun `the light mode restyles the editor at once`() {
        val activity = start()
        activity.focus("Theme", "Mode")
        val title = activity.findViewById<TextView>(R.id.settings_appearance_title)
        assertEquals(colorRoles(ThemeAppearance()).onSurface, title.currentTextColor)

        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)

        val light = colorRoles(ThemeAppearance(mode = ThemeMode.LIGHT))
        assertEquals("Light", activity.value("Mode"))
        assertEquals(light.onSurface, title.currentTextColor)
        assertEquals(light.screen, (activity.window.decorView.background as ColorDrawable).color)
        assertTrue("the focus stays", activity.row("Mode").isFocused)
    }

    // The swatch is the accent as the menus show it, in the mode they have.
    @Test
    fun `the accent's swatch is its color in the menus`() {
        settings.appearance = Appearance(theme = ThemeAppearance(AccentColor.BLUE, ThemeMode.LIGHT))
        val activity = start()
        activity.toggle("Theme")

        val swatch = activity.row("Accent color").findViewById<View>(R.id.settings_option_swatch).background as GradientDrawable
        assertEquals(colorRoles(ThemeAppearance(AccentColor.BLUE, ThemeMode.LIGHT)).primary, swatch.color?.defaultColor)
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
        activity.focus("Tiles", "Corner radius")

        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("24 dp", activity.value("Corner radius"))
        assertEquals(Corners.Radius(24), settings.appearance.tileStyle.corners)

        activity.press(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("16 dp", activity.value("Corner radius"))
        assertEquals(Appearance(), settings.appearance)
        assertTrue(activity.row("Corner radius").isFocused)
    }

    // The tvOS-like look, a step away each: the focused tile lifted, its name below it.
    @Test
    fun `the focus frame steps to Off, and the app name to When focused`() {
        val activity = start()
        activity.focus("Tiles", "Focus frame")

        activity.press(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("Off", activity.value("Focus frame"))
        assertTrue(activity.row("App name").requestFocus())
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("When focused", activity.value("App name"))

        assertEquals(Appearance(TileGeometry(appName = AppName.WHEN_FOCUSED), TileStyle(focusFrame = false)), settings.appearance)
        assertEquals(settings.appearance, activity.findViewById<AppearancePreviewView>(R.id.settings_appearance_preview).appearance)
    }

    @Test
    fun `the app name steps from When focused to Always, below every tile`() {
        settings.appearance = Appearance(TileGeometry(appName = AppName.WHEN_FOCUSED))
        val activity = start()
        activity.focus("Tiles", "App name")

        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)

        assertEquals("Always", activity.value("App name"))
        assertEquals(Appearance(TileGeometry(appName = AppName.ALWAYS)), settings.appearance)
    }

    @Test
    fun `the horizontal alignment steps from left through center to right`() {
        settings.appearance = Appearance(TileGeometry(horizontalAlignment = HorizontalAlignment.LEFT))
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
        activity.focus("Tiles", "Vertical alignment")   // at Bottom, the last

        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("Top", activity.value("Vertical alignment"))

        activity.press(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("Bottom", activity.value("Vertical alignment"))
        assertTrue(activity.row("Vertical alignment").isFocused)
    }

    @Test
    fun `nothing is asked or stored when nothing changes`() {
        val activity = start()
        val before = settings.appearance

        activity.reset()   // the defaults already

        assertNull(question())
        assertSame(before, settings.appearance)
    }

    // It only does something with the rows centered or at the bottom.
    @Test
    fun `with the rows at the top, the full bottom row is dimmed and the focus passes it by`() {
        settings.appearance = Appearance(TileGeometry(verticalAlignment = VerticalAlignment.TOP))
        val activity = start()
        activity.focus("Tiles", "Horizontal alignment")
        val fullRow = activity.row("Full bottom row")

        assertFalse(fullRow.isFocusable)
        assertTrue(fullRow.alpha < 1f)
        assertSame(activity.row("Corner radius"), activity.row("Horizontal alignment").focusSearch(View.FOCUS_DOWN))
    }

    @Test
    fun `with the rows centered, the full bottom row can be changed`() {
        settings.appearance = Appearance(TileGeometry(verticalAlignment = VerticalAlignment.TOP))
        val activity = start()
        activity.focus("Tiles", "Vertical alignment")
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)   // center
        val fullRow = activity.row("Full bottom row")

        assertTrue(fullRow.isFocusable)
        assertEquals(1f, fullRow.alpha)
        assertTrue(activity.row("Horizontal alignment").requestFocus())
        assertTrue(activity.row("Horizontal alignment").focusSearch(View.FOCUS_DOWN)!!.requestFocus())
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("Off", activity.value("Full bottom row"))
        assertFalse(settings.appearance.tileGeometry.fullBottomRow)
    }

    @Test
    fun `hides the date, and dims the order, which needs both`() {
        val activity = start()
        activity.focus("Time and date", "Date")

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
        activity.focus("Time and date", "Date")

        assertEquals("Hidden", activity.value("Time"))
        assertEquals("Hidden", activity.value("Date"))
        assertFalse(activity.row("Side").isFocusable)
        assertFalse(activity.row("Order").isFocusable)
        assertSame(activity.group("Theme"), activity.row("Date").focusSearch(View.FOCUS_DOWN))
    }

    @Test
    fun `OK on the reset entry asks, Cancel first, and Reset puts every option back to its default, and stores it`() {
        settings.appearance = Appearance(TileGeometry(verticalAlignment = VerticalAlignment.TOP), TileStyle(Corners.Pill))
        val activity = start()

        activity.reset()
        val (title, text, entries) = question()!!
        assertEquals("Reset the appearance to defaults?", title)
        assertEquals("Every option goes back to its default.", text)
        assertEquals(listOf("Cancel", "Reset"), entries.map { it.text.toString() })
        assertTrue("Cancel focused", entries[0].isFocused)
        answer("Reset")

        assertNull(question())
        assertEquals(Appearance(), settings.appearance)
        assertEquals("Bottom", activity.value("Vertical alignment"))
        assertEquals("16 dp", activity.value("Corner radius"))
        assertTrue("the focus back on Reset to defaults", activity.findViewById<View>(R.id.settings_appearance_reset).isFocused)
    }

    @Test
    fun `the reset question says the home screen loses its picture, and Reset leaves none`() {
        settings.appearance = Appearance(background = Background(picture = SAMPLE_WALLPAPER))
        val activity = start()

        activity.reset()
        assertEquals(
            "Every option goes back to its default: the home screen will have no picture. The picture stays in Luncher's pictures.",
            question()!!.second,
        )
        answer("Reset")

        assertEquals(Appearance(), settings.appearance)
    }

    @Test
    fun `Cancel on the reset question changes nothing`() {
        val changed = Appearance(TileGeometry(verticalAlignment = VerticalAlignment.TOP))
        settings.appearance = changed
        val activity = start()

        activity.reset()
        answer("Cancel")

        assertNull(question())
        assertEquals(changed, settings.appearance)
        assertEquals("Top", activity.value("Vertical alignment"))
    }

    @Test
    fun `the title is right above the preview, and the preview can't take the focus`() {
        val activity = start()

        val title = activity.findViewById<View>(R.id.settings_appearance_title)
        val preview = activity.findViewById<View>(R.id.settings_appearance_preview)
        assertEquals(title.left, preview.left)
        val gap = activity.resources.getDimensionPixelSize(R.dimen.settings_screen_title_gap)
        assertEquals(title.bottom + gap, preview.top)
        assertFalse(preview.isFocusable)
    }

    // Down to the bottom margin from the start, as the side sheets' lists, its groups closed.
    @Test
    fun `the groups' container starts level with the title and reaches the bottom margin`() {
        val activity = start()

        val title = bounds(activity.findViewById(R.id.settings_appearance_title))
        val container = bounds(activity.findViewById(R.id.settings_appearance_frame))
        val end = activity.findViewById<View>(R.id.settings_appearance_end)
        assertEquals(title.top, container.top, 1f)
        assertEquals(bounds(end).bottom - end.paddingBottom, container.bottom, 1f)
    }

    // The options' container fits the room below the title, measured for it in one pass: measured
    // for more room first, the list would lose its scroll each time the editor lays itself out.
    @Test
    fun `the options keep where they scrolled to when the editor lays itself out again`() {
        val activity = start()
        val scroll = activity.findViewById<ScrollView>(R.id.settings_appearance_scroll)
        activity.toggle("Tiles")
        repeat(activity.children().size) {
            activity.press(KeyEvent.KEYCODE_DPAD_DOWN)
            shadowOf(Looper.getMainLooper()).idle()
        }
        val scrolled = scroll.scrollY
        assertTrue("the options scrolled", scrolled > 0)
        val heights = mutableListOf<Int>()
        scroll.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> heights += view.height }

        activity.row("App name").requestLayout()   // as a value that changes its width does
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("laid out at its height only, not $heights", heights.all { it == scroll.height })
        assertEquals(scrolled, scroll.scrollY)
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

        assertEquals("Top", controller.get().value("Vertical alignment"))
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
        assertEquals(VerticalAlignment.TOP, settings.appearance.tileGeometry.verticalAlignment)
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(VerticalAlignment.BOTTOM, settings.appearance.tileGeometry.verticalAlignment)
    }
}
