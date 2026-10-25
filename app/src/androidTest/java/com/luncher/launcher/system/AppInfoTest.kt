package com.luncher.launcher.system

import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.home.HomeActivity
import com.luncher.launcher.home.R
import com.luncher.launcher.testing.RetryWhenCovered
import com.luncher.launcher.testing.focus
import com.luncher.launcher.testing.longPressOk
import com.luncher.launcher.testing.resolvedActivity
import com.luncher.launcher.testing.sayingWhetherItCrashed
import com.luncher.launcher.testing.waitForDialogFocus
import com.luncher.launcher.testing.waitForFocus
import com.luncher.launcher.testing.waitForHomeScreen
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * App info in a tile's menu: the app's page in the device's settings, and back. Luncher isn't the
 * home app in this tier, so the test starts the home screen itself, as an app.
 */
@RunWith(AndroidJUnit4::class)
class AppInfoTest {

    @get:Rule
    val retryWhenCovered = RetryWhenCovered()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val application = ApplicationProvider.getApplicationContext<LuncherApplication>()

    /** The device's settings app, whose page about an app App info opens. */
    private val settings = checkNotNull(resolvedActivity(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, data = "package:$LUNCHER")) {
        "nothing opens an app's page in the device's settings"
    }.substringBefore('/')

    /** Not while the home app is still starting, which can open windows over the home screen. */
    @Before
    fun startFromTheHomeScreen() {
        waitForHomeScreen()
    }

    /**
     * Back from the app's page where a failure left it in front, so the next test starts from the
     * home screen. Luncher's own activities the runner closes after the test.
     */
    @After
    fun closeTheAppsPage() {
        if (focus().isIn(settings)) device.pressBack()
    }

    @Test
    fun appInfo_opensTheAppsPageInTheDeviceSettings_andBackReturnsToTheHomeScreen() {
        val startedAt = System.currentTimeMillis()
        application.startActivity(Intent(application, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        // A focused tile: laid out, so a long press reaches it (HomeKeyTest's homeKey_closesTheTilesMenu).
        assertTrue("No tile has the focus", device.wait(Until.hasObject(By.pkg(LUNCHER).res(LUNCHER, "home_apps").hasDescendant(By.focused(true))), TIMEOUT_MS))
        waitForFocus(HomeActivity::class.java)
        val label = checkNotNull(device.findObject(By.pkg(LUNCHER).focused(true)).contentDescription) { "the focused tile has no name" }
        val app = packageOf(label)
        val home = focus()

        longPressOk()   // on the focused tile
        assertTrue("The tile's menu didn't open", device.wait(Until.hasObject(TILE_MENU), TIMEOUT_MS))
        waitForDialogFocus(home)
        // Past Hide, Rename and Tile banner, and Move, offered only with another app shown: the device's apps are what they are.
        repeat(if (device.hasObject(entry(R.string.home_menu_move))) 4 else 3) { device.pressDPadDown() }
        // The entry has the focus, not its label.
        assertTrue("App info didn't get the focus", device.wait(Until.hasObject(By.pkg(LUNCHER).focused(true).hasChild(entry(R.string.home_menu_info))), TIMEOUT_MS))
        device.pressDPadCenter()

        sayingWhetherItCrashed(settings, startedAt) {
            // Its page, which lists its package below its version. Not by its name: the page's title
            // is the application's label, the tile's its activity's ("Play Games", "Google Play Games").
            assertTrue("The page of $app didn't open", device.wait(Until.hasObject(By.pkg(settings).text(app)), TIMEOUT_MS))
            waitForFocus(settings)                   // Back goes there
        }

        device.pressBack()

        assertTrue("Back didn't return to the home screen", device.wait(Until.hasObject(By.pkg(LUNCHER).res(LUNCHER, "home_apps")), TIMEOUT_MS))
        waitForFocus(HomeActivity::class.java)
    }

    /** The package of the TV app whose tile is named [label], as the home screen lists it. */
    private fun packageOf(label: String): String {
        val packages = application.packageManager
        val tvApps = packages.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
        return tvApps.first { it.loadLabel(packages).toString() == label }.activityInfo.packageName
    }

    private fun entry(label: Int): BySelector = By.pkg(LUNCHER).text(application.getString(label))

    private companion object {
        /** The app under test, e.g. com.luncher.launcher.debug (a debug build's ID). */
        val LUNCHER: String = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val TILE_MENU: BySelector = By.res(LUNCHER, "home_tile_menu")

        /** For UI Automator to see a screen, as SystemSettingsTest's. */
        const val TIMEOUT_MS = 30_000L
    }
}
