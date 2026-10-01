package com.luncher.launcher.system

import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.luncher.launcher.home.HomeActivity
import com.luncher.launcher.longPressOk
import com.luncher.launcher.resolvedActivity
import com.luncher.launcher.resolvedHome
import com.luncher.launcher.waitForFocus
import com.luncher.launcher.waitForHomeScreen
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
     * Makes Luncher the home screen and starts each test with it in front, settled. Starts from the
     * device's home screen, settled (like every instrumented test: [waitForHomeScreen]).
     * [restoreDevice] undoes the changes, leaving the device as it was.
     */
    @Before
    fun makeLuncherTheHome() {
        waitForHomeScreen()
        markTvSetupComplete()
        disableOtherHomes()
        assertEquals("Luncher must be the home app", LUNCHER, resolvedHome())
        showLuncher()
    }

    /**
     * Another home app (e.g. the stock launcher, whose HOME filter has a higher priority) keeps
     * Luncher from being the home screen, so disable those for the test.
     */
    private fun disableOtherHomes() {
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
    }

    /**
     * Android TV 8.0 and 8.1 (API 26, 27) ignore the Home key until the TV setup wizard has set
     * tv_user_setup_complete ("Not starting activity because user setup is in progress"). Real
     * TVs have it set, but the emulator images never run that wizard. [restoreDevice] puts back
     * the value it had. Before [showLuncher]'s Home.
     */
    private fun markTvSetupComplete() {
        val value = device.executeShellCommand("settings get secure $TV_SETUP_COMPLETE").trim()
        if (value == "1") return
        tvSetupCompleteBefore = value
        device.executeShellCommand("settings put secure $TV_SETUP_COMPLETE 1")
    }

    /**
     * Home, until Luncher has the focus, then waits until it's settled. Android closes a disabled
     * home app asynchronously, and an app started meanwhile can be lost: on API 30, Settings started
     * 80 ms after the stock launcher was disabled never showed, nor did anything else, for 10 s.
     * So the tests open other apps only from Luncher, settled, and the first Home is pressed again
     * if it's lost the same way.
     */
    private fun showLuncher() {
        repeat(HOME_TRIES) { attempt ->
            device.pressHome()
            try {
                waitForFocus(HomeActivity::class.java)
                waitForHomeScreen()
                return
            } catch (e: AssertionError) {
                if (attempt == HOME_TRIES - 1) throw e
            }
        }
    }

    /**
     * Undoes what the test changed. Android saves such changes seconds later, and an emulator
     * stopped before that (`adb emu kill` right after the run) boots with the test's state
     * instead, e.g. without its stock launcher. So after a restore, wait until it's saved; no
     * condition to wait for is visible without root, hence the fixed time (docs/TESTING.md).
     *
     * Presses Home once the other home apps are back, so the stock launcher starts now, cold, and
     * not when the runner closes Luncher after the test, while the next test starts its own
     * activity (which the launcher's windows then covered, see [waitForHomeScreen]). Before
     * tv_user_setup_complete is put back, which on API 26 and 27 can make Android ignore Home.
     * Ends on the settled home screen.
     */
    @After
    fun restoreDevice() {
        disabledHomes.forEach { device.executeShellCommand("pm enable $it") }
        if (disabledHomes.isNotEmpty()) device.pressHome()
        when (val value = tvSetupCompleteBefore) {
            null -> Unit
            "null" -> device.executeShellCommand("settings delete secure $TV_SETUP_COMPLETE")
            else -> device.executeShellCommand("settings put secure $TV_SETUP_COMPLETE $value")
        }
        if (disabledHomes.isEmpty() && tvSetupCompleteBefore == null) return
        // Gradle doesn't show a test's output, so this is for whoever reads logcat.
        Log.i(TAG, "Waiting ${SAVE_DELAY_MS / 1000} s so Android saves the restored home apps and settings")
        SystemClock.sleep(SAVE_DELAY_MS)
        waitForHomeScreen()
    }

    @Test
    fun homeKey_returnsToLuncherFromAnotherApp() {
        val settings = checkNotNull(resolvedActivity(Settings.ACTION_SETTINGS)) { "nothing opens the device's settings" }
            .substringBefore('/')
        device.executeShellCommand("am start -W -a ${Settings.ACTION_SETTINGS}")
        assertTrue("Settings didn't open", device.wait(Until.hasObject(By.pkg(settings)), TIMEOUT_MS))
        waitForFocus(settings)   // so Home comes from there

        device.pressHome()

        assertTrue("Luncher isn't in front", device.wait(Until.hasObject(By.pkg(LUNCHER)), TIMEOUT_MS))
    }

    @Test
    fun homeKey_endsArrangeMode() {
        // Luncher is in front, settled (makeLuncherTheHome); a Home now could reach it late and end
        // the mode the long press starts. Seen with a focused tile, so it's laid out (its window
        // can get the focus before that, and a long press sent then was lost), and its window has
        // the focus: keys go there.
        assertTrue("Luncher isn't in front", device.wait(Until.hasObject(By.pkg(LUNCHER).focused(true)), TIMEOUT_MS))
        waitForFocus(HomeActivity::class.java)
        longPressOk()   // on the focused app
        assertTrue("Arrange mode didn't start", device.wait(Until.hasObject(ARRANGE_TITLE), TIMEOUT_MS))

        device.pressHome()

        assertTrue("Arrange mode didn't end", device.wait(Until.gone(ARRANGE_TITLE), TIMEOUT_MS))
        assertTrue("The settings entry didn't come back", device.hasObject(By.res(LUNCHER, "home_settings")))
    }

    private companion object {
        val ARRANGE_TITLE: BySelector = By.res(LUNCHER, "home_arrange_title")
        const val TAG = "HomeKeyTest"
        const val LUNCHER = "com.luncher.launcher"
        const val TIMEOUT_MS = 10_000L
        const val MAX_OTHER_HOMES = 5
        const val HOME_TRIES = 3
        const val TV_SETUP_COMPLETE = "tv_user_setup_complete"

        /** How long Android may take to save a changed setting, with a margin. */
        const val SAVE_DELAY_MS = 30_000L
    }
}
