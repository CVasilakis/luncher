package com.luncher.launcher.settings

import com.github.takahirom.roborazzi.captureRoboImage
import com.luncher.launcher.TV_1080P
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The settings panel alone, in US English, with its first entry focused. Reference images:
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
}
