package com.luncher.launcher.home

import com.github.takahirom.roborazzi.captureRoboImage
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.clock.FakeClock
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
 * them, `verifyRoborazziDebug` fails when the screen looks different; see docs/TESTING.md.
 *
 * The fake apps aren't installed, so every tile shows the card Luncher draws for an app without a
 * banner, with Android's default icon. The clock is fixed at 14:05 on Sunday, 27 September 2026,
 * shown in US English.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-$TV_1080P")
class HomeScreenshotTest {

    @Test
    fun homeScreen() {
        val application = RuntimeEnvironment.getApplication() as LuncherApplication
        application.graph = object : AppGraph(application) {
            // Seven apps: a full row, and a second one that starts at the left.
            override val installedApps = FakeInstalledApps(
                listOf("games", "movies", "music", "news", "photos", "radio", "an app name too long for its tile").map(::app),
            )
            override val clock = FakeClock()
        }
        val activity = Robolectric.buildActivity(HomeActivity::class.java).setup().get()

        activity.window.decorView.captureRoboImage("src/test/screenshots/home/home_screen.png")
    }
}
