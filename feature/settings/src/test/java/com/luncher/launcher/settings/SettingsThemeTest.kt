package com.luncher.launcher.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.AppearanceTopic
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.appearanceChanges
import com.luncher.domain.appearance.appearanceOptions
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The catalog-wide check (docs/ARCHITECTURE.md#appearance) for the options the home screen and
 * the preview don't show (the theme's mode): each value changes what a settings screen draws,
 * the appearance editor's, which shows a focused entry, a button and a container.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-$TV_1080P")
class SettingsThemeTest {

    private val settings = FakeAppearanceSettings()

    private fun draw(appearance: Appearance): Bitmap {
        settings.appearance = appearance
        val application = RuntimeEnvironment.getApplication() as SettingsTestApplication
        application.graph = object : TestSettingsGraph() {
            override val appearanceSettings = this@SettingsThemeTest.settings
        }
        val activity = Robolectric.buildActivity(AppearanceActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        val window = activity.window.decorView
        return Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888).also { window.draw(Canvas(it)) }
    }

    @Test
    fun `every value of the options the home screen doesn't show changes what the settings draw`() {
        val changes = appearanceChanges().filter { !it.option.showsOnHome }
        assertFalse("no option to check", changes.isEmpty())
        for (change in changes) {
            assertFalse("$change draws as ${change.option} at its default", draw(change.base).sameAs(draw(change.changed)))
        }
    }

    // The theme's options are its group's: the accent shows on the home screen too, the mode only here.
    @Test
    fun `only the theme's options leave the home screen as it is`() {
        val theme = appearanceOptions().groups.single { it.topic == AppearanceTopic.THEME }.options
        assertFalse(appearanceOptions().all.any { !it.showsOnHome && it !in theme })
    }
}
