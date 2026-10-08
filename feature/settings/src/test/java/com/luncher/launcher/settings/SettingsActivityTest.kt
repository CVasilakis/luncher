package com.luncher.launcher.settings

import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.launcher.testing.TV_1080P
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

    private fun SettingsActivity.entries(): List<TextView> {
        val entries = findViewById<LinearLayout>(R.id.settings_entries)
        return (0 until entries.childCount).map { entries.getChildAt(it) as TextView }
    }

    private fun SettingsActivity.press(keyCode: Int) {
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    @Test
    fun `lists hiding apps, then the system settings`() {
        assertEquals(listOf("Hide apps", "System settings"), start().entries().map { it.text.toString() })
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
        assertEquals(listOf("Hide apps", "System settings"), activity.entries().map { it.text.toString() })
        assertTrue(activity.entries().first().isFocused)
    }

    @Test
    fun `OK on Hide apps opens the list of apps`() {
        val activity = start()

        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertEquals(HideAppsActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    @Test
    fun `the panel disappears while Hide apps is open in its place, and comes back after`() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()

        controller.get().press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(0f, controller.get().window.attributes.alpha)

        controller.pause().resume()                 // Hide apps closed
        assertEquals(1f, controller.get().window.attributes.alpha)
    }

    @Test
    fun `OK on the system settings opens them in a task of their own`() {
        val activity = start()

        activity.entries()[1].requestFocus()
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

        activity.entries()[1].requestFocus()
        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals("This device has no settings app", ShadowToast.getTextOfLatestToast())
    }

    // Unlike the home screen (HomeActivityTest), which stays left to right. Persian: a
    // right-to-left language Luncher has no translation for, so its entries are in English.
    @Test
    @Config(qualifiers = "fa-rIR-$TV_1080P")
    fun `lines its entries up at the right in right-to-left languages, English ones too`() {
        val entries = start().entries()
        val entry = entries.first().layout

        assertEquals("Hide apps", entries.first().text.toString())
        assertEquals(entry.width.toFloat(), entry.getLineRight(0))
    }
}
