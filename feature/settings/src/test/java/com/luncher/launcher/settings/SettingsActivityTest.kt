package com.luncher.launcher.settings

import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Side
import com.luncher.domain.appearance.TopBarAppearance
import com.luncher.launcher.testing.TV_1080P
import com.luncher.launcher.ui.R as UiR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class SettingsActivityTest {

    private fun start() = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

    private fun SettingsActivity.entries(): List<View> {
        val entries = findViewById<LinearLayout>(R.id.settings_entries)
        return (0 until entries.childCount).map { entries.getChildAt(it) }
    }

    private fun SettingsActivity.titles(): List<TextView> = entries().map { it.findViewById(R.id.settings_entry_title) }

    private fun SettingsActivity.press(keyCode: Int) {
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    @Test
    fun `lists hiding apps, the appearance and the pictures, then the system settings`() {
        assertEquals(listOf("Hide apps", "Appearance", "System settings"), start().titles().map { it.text.toString() })
    }

    @Test
    fun `lists its entries equally far apart`() {
        val entries = start().entries()

        val gaps = entries.zipWithNext { above, below -> below.top - above.bottom }
        assertEquals(listOf(0, 0), gaps)
    }

    @Test
    fun `starts with the first entry focused`() {
        assertTrue(start().entries().first().isFocused)
    }

    // A TV switched from 1080p to 720p (HDMI), or a new language, recreates the panel.
    @Test
    fun `after a configuration change, lists the same entries, the first focused`() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()

        RuntimeEnvironment.setQualifiers("+tvdpi")   // 960x540 dp at 720p
        controller.configurationChange().visible()   // Robolectric shows the new window only when told

        val activity = controller.get()
        assertEquals(listOf("Hide apps", "Appearance", "System settings"), activity.titles().map { it.text.toString() })
        assertTrue(activity.entries().first().isFocused)
    }

    @Test
    fun `OK on Hide apps opens the list of apps`() {
        val activity = start()

        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertEquals(HideAppsActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    // Hide apps is the same side sheet, so it covers the panel, which stays shown under it as it
    // fades in and out: never a moment with neither.
    @Test
    fun `the panel stays shown under Hide apps, which covers it, the same side sheet in its place`() {
        val panel = start()
        val hideApps = Robolectric.buildActivity(HideAppsActivity::class.java).setup().get()

        panel.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertEquals(1f, panel.window.attributes.alpha)
        val sheet = { attributes: android.view.WindowManager.LayoutParams -> listOf(attributes.gravity, attributes.x, attributes.y, attributes.width, attributes.height) }
        assertEquals(sheet(panel.window.attributes), sheet(hideApps.window.attributes))
    }

    @Test
    fun `OK on Appearance opens the editor, over the panel`() {
        val activity = start()

        activity.entries()[1].requestFocus()
        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertEquals(AppearanceActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        assertEquals(1f, activity.window.attributes.alpha)
    }

    // Luncher's short fade, not what the dialog theme leaves to Android (none on Android 16).
    @Test
    fun `the panel comes and goes with a short fade of Luncher's own`() {
        assertEquals(UiR.style.Animation_Luncher_Panel, start().window.attributes.windowAnimations)
    }

    @Test
    fun `OK on the system settings opens them in a task of their own`() {
        val activity = start()

        activity.entries()[2].requestFocus()
        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_SETTINGS, intent.action)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `says so when the device has no settings app`() {
        val activity = start()
        // Nothing handles ACTION_SETTINGS here, so starting it now throws, as on such a device.
        shadowOf(activity.application).checkActivities(true)

        activity.entries()[2].requestFocus()
        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals("No settings app on this device", ShadowToast.getTextOfLatestToast())
    }

    // Unlike the home screen (HomeActivityTest), which stays left to right. Persian: a
    // right-to-left language Luncher has no translation for, so its entries are in English.
    @Test
    @Config(qualifiers = "fa-rIR-$TV_1080P")
    fun `lines its entries up at the right in right-to-left languages, English ones too`() {
        val titles = start().titles()
        val entry = titles.first().layout

        assertEquals("Hide apps", titles.first().text.toString())
        assertEquals(entry.width.toFloat(), entry.getLineRight(0))
    }

    // A side sheet beside the home screen's gear, at the right by default, a margin from its edges.
    @Test
    fun `is a side sheet at the right, as tall as the screen less its margins`() {
        val activity = start()
        val window = activity.window.attributes
        val margin = activity.resources.getDimensionPixelSize(R.dimen.settings_sheet_margin)

        assertEquals(android.view.Gravity.RIGHT or android.view.Gravity.CENTER_VERTICAL, window.gravity)
        assertEquals(margin, window.x)
        assertEquals(activity.resources.displayMetrics.heightPixels - 2 * margin, window.height)
    }

    // The clock moved to the right puts the gear at the left: the panel opens beside it.
    @Test
    fun `is at the left when the clock is at the right, the gear at the left`() {
        val graph = (RuntimeEnvironment.getApplication() as SettingsTestApplication).graph as TestSettingsGraph
        graph.appearanceSettings.appearance = Appearance(topBar = TopBarAppearance(clockSide = Side.RIGHT))

        assertEquals(android.view.Gravity.LEFT or android.view.Gravity.CENTER_VERTICAL, start().window.attributes.gravity)
    }

    // Without a clock, the gear is at the right, wherever the clock would be.
    @Test
    fun `is at the right when the clock is hidden`() {
        val graph = (RuntimeEnvironment.getApplication() as SettingsTestApplication).graph as TestSettingsGraph
        graph.appearanceSettings.appearance = Appearance(topBar = TopBarAppearance(showTime = false, showDate = false, clockSide = Side.RIGHT))

        assertEquals(android.view.Gravity.RIGHT or android.view.Gravity.CENTER_VERTICAL, start().window.attributes.gravity)
    }

    // The home screen doesn't mirror in a right-to-left language, so its gear stays at the right,
    // and the panel with it; its content mirrors.
    @Test
    @Config(qualifiers = "ar-$TV_1080P")
    fun `is beside the gear in a right-to-left language too`() {
        assertEquals(android.view.Gravity.RIGHT or android.view.Gravity.CENTER_VERTICAL, start().window.attributes.gravity)
    }
}
