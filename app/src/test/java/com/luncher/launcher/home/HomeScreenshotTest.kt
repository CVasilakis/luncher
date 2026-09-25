package com.luncher.launcher.home

import com.github.takahirom.roborazzi.captureRoboImage
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.TV_1080P
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reference images live in app/src/test/screenshots/<feature>/. `recordRoborazziDebug` writes
 * them, `verifyRoborazziDebug` fails when the screen looks different; see TESTING.md.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = TV_1080P)
class HomeScreenshotTest {

    @Test
    fun homeScreen() {
        val application = RuntimeEnvironment.getApplication() as LuncherApplication
        application.graph = object : AppGraph(application) {
            override val installedApps = FakeInstalledApps(app("movies"), app("music"))
        }
        val activity = Robolectric.buildActivity(HomeActivity::class.java).setup().get()

        activity.window.decorView.captureRoboImage("src/test/screenshots/home/home_screen.png")
    }
}
