package com.luncher.launcher.system

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.luncher.launcher.resolvedHome
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Luncher as the device's home screen, across apps: real Home and Back keys, real task switches. */
@RunWith(AndroidJUnit4::class)
class HomeKeyTest {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val disabledHomes = mutableListOf<String>()
    private var tvSetupCompleteBefore: String? = null

    /**
     * Another home app (e.g. the stock launcher, whose HOME filter has a higher priority) keeps
     * Luncher from being the home screen, so disable those for the test. [restoreDevice]
     * re-enables them, leaving the device as it was.
     */
    @Before
    fun makeLuncherTheHome() {
        repeat(MAX_OTHER_HOMES) {
            val home = checkNotNull(resolvedHome()) { "nothing handles Home" }
            if (home == LUNCHER) return
            check(home != "android") {
                "several home apps have the same priority, so Android asks which one to use; " +
                    "choose Luncher once on the device"
            }
            device.executeShellCommand("pm disable-user --user 0 $home")
            disabledHomes += home
        }
        assertEquals("Luncher must be the home app", LUNCHER, resolvedHome())
    }

    /**
     * Android TV 8.0 and 8.1 (API 26, 27) ignore the Home key until the TV setup wizard has set
     * tv_user_setup_complete ("Not starting activity because user setup is in progress"). Real
     * TVs have it set, but the emulator images never run that wizard. [restoreDevice] puts back
     * the value it had.
     */
    @Before
    fun markTvSetupComplete() {
        val value = device.executeShellCommand("settings get secure $TV_SETUP_COMPLETE").trim()
        if (value == "1") return
        tvSetupCompleteBefore = value
        device.executeShellCommand("settings put secure $TV_SETUP_COMPLETE 1")
    }

    /**
     * Undoes what the test changed. Android saves such changes seconds later, and an emulator
     * stopped before that (`adb emu kill` right after the run) boots with the test's state
     * instead, e.g. without its stock launcher. So after a restore, wait until it's saved; no
     * condition to wait for is visible without root, hence the fixed time (docs/TESTING.md).
     */
    @After
    fun restoreDevice() {
        disabledHomes.forEach { device.executeShellCommand("pm enable $it") }
        when (val value = tvSetupCompleteBefore) {
            null -> Unit
            "null" -> device.executeShellCommand("settings delete secure $TV_SETUP_COMPLETE")
            else -> device.executeShellCommand("settings put secure $TV_SETUP_COMPLETE $value")
        }
        if (disabledHomes.isEmpty() && tvSetupCompleteBefore == null) return
        // Gradle doesn't show a test's output, so this is for whoever reads logcat.
        Log.i(TAG, "Waiting ${SAVE_DELAY_MS / 1000} s so Android saves the restored home apps and settings")
        SystemClock.sleep(SAVE_DELAY_MS)
    }

    @Test
    fun homeKey_returnsToLuncherFromAnotherApp() {
        device.executeShellCommand("am start -W -a android.settings.SETTINGS")
        assertTrue("Settings didn't open", device.wait(Until.gone(By.pkg(LUNCHER)), TIMEOUT_MS))

        device.pressHome()

        assertTrue("Luncher isn't in front", device.wait(Until.hasObject(By.pkg(LUNCHER)), TIMEOUT_MS))
    }

    private companion object {
        const val TAG = "HomeKeyTest"
        const val LUNCHER = "com.luncher.launcher"
        const val TIMEOUT_MS = 10_000L
        const val MAX_OTHER_HOMES = 5
        const val TV_SETUP_COMPLETE = "tv_user_setup_complete"

        /** How long Android may take to save a changed setting, with a margin. */
        const val SAVE_DELAY_MS = 30_000L
    }
}
