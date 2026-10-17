package com.luncher.launcher.settings

import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.github.takahirom.roborazzi.captureRoboImage
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.HorizontalAlignment
import com.luncher.domain.appearance.Side
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.TileStyle
import com.luncher.domain.appearance.TopBarAppearance
import com.luncher.domain.appearance.VerticalAlignment
import com.luncher.domain.appearance.appearanceChanges
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.SAMPLE_WALLPAPER
import com.luncher.domain.pictures.Blur
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

    // 720p at the 1080p density, 640x360 dp, the shortest TV screen: all three entries fit.
    @Test
    @Config(qualifiers = "+w640dp-h360dp")
    fun settingsPanelOnASmallScreen() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

        activity.window.decorView.captureRoboImage("src/test/screenshots/settings/settings_panel_small_screen.png")
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

    @Test
    fun appearanceEditor() {
        appearanceEditor(Appearance()).window.decorView.captureRoboImage("src/test/screenshots/settings/settings_appearance.png")
    }

    // Every option away from its default but the time's and the date's, which would hide what the
    // others move; the rows centered, so the full bottom row can be changed. The tiles' group
    // open, the focus on the corner radius.
    @Test
    fun appearanceEditorChanged() {
        val activity = appearanceEditor(
            Appearance(
                TileGeometry(verticalAlignment = VerticalAlignment.CENTER, horizontalAlignment = HorizontalAlignment.CENTER, fullBottomRow = true),
                TileStyle(Corners.Radius(16)),
                TopBarAppearance(clockSide = Side.RIGHT, timeSide = Side.RIGHT),
            ),
            open = "Tiles",
        )
        activity.child { it.findViewById<TextView>(R.id.settings_option_label)?.text == "Corner radius" }.requestFocus()

        activity.window.decorView.captureRoboImage("src/test/screenshots/settings/settings_appearance_changed.png")
    }

    // The top bar's group open, the date hidden: the order, which needs both, dimmed.
    @Test
    fun appearanceEditorTopBar() {
        val activity = appearanceEditor(Appearance(topBar = TopBarAppearance(showDate = false)), open = "Status bar")
        activity.child { it.findViewById<TextView>(R.id.settings_option_label)?.text == "Date" }.requestFocus()

        activity.window.decorView.captureRoboImage("src/test/screenshots/settings/settings_appearance_top_bar.png")
    }

    // 720p at the 1080p density, 640x360 dp: the options take half the screen, and scroll.
    @Test
    @Config(qualifiers = "+w640dp-h360dp")
    fun appearanceEditorOnASmallScreen() {
        appearanceEditor(Appearance(), open = "Tiles").window.decorView
            .captureRoboImage("src/test/screenshots/settings/settings_appearance_small_screen.png")
    }

    // Arabic: mirrored, the options at the left; the preview's content still left to right.
    @Test
    @Config(qualifiers = "ar-rEG-$TV_1080P")
    fun appearanceEditorInArabic() {
        appearanceEditor(Appearance(), open = "المربعات").window.decorView
            .captureRoboImage("src/test/screenshots/settings/settings_appearance_arabic.png")
    }

    // With a picture, dimmed and blurred: the preview draws it under the tiles. The focus on the
    // background's entry, the picture's name beside it.
    @Test
    fun appearanceEditorBackground() {
        val activity = appearanceEditor(Appearance(background = Background(picture = SAMPLE_WALLPAPER, dim = 20, blur = Blur.LIGHT)))
        activity.child { it.findViewById<View>(R.id.settings_apart_value) != null }.requestFocus()

        activity.window.decorView.captureRoboImage("src/test/screenshots/settings/settings_appearance_background.png")
    }

    // The preview alone, with the defaults and with each value of each option: a loop over the
    // catalog, so a new option's values get images too.
    @Test
    fun appearancePreview() {
        val preview = appearanceEditor(Appearance()).findViewById<AppearancePreviewView>(R.id.settings_appearance_preview)
        preview.captureRoboImage("src/test/screenshots/settings/preview/default.png")
        for (change in appearanceChanges()) {
            preview.appearance = change.changed
            drawWallpaper()
            val name = "${change.option}_${change.value}".lowercase()
            preview.captureRoboImage("src/test/screenshots/settings/preview/$name.png")
        }
    }

    /** The preview's wallpaper, drawn in the background, and shown. */
    private fun drawWallpaper() {
        ((RuntimeEnvironment.getApplication() as SettingsTestApplication).graph as TestSettingsGraph).jobs.runAll()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** The appearance editor showing [appearance], with the group titled [open] open and focused, or the first focused. */
    private fun appearanceEditor(appearance: Appearance, open: String? = null): AppearanceActivity {
        val application = RuntimeEnvironment.getApplication() as SettingsTestApplication
        application.graph = object : TestSettingsGraph() {
            override val appearanceSettings = FakeAppearanceSettings(appearance)
        }
        val activity = Robolectric.buildActivity(AppearanceActivity::class.java).setup().get()
        drawWallpaper()
        if (open != null) {
            activity.child { it.findViewById<TextView>(R.id.settings_group_title)?.text == open }.performClick()
            shadowOf(Looper.getMainLooper()).idle()   // the layout pass, for the options it shows
        }
        return activity
    }

    /** The editor's entry that [matches]. */
    private fun AppearanceActivity.child(matches: (View) -> Boolean): View {
        val options = findViewById<LinearLayout>(R.id.settings_appearance_options)
        return (0 until options.childCount).map { options.getChildAt(it) }.single(matches)
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
