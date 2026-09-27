package com.luncher.launcher.settings

import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.launcher.R
import com.luncher.launcher.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
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
    fun `lists the system settings`() {
        assertEquals(listOf("System settings"), start().entries().map { it.text.toString() })
    }

    @Test
    fun `starts with the first entry focused`() {
        assertTrue(start().entries().first().isFocused)
    }

    @Test
    fun `OK on the system settings opens them in a task of their own`() {
        val activity = start()

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

        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals("This device has no settings app", ShadowToast.getTextOfLatestToast())
    }
}
