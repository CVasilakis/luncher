package com.luncher.launcher.system

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
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
import com.luncher.launcher.settings.SettingsActivity
import com.luncher.launcher.testing.longPressOk
import com.luncher.launcher.testing.resolvedActivity
import com.luncher.launcher.testing.resolvedHome
import com.luncher.launcher.testing.waitForFocus
import com.luncher.launcher.testing.waitForHomeScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Luncher as the device's home screen, across apps: real Home and Back keys, real task switches.
 *
 * Without [com.luncher.launcher.testing.RetryWhenCovered], unlike the other tests that open
 * screens: the stock launcher can't cover these tests. Their setup disables it, which stops it,
 * and presses Home until Luncher is in front, settled, so it also undoes a cover that came before;
 * from then on Luncher is the home app, until the cleanup brings the stock launcher back itself.
 */
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
     * Undoes what the test changed, and makes Android save it before the test ends: an emulator
     * stopped right after the run (`adb emu kill`, which doesn't shut Android down) would otherwise
     * boot with the test's state, e.g. without its stock launcher. Android writes a changed app
     * state up to 10 s later, and a changed setting about 0.2 s later. Even a written file is lost
     * for up to 5 s more: Android keeps the old file as a backup until the new one is complete,
     * and until the filesystem's journal has recorded that, a boot reads the backup
     * (docs/TESTING.md). So Android is told to write the app states at once ([writeHomeAppsNow]),
     * and from API 33 on, where that's only seen in its log, that's awaited
     * ([waitUntilHomeAppsWritten]); the setting is awaited ([waitUntilSettingWritten]); and then
     * `sync` commits the journal.
     *
     * Presses Home once the other home apps are back, so the stock launcher starts now, cold, and
     * not when the runner closes Luncher after the test, while the next test starts its own
     * activity (which the launcher's windows then covered, see [waitForHomeScreen]). Before
     * tv_user_setup_complete is put back, which on API 26 and 27 can make Android ignore Home.
     * Ends on the settled home screen. A screen the stock launcher opens over itself later, after
     * its cold start (API 36's promotion, Google TV's profile chooser), can come over the next
     * test, which [com.luncher.launcher.testing.RetryWhenCovered] then runs once more.
     */
    @After
    fun restoreDevice() {
        disabledHomes.forEach { device.executeShellCommand("pm enable $it") }
        val homesEnabledAt = System.currentTimeMillis()
        if (disabledHomes.isNotEmpty()) waitUntilTheClockHasPassed(homesEnabledAt)
        val homesWritten = disabledHomes.isEmpty() || writeHomeAppsNow()
        if (disabledHomes.isNotEmpty()) device.pressHome()
        val tvSetupComplete = tvSetupCompleteBefore
        val writtenBefore = tvSetupComplete?.let { settingsWritten() }
        when (tvSetupComplete) {
            null -> Unit
            "null" -> device.executeShellCommand("settings delete secure $TV_SETUP_COMPLETE")
            else -> device.executeShellCommand("settings put secure $TV_SETUP_COMPLETE $tvSetupComplete")
        }
        if (disabledHomes.isEmpty() && tvSetupComplete == null) return
        waitForHomeScreen()
        if (!homesWritten) waitUntilHomeAppsWritten(homesEnabledAt)
        writtenBefore?.let { waitUntilSettingWritten(it) }
        device.executeShellCommand("sync")
    }

    /**
     * Waits until the device's clock has passed [changedAt] by [LOGGED_TIME_ERROR_MS], so that the
     * write [writeHomeAppsNow] causes is logged as starting after [changedAt], as
     * [waitUntilHomeAppsWritten] requires. The logged start is computed from two values cut to the
     * millisecond, the event's time and the write's duration (the difference of two readings of
     * the uptime clock), so it can come out up to 2 ms before the write really began. On API 34 the
     * write starts within a millisecond of the call, and was once logged as starting in the very
     * millisecond [changedAt] was read: the wait rejected it and waited 10 s for Android's own
     * write, which a compile of Google Play services had held back, and would have failed after
     * [SAVE_TIMEOUT_MS] had the compile lasted longer. Accepting a start equal to [changedAt]
     * instead would also accept a write that began in that millisecond before the change. Once the
     * clock is 2 ms past [changedAt], every write that begins is logged as starting after it. That
     * takes 2 ms; the limit only guards against a clock set back meanwhile, after which the test
     * waits for Android's own write instead.
     */
    private fun waitUntilTheClockHasPassed(changedAt: Long) {
        val deadline = SystemClock.uptimeMillis() + CLOCK_WAIT_LIMIT_MS
        while (System.currentTimeMillis() < changedAt + LOGGED_TIME_ERROR_MS && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(1)
        }
    }

    /**
     * Has Android write the apps' enabled states now, rather than 10 s later, or minutes later
     * while its package manager is busy (on a starved Google TV API 33, while it compiled an
     * update of Google Play services that Play Store had just installed, it wrote nothing for
     * over 3 minutes). Returns whether Android said it wrote them.
     *
     * Up to API 31: `dumpsys package write`, which answers "Settings written.".
     *
     * From API 33 on that command writes nothing (it prints the whole dump), and no shell command
     * writes at once. What remains is a flag an app can pass when it changes the enabled state of
     * its own components, PackageManager.SYNCHRONOUS: Android then writes at once. It writes the
     * whole file of the user's app states (package-restrictions.xml), every app in it, so the
     * restored home apps' state goes with it. The test can't pass that flag for the stock
     * launchers themselves: package visibility hides them from Luncher's PackageManager
     * ("Unknown package"), and the shell's permissions don't change that. So it changes the state
     * of one of Luncher's own activities, [SettingsActivity], from default to explicitly enabled,
     * and back to default, each with the flag. That's harmless: an activity enabled by default and
     * one enabled explicitly behave the same, `DONT_KILL_APP` keeps Android from stopping Luncher
     * (and with it this test), and the activity's state ends as it was, the default, which
     * `dumpsys package com.luncher.launcher.debug` shows as no enabled or disabled component entry.
     * API 33 writes before the call returns, on the calling thread, even while the thread that
     * makes Android's own writes is busy; later levels start the write at once on a thread of
     * their own. Android logs the write, which [waitUntilHomeAppsWritten] then finds, so this
     * returns false there.
     *
     * If a future Android stops writing the whole file for such a change (e.g. writes only the
     * app whose state changed), the logged write would no longer prove the home apps saved, and
     * the wait would pass anyway. Check it as it was found: re-enable a disabled stock launcher,
     * do this, kill the emulator at once (`adb emu kill`), boot it, and see whether the launcher
     * is enabled. If not, drop this and wait for Android's own write instead (raise
     * [SAVE_TIMEOUT_MS] to minutes, for the case above).
     *
     * Before the setting is restored: the write changes the folder [waitUntilSettingWritten]
     * watches. (From API 28 on, the setting is already set, so it isn't restored.)
     */
    private fun writeHomeAppsNow(): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            val packageManager = InstrumentationRegistry.getInstrumentation().targetContext.packageManager
            val writeTrigger = ComponentName(LUNCHER, SettingsActivity::class.java.name)
            // Without DONT_KILL_APP, Android would stop Luncher, and with it this test.
            val flags = PackageManager.DONT_KILL_APP or PackageManager.SYNCHRONOUS
            packageManager.setComponentEnabledSetting(writeTrigger, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, flags)
            packageManager.setComponentEnabledSetting(writeTrigger, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, flags)
            return false
        }
        return device.executeShellCommand("dumpsys package write").startsWith("Settings written.")
    }

    /**
     * Waits until Android has written the apps' enabled states, which it logs from API 28 on: a
     * `commit_sys_config_file` event for package-user-0 in the events log, once the file is
     * complete, with how long the write took. Only a write that began after [changedAt] (the
     * device's clock, in ms) has the restored states. Fails after [SAVE_TIMEOUT_MS], saying what
     * that means: the restored apps are enabled, but a device stopped without Android's save
     * boots without them.
     */
    private fun waitUntilHomeAppsWritten(changedAt: Long) {
        // Gradle doesn't show a test's output, so this is for whoever reads logcat.
        Log.i(TAG, "Waiting until Android has written the restored home apps, in a write begun after $changedAt")
        val deadline = SystemClock.uptimeMillis() + SAVE_TIMEOUT_MS
        while (lastWriteStart(PACKAGE_STATES_FILE)?.let { it > changedAt } != true) {
            if (SystemClock.uptimeMillis() > deadline) {
                throw AssertionError(
                    "Android didn't write the restored home apps (${disabledHomes.joinToString()}) within " +
                        "${SAVE_TIMEOUT_MS / 1000} s. They're enabled again, but until Android has written that, a device " +
                        "stopped without shutting Android down (adb emu kill) boots without them, and so without its " +
                        "stock launcher. Don't stop it that way yet: Android writes them later by itself, which " +
                        "`adb logcat -b events -d -s $COMMIT_EVENT` shows as a new [$PACKAGE_STATES_FILE,…] line; " +
                        "then run `adb shell sync`, or stop the emulator with android-tv-wsl-dev-tools' stop-emulator.sh.",
                )
            }
            SystemClock.sleep(POLL_MS)
        }
    }

    /**
     * When Android began its last write of the system file it logs as [file] (device clock, ms),
     * from the events log's `commit_sys_config_file` events ("[package-user-0,5]": the file, and
     * the write's duration in ms), or null if there's none.
     */
    private fun lastWriteStart(file: String): Long? {
        val event = device.executeShellCommand("logcat -b events -d -v epoch -s $COMMIT_EVENT")
            .lines().lastOrNull { "[$file," in it } ?: return null
        val time = event.trim().substringBefore(' ').toDoubleOrNull() ?: return null
        val tookMs = event.substringAfter("[$file,").substringBefore(']').trim().toLongOrNull() ?: 0
        return (time * 1000).toLong() - tookMs
    }

    /**
     * Waits until Android has written the restored setting, i.e. until the modification time of
     * the folder its file is in ([settingsWritten]) differs from [before], read before the
     * change: the file can't be read without root, and no command writes settings at once. Any
     * other file written there meanwhile would end the wait too; [writeHomeAppsNow] has written
     * the app states before, so that's left to the writes Android makes by itself.
     */
    private fun waitUntilSettingWritten(before: String) {
        Log.i(TAG, "Waiting until Android has written the restored $TV_SETUP_COMPLETE")
        val deadline = SystemClock.uptimeMillis() + SAVE_TIMEOUT_MS
        while (settingsWritten() == before) {
            if (SystemClock.uptimeMillis() > deadline) {
                throw AssertionError("Android didn't write the restored $TV_SETUP_COMPLETE within ${SAVE_TIMEOUT_MS / 1000} s")
            }
            SystemClock.sleep(POLL_MS)
        }
    }

    /** When something was last written in the folder of user 0's settings files, as `stat` says it. */
    private fun settingsWritten(): String {
        val modified = device.executeShellCommand("stat -c %y $SETTINGS_FOLDER").trim()
        check(modified.firstOrNull()?.isDigit() == true) { "can't see when Android writes settings: '$modified'" }
        return modified
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
        /** The app under test, e.g. com.luncher.launcher.debug (a debug build's ID). */
        val LUNCHER: String = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val ARRANGE_TITLE: BySelector = By.res(LUNCHER, "home_arrange_title")
        const val TAG = "HomeKeyTest"

        /**
         * For UI Automator to see a screen. On an emulator starved of CPU, Luncher's settings panel
         * showed 5.7 s after its start.
         */
        const val TIMEOUT_MS = 30_000L

        const val MAX_OTHER_HOMES = 5
        const val HOME_TRIES = 3
        const val TV_SETUP_COMPLETE = "tv_user_setup_complete"

        /** Where Android keeps user 0's settings files (and its apps' enabled states). */
        const val SETTINGS_FOLDER = "/data/system/users/0"

        /** The events log's tag for Android's writes of its system files, and the app states' file. */
        const val COMMIT_EVENT = "commit_sys_config_file"
        const val PACKAGE_STATES_FILE = "package-user-0"

        /**
         * For Android to write restored app states (at once when told to, else up to 10 s after
         * the change) or a restored setting (about 0.2 s after it).
         */
        const val SAVE_TIMEOUT_MS = 30_000L
        const val POLL_MS = 100L

        /**
         * How much earlier than it began a write can be logged as starting: its event's time and
         * its duration are each cut to the millisecond ([waitUntilTheClockHasPassed]).
         */
        const val LOGGED_TIME_ERROR_MS = 2L
        const val CLOCK_WAIT_LIMIT_MS = 1_000L
    }
}
