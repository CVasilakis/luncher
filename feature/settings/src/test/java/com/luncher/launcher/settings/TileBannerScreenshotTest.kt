package com.luncher.launcher.settings

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.TileStyle
import com.luncher.domain.apps.AppCustomization
import com.luncher.domain.apps.FakeAppCustomizations
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.TileImage
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.FakePictures.Companion.picture
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PicturePosition
import com.luncher.launcher.testing.TV_1080P
import com.luncher.launcher.testing.captureFocused
import com.luncher.launcher.testing.captureScreenFocused
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tile banner picker, in US English, with sample pictures and banners drawn as gradients.
 * Reference images: src/test/screenshots/settings/ of this module; see docs/TESTING.md.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-$TV_1080P")
class TileBannerScreenshotTest {

    private val application = RuntimeEnvironment.getApplication() as SettingsTestApplication

    /**
     * Four pictures: a camera's 4:3 photo, cut at its top and bottom in a tile, a panorama cut at
     * its sides, a picture of a tile's shape, and a name too long for its row.
     */
    private val photo = picture("garden.jpg", added = 4, width = 4032, height = 3024, bytes = 3_200_000)
    private val panorama = picture("panorama.jpg", added = 3, width = 6000, height = 1500, bytes = 2_100_000)
    private val tileShaped = picture("poster.png", added = 2, bytes = 900_000)
    private val library = arrayOf(photo, panorama, tileShaped, picture("a picture with a name far too long for its row.jpg", added = 1, bytes = 1_100_000))

    private val youtube = app("youtube")

    private lateinit var graph: TestPicturesGraph

    // A remote's keys: the focus shows only out of touch mode, where Robolectric starts.
    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** The picker for YouTube's tile, with [pictures], its tile showing [image] as stored, every image drawn. */
    private fun picker(vararg pictures: Picture, image: TileImage? = null, appearance: Appearance = Appearance()): TileBannerActivity {
        graph = object : TestPicturesGraph(FakePictures(*pictures)) {
            override val installedApps = FakeInstalledApps(youtube, app("music"))
            override val appCustomizations = FakeAppCustomizations(mapOf(youtube.launchable to AppCustomization(image = image)))
        }
        application.graph = graph
        graph.appearanceSettings.appearance = appearance
        val intent = TileBannerActivity.intent(application, youtube.launchable)
        val activity = Robolectric.buildActivity(TileBannerActivity::class.java, intent).setup().get()
        idle()
        graph.jobs.runAll()
        idle()
        return activity
    }

    private fun TileBannerActivity.row(name: String): View {
        val list = findViewById<ViewGroup>(R.id.settings_pictures_list)
        return (0 until list.childCount).map(list::getChildAt).single { it.findViewById<TextView>(R.id.settings_pictures_name)?.text == name }
    }

    // The tile's stored picture chosen and focused, filling the tile, its top showing.
    @Test
    fun tileBannerPicker() {
        picker(*library, image = TileImage(photo.id, Framing(Fit.FILL, PicturePosition.TOP)))
            .window.decorView.captureFocused("src/test/screenshots/settings/settings_tile_banner.png")
    }

    // No picture on the tile: Original banner chosen, the options dimmed.
    @Test
    fun tileBannerPickerOwnBanner() {
        picker(*library).window.decorView.captureFocused("src/test/screenshots/settings/settings_tile_banner_original.png")
    }

    // No picture yet: how to add one.
    @Test
    fun tileBannerPickerEmpty() {
        picker().window.decorView.captureFocused("src/test/screenshots/settings/settings_tile_banner_empty.png")
    }

    // The panorama stretched to the tile's shape, its position dimmed; pill-shaped tiles.
    @Test
    fun tileBannerPickerStretched() {
        picker(*library, image = TileImage(panorama.id, Framing(Fit.STRETCH)), appearance = Appearance(tileStyle = TileStyle(corners = Corners.Pill)))
            .window.decorView.captureFocused("src/test/screenshots/settings/settings_tile_banner_stretched.png")
    }

    // A picture of the tile's shape: neither option moves it, both dimmed; square tiles.
    @Test
    fun tileBannerPickerTileShaped() {
        picker(*library, image = TileImage(tileShaped.id), appearance = Appearance(tileStyle = TileStyle(corners = Corners.Radius(0))))
            .window.decorView.captureFocused("src/test/screenshots/settings/settings_tile_banner_tile_shaped.png")
    }

    // OK on Position, at the row's end: its values over the pictures, the one it has level with the field.
    @Test
    fun tileBannerOptionList() {
        val activity = picker(*library, image = TileImage(panorama.id, Framing(Fit.FILL, PicturePosition.LEFT)))
        val position = (activity.findViewById<ViewGroup>(R.id.settings_picture_editor_options).getChildAt(0) as ViewGroup).getChildAt(1)
        position.requestFocus()
        position.performClick()
        idle()
        captureScreenFocused("src/test/screenshots/settings/settings_tile_banner_option_list.png")
    }

    // Back with another picture chosen: the question, Cancel focused.
    @Test
    fun tileBannerDiscard() {
        val activity = picker(*library)
        activity.row("panorama.jpg").performClick()
        graph.jobs.runAll()
        idle()
        @Suppress("DEPRECATION")
        activity.onBackPressed()
        idle()
        captureScreenFocused("src/test/screenshots/settings/settings_tile_banner_discard.png")
    }

    // Mirrored, but not the preview's content nor the pictures.
    @Test
    @Config(qualifiers = "ar-$TV_1080P")
    fun tileBannerPickerArabic() {
        picker(*library, image = TileImage(panorama.id, Framing(Fit.FILL, PicturePosition.LEFT)))
            .window.decorView.captureFocused("src/test/screenshots/settings/settings_tile_banner_arabic.png")
    }

    @Test
    @Config(qualifiers = "el-$TV_1080P")
    fun tileBannerPickerGreek() {
        picker(*library, image = TileImage(photo.id))
            .window.decorView.captureFocused("src/test/screenshots/settings/settings_tile_banner_greek.png")
    }
}
