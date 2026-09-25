package com.luncher.launcher.system

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
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

    /**
     * Another home app (e.g. the stock launcher, whose HOME filter has a higher priority) keeps
     * Luncher from being the home screen, so disable those for the test. [restoreOtherHomes]
     * re-enables them, leaving the device as it was.
     */
    @Before
    fun makeLuncherTheHome() {
        repeat(MAX_OTHER_HOMES) {
            val home = resolvedHome()
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

    @After
    fun restoreOtherHomes() {
        disabledHomes.forEach { device.executeShellCommand("pm enable $it") }
    }

    @Test
    fun homeKey_returnsToLuncherFromAnotherApp() {
        device.executeShellCommand("am start -W -a android.settings.SETTINGS")
        assertTrue("Settings didn't open", device.wait(Until.gone(By.pkg(LUNCHER)), TIMEOUT_MS))

        device.pressHome()

        assertTrue("Luncher isn't in front", device.wait(Until.hasObject(By.pkg(LUNCHER)), TIMEOUT_MS))
    }

    /** Package of the activity Android starts for Home ("android" means it would ask). */
    private fun resolvedHome(): String =
        device.executeShellCommand(
            "cmd package resolve-activity --brief -a android.intent.action.MAIN " +
                "-c android.intent.category.HOME",
        ).lines().last { it.isNotBlank() }.substringBefore('/')

    private companion object {
        const val LUNCHER = "com.luncher.launcher"
        const val TIMEOUT_MS = 10_000L
        const val MAX_OTHER_HOMES = 5
    }
}
