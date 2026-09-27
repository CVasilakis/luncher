package com.luncher.launcher.system

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.R
import com.luncher.launcher.settings.SettingsActivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The settings panel's way into the device's own settings app, and back. */
@RunWith(AndroidJUnit4::class)
class SystemSettingsTest {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val application = ApplicationProvider.getApplicationContext<LuncherApplication>()
    private val systemSettingsEntry = By.pkg(LUNCHER).text(application.getString(R.string.settings_system))

    @After
    fun closeThePanel() {
        if (device.hasObject(systemSettingsEntry)) device.pressBack()
    }

    @Test
    fun systemSettingsEntry_opensTheDeviceSettings_andBackReturnsToThePanel() {
        application.startActivity(Intent(application, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue("The settings panel didn't open", device.wait(Until.hasObject(systemSettingsEntry), TIMEOUT_MS))

        device.pressDPadDown()      // past Hide apps
        device.pressDPadCenter()

        assertTrue("The device's settings didn't open", device.wait(Until.gone(By.pkg(LUNCHER)), TIMEOUT_MS))

        device.pressBack()

        assertTrue("Back didn't return to the panel", device.wait(Until.hasObject(systemSettingsEntry), TIMEOUT_MS))
    }

    private companion object {
        const val LUNCHER = "com.luncher.launcher"
        const val TIMEOUT_MS = 10_000L
    }
}
