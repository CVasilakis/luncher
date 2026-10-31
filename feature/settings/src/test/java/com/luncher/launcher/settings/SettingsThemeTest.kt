package com.luncher.launcher.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.AppearanceTopic
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.appearanceChanges
import com.luncher.domain.appearance.appearanceOptions
import android.view.ContextThemeWrapper
import com.luncher.launcher.testing.TV_1080P
import com.luncher.launcher.ui.R as UiR
import org.junit.Assert.assertEquals
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

    private fun animationStyle(theme: Int): Int {
        val styled = ContextThemeWrapper(RuntimeEnvironment.getApplication(), theme).theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowAnimationStyle))
        return styled.getResourceId(0, 0).also { styled.recycle() }
    }

    // The same short fade as the panels', whole screens too, with the screen under holding still;
    // never Theme.Luncher's own, which is the home screen's as well.
    @Test
    fun `the full-screen screens come and go with Luncher's short fade, the home screen's theme stays Android's`() {
        assertEquals(UiR.style.Animation_Luncher_Screen, animationStyle(R.style.Theme_Luncher_Settings_FullScreen))
        assertEquals(UiR.style.Animation_Luncher_Screen, animationStyle(R.style.Theme_Luncher_Settings_Pictures))
        assertFalse(animationStyle(UiR.style.Theme_Luncher) == UiR.style.Animation_Luncher_Screen)
        assertEquals("no window at all, so none to animate", 0, animationStyle(R.style.Theme_Luncher_Settings_Pictures_NoDisplay))
    }

    // Their windows play an activity's animation only: with a window's exit too, the fade played
    // twice over on Android 7.0, and the panel jumped back as the first one ended.
    @Test
    fun `the panels that cover the screen have an activity's fade, and no window's`() {
        val style = UiR.style.Animation_Luncher_PanelActivity
        assertEquals(style, animationStyle(R.style.Theme_Luncher_Settings))
        val attributes = intArrayOf(android.R.attr.windowEnterAnimation, android.R.attr.windowExitAnimation)
        val items = RuntimeEnvironment.getApplication().obtainStyledAttributes(style, attributes)
        assertEquals(0, items.getResourceId(0, 0))
        assertEquals(0, items.getResourceId(1, 0))
        items.recycle()
    }

    @Test
    fun `the appearance editor's window plays that fade`() {
        val application = RuntimeEnvironment.getApplication() as SettingsTestApplication
        application.graph = object : TestSettingsGraph() {
            override val appearanceSettings = this@SettingsThemeTest.settings
        }
        val activity = Robolectric.buildActivity(AppearanceActivity::class.java).setup().get()
        assertEquals(UiR.style.Animation_Luncher_Screen, activity.window.attributes.windowAnimations)
    }
}
