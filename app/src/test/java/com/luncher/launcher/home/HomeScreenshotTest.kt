package com.luncher.launcher.home

import android.view.KeyEvent
import android.view.View
import com.github.takahirom.roborazzi.captureRoboImage
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.clock.FakeClock
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.R
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

    private fun start(hidden: List<String> = emptyList()): HomeActivity {
        val application = RuntimeEnvironment.getApplication() as LuncherApplication
        application.graph = object : AppGraph(application) {
            // Seven apps shown: a full row, and a second one that starts at the left.
            override val installedApps = FakeInstalledApps(
                (listOf("games", "movies", "music", "news", "photos", "radio", "an app name too long for its tile") + hidden)
                    .map(::app),
            )
            override val appArrangements = FakeAppArrangements(AppArrangement(order = null, hidden = hidden.map { app(it).launchable }))
            override val clock = FakeClock()
        }
        return Robolectric.buildActivity(HomeActivity::class.java).setup().get()
    }

    /** Starts arranging as a long press of OK on the tile of [label] would. */
    private fun HomeActivity.arrange(label: String) {
        val tiles = findViewById<AppTilesView>(R.id.home_apps)
        val tile = (0 until tiles.childCount).map { tiles.getChildAt(it) as AppTileView }.single { it.app.label == label }
        tile.requestFocus()
        tile.performLongClick()
    }

    @Test
    fun homeScreen() {
        start().window.decorView.captureRoboImage("src/test/screenshots/home/home_screen.png")
    }

    // A 4:3 screen, 720x540 dp: four tiles in a row, of about the size they have at 1080p.
    @Test
    @Config(qualifiers = "+w720dp")
    fun homeScreen4by3() {
        start().window.decorView.captureRoboImage("src/test/screenshots/home/home_screen_4by3.png")
    }

    // 1080p at 160 dpi, as some TV boxes are set, 1920x1080 dp: ten tiles in a row, of about the
    // size they have next to the clock at 1080p.
    @Test
    @Config(qualifiers = "+w1920dp-h1080dp-mdpi")
    fun homeScreen160dpi() {
        start().window.decorView.captureRoboImage("src/test/screenshots/home/home_screen_160dpi.png")
    }

    @Test
    fun settingsEntryFocused() {
        val activity = start()
        activity.findViewById<View>(R.id.home_settings).requestFocus()

        activity.window.decorView.captureRoboImage("src/test/screenshots/home/home_settings_focused.png")
    }

    // Holding Music, one app on the shelf.
    @Test
    fun arrangingHoldingAnApp() {
        val activity = start(hidden = listOf("weather"))
        activity.arrange("Music")

        activity.window.decorView.captureRoboImage("src/test/screenshots/home/home_arranging_holding.png")
    }

    // Having put Music down: the focus only, and the empty slot of a shelf with nothing on it.
    @Test
    fun arrangingNothingHidden() {
        val activity = start()
        activity.arrange("Music")
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))

        activity.window.decorView.captureRoboImage("src/test/screenshots/home/home_arranging_empty_shelf.png")
    }
}
