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
import com.luncher.launcher.testing.captureFocused
import com.luncher.launcher.testing.captureScreenFocused
import com.luncher.domain.appearance.AppName
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
import com.luncher.domain.apps.FakeAppInfoScreen
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
     * saying it's being drawn, [kept] being what the last start kept. [hasAppInfo]: whether the
     * device has the apps' page in its settings.
     */
    private fun start(
        hidden: List<String> = emptyList(),
        appearance: Appearance = Appearance(),
        drawn: Boolean = true,
        kept: Pair<WallpaperDrawing, Bitmap>? = null,
        hasAppInfo: Boolean = true,
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
            override val appInfoScreen = FakeAppInfoScreen(hasAppInfo)
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
            .captureFocused("src/test/screenshots/home/home_screen_wallpaper.png")
    }

    // The same, sharp and not dimmed, stretched: all of the 4:3 picture, wider.
    @Test
    fun homeScreenWithAStretchedWallpaper() {
        val background = Background(landscape(), Fit.STRETCH)

        start(appearance = Appearance(background = background)).window.decorView
            .captureFocused("src/test/screenshots/home/home_screen_wallpaper_stretched.png")
    }

    // The wallpaper drawn again for a TV that switched to another screen size: the plain
    // background, and a window over it saying so, which takes every key meanwhile.
    @Test
    fun homeScreenFittingTheWallpaper() {
        fittingTheWallpaper()
        captureScreenFocused("src/test/screenshots/home/home_screen_fitting_wallpaper.png")
    }

    // The same in Arabic: the window mirrors, the home screen doesn't.
    @Test
    @Config(qualifiers = "ar-rEG-$TV_1080P")
    fun homeScreenFittingTheWallpaperInArabic() {
        fittingTheWallpaper()
        captureScreenFocused("src/test/screenshots/home/home_screen_fitting_wallpaper_arabic.png")
    }

    private fun fittingTheWallpaper() {
        val background = Background(landscape(), dim = 20)
        val kept = WallpaperDrawing(background.picture!!, background.style, 1280, 720) to Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        start(appearance = Appearance(background = background), drawn = false, kept = kept)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun HomeActivity.tile(label: String): AppTileView {
        val tiles = findViewById<AppTilesView>(R.id.home_apps)
        return (0 until tiles.childCount).map { tiles.getChildAt(it) as AppTileView }.single { it.app.label == label }
    }

    /** Opens the menu of the tile of [label], as a long press of OK on it would, its zoom done. */
    private fun HomeActivity.openMenu(label: String) {
        tile(label).requestFocus()
        tile(label).performLongClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
    }

    /** Moves Music one place right, as Move in its menu and the D-pad would. */
    private fun HomeActivity.moveMusic() {
        tile("Music").startMoving()
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
    }

    @Test
    fun homeScreen() {
        start().window.decorView.captureFocused("src/test/screenshots/home/home_screen.png")
    }

    // A 4:3 screen, 720x540 dp: four tiles in a row, of about the size they have at 1080p.
    @Test
    @Config(qualifiers = "+w720dp")
    fun homeScreen4by3() {
        start().window.decorView.captureFocused("src/test/screenshots/home/home_screen_4by3.png")
    }

    // 1080p at 160 dpi, as some TV boxes are set, 1920x1080 dp: ten tiles in a row, of about the
    // size they have next to the clock at 1080p.
    @Test
    @Config(qualifiers = "+w1920dp-h1080dp-mdpi")
    fun homeScreen160dpi() {
        start().window.decorView.captureFocused("src/test/screenshots/home/home_screen_160dpi.png")
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
        start(appearance = appearance).window.decorView.captureFocused("src/test/screenshots/home/home_screen_appearance.png")
    }

    // The tvOS-like look: the focused tile lifted, without a frame, its shadow under it, and its
    // app's name below it, the rows further apart.
    @Test
    fun homeScreenLiftedWithTheAppName() {
        val appearance = Appearance(TileGeometry(appName = AppName.WHEN_FOCUSED), TileStyle(focusFrame = false))

        start(appearance = appearance).window.decorView.captureFocused("src/test/screenshots/home/home_screen_lifted_name.png")
    }

    // The same with the long name focused: cut to the tile's width.
    @Test
    fun homeScreenWithALongAppName() {
        val activity = start(appearance = Appearance(TileGeometry(appName = AppName.WHEN_FOCUSED)))
        val tiles = activity.findViewById<AppTilesView>(R.id.home_apps)
        tiles.getChildAt(tiles.childCount - 1).requestFocus()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))   // the zoom and the name's fade

        activity.window.decorView.captureFocused("src/test/screenshots/home/home_screen_long_name.png")
    }

    // Moving Music, lifted: the held tile's white frame tells it from one only focused.
    @Test
    fun movingLifted() {
        val activity = start(hidden = listOf("weather"), appearance = Appearance(TileGeometry(appName = AppName.WHEN_FOCUSED), TileStyle(focusFrame = false)))
        activity.moveMusic()

        activity.window.decorView.captureFocused("src/test/screenshots/home/home_moving_lifted.png")
    }

    // The date hidden: the time alone, the tiles where they are with both.
    @Test
    fun homeScreenWithoutTheDate() {
        start(appearance = Appearance(topBar = TopBarAppearance(showDate = false))).window.decorView
            .captureFocused("src/test/screenshots/home/home_screen_without_date.png")
    }

    @Test
    fun settingsEntryFocused() {
        val activity = start()
        activity.findViewById<View>(R.id.home_settings).requestFocus()

        activity.window.decorView.captureFocused("src/test/screenshots/home/home_settings_focused.png")
    }

    // Moving Music, an app hidden, which doesn't show meanwhile.
    @Test
    fun moving() {
        val activity = start(hidden = listOf("weather"))
        activity.moveMusic()

        activity.window.decorView.captureFocused("src/test/screenshots/home/home_moving.png")
    }

    // The menu of a tile in the second row: above it.
    @Test
    fun menuAbove() {
        start().openMenu("Radio")

        captureScreenFocused("src/test/screenshots/home/home_menu_above.png")
    }

    // The menu of a tile in the first row, the rows at the top, its name shown: below the name,
    // where there's no room above.
    @Test
    fun menuBelow() {
        start(appearance = Appearance(topLeftTiles.copy(appName = AppName.WHEN_FOCUSED))).openMenu("Music")

        captureScreenFocused("src/test/screenshots/home/home_menu_below.png")
    }

    // 720p at 320 dpi, 640x360 dp, the rows at the top: room for the menu of a tile in the second
    // row neither above nor below it, so beside it, on the side with more room.
    @Test
    @Config(qualifiers = "+w640dp-h360dp-xhdpi")
    fun menuBeside() {
        start(appearance = Appearance(topLeftTiles)).openMenu("Music")

        captureScreenFocused("src/test/screenshots/home/home_menu_beside.png")
    }

    // The same screen, the second row's middle tile: room for the whole menu nowhere, so it
    // scrolls above the tile, below the top bar, two rows showing, the second fading.
    @Test
    @Config(qualifiers = "+w640dp-h360dp-xhdpi")
    fun menuScrolling() {
        start(appearance = Appearance(topLeftTiles)).openMenu("News")

        captureScreenFocused("src/test/screenshots/home/home_menu_scrolling.png")
    }

    // On a device without the apps' page in its settings: App info dimmed.
    @Test
    fun menuWithAppInfoDimmed() {
        start(hasAppInfo = false).openMenu("Radio")

        captureScreenFocused("src/test/screenshots/home/home_menu_app_info_dimmed.png")
    }

    // The same in Arabic: the menu mirrors, the home screen doesn't.
    @Test
    @Config(qualifiers = "ar-rEG-$TV_1080P")
    fun menuInArabic() {
        start().openMenu("Radio")

        captureScreenFocused("src/test/screenshots/home/home_menu_arabic.png")
    }
}
