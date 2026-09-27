package com.luncher.launcher.settings

import android.os.Looper
import android.widget.ListView
import com.github.takahirom.roborazzi.captureRoboImage
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.R
import com.luncher.launcher.TV_1080P
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
 * app/src/test/screenshots/settings/; see docs/TESTING.md.
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
        val application = RuntimeEnvironment.getApplication() as LuncherApplication
        val names = listOf("games", "movies", "music", "news", "photos", "radio", "sports", "weather", "an app name far too long for its row")
        application.graph = object : AppGraph(application) {
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

        activity.window.decorView.captureRoboImage("src/test/screenshots/settings/settings_hide_apps.png")
    }
}
