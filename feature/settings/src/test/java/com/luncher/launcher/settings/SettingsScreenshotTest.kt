package com.luncher.launcher.settings

import android.os.Looper
import android.view.View
import android.widget.ListView
import com.github.takahirom.roborazzi.captureRoboImage
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.launcher.testing.TV_1080P
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The settings panels alone, in US English, each with its first entry focused. Reference images:
 * src/test/screenshots/settings/ of this module; see docs/TESTING.md.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-$TV_1080P")
class SettingsScreenshotTest {

    @Test
    fun settingsPanel() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

        activity.window.decorView.captureRoboImage("src/test/screenshots/settings/settings_panel.png")
    }

    // Nine apps, two of them hidden, and a name too long for its row: six and a half rows show, the rest scroll.
    @Test
    fun hideAppsPanel() {
        hideApps().captureRoboImage("src/test/screenshots/settings/settings_hide_apps.png")
    }

    // 720p at the 1080p density, 640x360 dp: as many rows as leave the panel its margin, still
    // ending on half a row.
    @Test
    @Config(qualifiers = "+w640dp-h360dp")
    fun hideAppsPanelOnASmallScreen() {
        hideApps().captureRoboImage("src/test/screenshots/settings/settings_hide_apps_small_screen.png")
    }

    /** Hide apps with the apps of [hideAppsPanel], the first one selected: its window. */
    private fun hideApps(): View {
        val application = RuntimeEnvironment.getApplication() as SettingsTestApplication
        val names = listOf("games", "movies", "music", "news", "photos", "radio", "sports", "weather", "an app name far too long for its row")
        application.graph = object : TestSettingsGraph() {
            override val installedApps = FakeInstalledApps(names.map(::app))
            override val appArrangements = FakeAppArrangements(
                AppArrangement(order = null, hidden = listOf(app("an app name far too long for its row"), app("music")).map { it.launchable }),
            )
        }
        val activity = Robolectric.buildActivity(HideAppsActivity::class.java).setup().get()
        // Robolectric starts in touch mode, where a list selects nothing; a D-pad press leaves it.
        val list = activity.findViewById<ListView>(R.id.settings_apps)
        list.requestFocusFromTouch()
        list.setSelection(0)
        shadowOf(Looper.getMainLooper()).idle()
        return activity.window.decorView
    }
}
