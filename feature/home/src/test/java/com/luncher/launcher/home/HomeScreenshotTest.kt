package com.luncher.launcher.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.HorizontalAlignment
import com.luncher.domain.appearance.Side
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.TileStyle
import com.luncher.domain.appearance.TopBarAppearance
import com.luncher.domain.appearance.VerticalAlignment
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.clock.FakeClock
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.wallpaper.FakeStoredWallpaper
import com.luncher.domain.wallpaper.WallpaperDrawing
import com.luncher.launcher.pictures.FilePictureImages
import com.luncher.launcher.ui.WorkingDialog
import com.luncher.launcher.testing.TV_1080P
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Reference images live in src/test/screenshots/home/ of this module. `recordRoborazziDebug` writes
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

    /**
     * The home screen, its wallpaper drawn and shown unless [drawn] is false: then, its window
     * saying it's being drawn, [kept] being what the last start kept.
     */
    private fun start(
        hidden: List<String> = emptyList(),
        appearance: Appearance = Appearance(),
        drawn: Boolean = true,
        kept: Pair<WallpaperDrawing, Bitmap>? = null,
    ): HomeActivity {
        val application = RuntimeEnvironment.getApplication() as HomeTestApplication
        val graph = object : TestHomeGraph(application) {
            // Seven apps shown: a full row, and a second one that starts at the left.
            override val installedApps = FakeInstalledApps(
                (listOf("games", "movies", "music", "news", "photos", "radio", "an app name too long for its tile") + hidden)
                    .map(::app),
            )
            override val appArrangements = FakeAppArrangements(AppArrangement(order = null, hidden = hidden.map { app(it).launchable }))
            override val clock = FakeClock()
            override val appearanceSettings = FakeAppearanceSettings(appearance)
            // The real drawing, of a picture stored as the library stores it (landscape()).
            override val pictureImages = FilePictureImages(application)
            override val storedWallpaper = FakeStoredWallpaper(kept)
        }
        application.graph = graph
        val activity = Robolectric.buildActivity(HomeActivity::class.java).setup().get()
        if (!drawn) return activity
        graph.jobs.runAll()                          // the wallpaper, drawn in the background
        // and shown, the window that said so closed
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WorkingDialog.SHORTEST))
        return activity
    }

    /**
     * A picture in the library's folder, as one added there: a made-up landscape, a sky and two
     * ranges of hills, its sharp edges showing the blur.
     */
    private fun landscape(): PictureId {
        val id = PictureId("landscape")
        val picture = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(picture)
        canvas.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, 1200f, Color.rgb(40, 50, 110), Color.rgb(220, 110, 120), Shader.TileMode.CLAMP) })
        canvas.drawCircle(1150f, 640f, 110f, Paint().apply { color = Color.rgb(250, 200, 120) })
        for ((top, shade) in listOf(560f to Color.rgb(70, 50, 100), 760f to Color.rgb(40, 30, 60))) {
            val hills = Path().apply {
                moveTo(0f, top + 120)
                for (x in 0..1600 step 200) lineTo(x.toFloat(), top + if (x / 200 % 2 == 0) 120f else 0f)
                lineTo(1600f, 1200f)
                lineTo(0f, 1200f)
                close()
            }
            canvas.drawPath(hills, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = shade })
        }
        val folder = File(RuntimeEnvironment.getApplication().filesDir, "pictures").apply { mkdirs() }
        File(folder, id.value).outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return id
    }

    // The wallpaper, a landscape photo filling the screen from its bottom, dimmed and lightly
    // blurred behind the tiles.
    @Test
    fun homeScreenWithAWallpaper() {
        val background = Background(landscape(), Fit.FILL, PicturePosition.BOTTOM, dim = 20, blur = Blur.LIGHT)

        start(appearance = Appearance(background = background)).window.decorView
            .captureRoboImage("src/test/screenshots/home/home_screen_wallpaper.png")
    }

    // The same, sharp and not dimmed, stretched: all of the 4:3 picture, wider.
    @Test
    fun homeScreenWithAStretchedWallpaper() {
        val background = Background(landscape(), Fit.STRETCH)

        start(appearance = Appearance(background = background)).window.decorView
            .captureRoboImage("src/test/screenshots/home/home_screen_wallpaper_stretched.png")
    }

    // The wallpaper drawn again for a TV that switched to another screen size: the plain
    // background, and a window over it saying so, which takes every key meanwhile.
    @Test
    @OptIn(ExperimentalRoborazziApi::class)   // captureScreenRoboImage, the one capture of a dialog over its screen
    fun homeScreenFittingTheWallpaper() {
        fittingTheWallpaper()
        captureScreenRoboImage("src/test/screenshots/home/home_screen_fitting_wallpaper.png")
    }

    // The same in Arabic: the window mirrors, the home screen doesn't.
    @Test
    @Config(qualifiers = "ar-rEG-$TV_1080P")
    @OptIn(ExperimentalRoborazziApi::class)
    fun homeScreenFittingTheWallpaperInArabic() {
        fittingTheWallpaper()
        captureScreenRoboImage("src/test/screenshots/home/home_screen_fitting_wallpaper_arabic.png")
    }

    private fun fittingTheWallpaper() {
        val background = Background(landscape(), dim = 20)
        val kept = WallpaperDrawing(background.picture!!, background.style, 1280, 720) to Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        start(appearance = Appearance(background = background), drawn = false, kept = kept)
        shadowOf(Looper.getMainLooper()).idle()
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

    // Every option away from its default but the time's and the date's, which would hide what the
    // others move: the rows centered both ways, the bottom row full, round corners; the clock on
    // the right, the time after the date.
    @Test
    fun homeScreenWithAnAppearance() {
        val appearance = Appearance(
            TileGeometry(verticalAlignment = VerticalAlignment.CENTER, horizontalAlignment = HorizontalAlignment.CENTER, fullBottomRow = true),
            TileStyle(Corners.Radius(12)),
            TopBarAppearance(clockSide = Side.RIGHT, timeSide = Side.RIGHT),
        )
        start(appearance = appearance).window.decorView.captureRoboImage("src/test/screenshots/home/home_screen_appearance.png")
    }

    // The date hidden: the time alone, the tiles where they are with both.
    @Test
    fun homeScreenWithoutTheDate() {
        start(appearance = Appearance(topBar = TopBarAppearance(showDate = false))).window.decorView
            .captureRoboImage("src/test/screenshots/home/home_screen_without_date.png")
    }

    // Arrange mode with the tiles at the bottom right: the shelf below them, all at the bottom.
    @Test
    fun arrangingAtTheBottomRight() {
        val activity = start(hidden = listOf("weather"), appearance = Appearance(TileGeometry(verticalAlignment = VerticalAlignment.BOTTOM, horizontalAlignment = HorizontalAlignment.RIGHT)))
        activity.arrange("Music")

        activity.window.decorView.captureRoboImage("src/test/screenshots/home/home_arranging_bottom_right.png")
    }

    // Arrange mode centered both ways: the shown tiles, the label and the shelf in the middle of the
    // space, the rows that aren't full and the label in the middle of the width.
    @Test
    fun arrangingCentered() {
        val activity = start(hidden = listOf("weather"), appearance = Appearance(TileGeometry(verticalAlignment = VerticalAlignment.CENTER, horizontalAlignment = HorizontalAlignment.CENTER)))
        activity.arrange("Music")

        activity.window.decorView.captureRoboImage("src/test/screenshots/home/home_arranging_centered.png")
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
