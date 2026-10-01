package com.luncher.launcher.system

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.R
import com.luncher.launcher.focus
import com.luncher.launcher.resolvedActivity
import com.luncher.launcher.settings.SettingsActivity
import com.luncher.launcher.waitForFocus
import com.luncher.launcher.waitForHomeScreen
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The settings panel's way into the device's own settings app, and back. */
@RunWith(AndroidJUnit4::class)
class SystemSettingsTest {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val application = ApplicationProvider.getApplicationContext<LuncherApplication>()
    private val systemSettingsEntry = By.pkg(LUNCHER).text(application.getString(R.string.settings_system))

    /** Not while the home app is still starting, which can open windows over the panel. */
    @Before
    fun startFromTheHomeScreen() {
        waitForHomeScreen()
    }

    /**
     * Back goes to the window that has the focus, so only while the panel has it. Otherwise the
     * runner closes the panel after the test, with the app's other activities.
     */
    @After
    fun closeThePanel() {
        val panel = ComponentName(application, SettingsActivity::class.java)
        if (focus().isOf(panel)) device.pressBack()
    }

    @Test
    fun systemSettingsEntry_opensTheDeviceSettings_andBackReturnsToThePanel() {
        val settings = checkNotNull(resolvedActivity(Settings.ACTION_SETTINGS)) { "nothing opens the device's settings" }
            .substringBefore('/')
        application.startActivity(Intent(application, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue("The settings panel didn't open", device.wait(Until.hasObject(systemSettingsEntry), TIMEOUT_MS))
        waitForFocus(SettingsActivity::class.java)   // shown isn't enough: keys go where the focus is

        device.pressDPadDown()      // past Hide apps
        device.pressDPadCenter()

        assertTrue("The device's settings didn't open", device.wait(Until.hasObject(By.pkg(settings)), TIMEOUT_MS))
        waitForFocus(settings)                       // Back goes there

        device.pressBack()

        assertTrue("Back didn't return to the panel", device.wait(Until.hasObject(systemSettingsEntry), TIMEOUT_MS))
    }

    private companion object {
        const val LUNCHER = "com.luncher.launcher"
        const val TIMEOUT_MS = 10_000L
    }
}
