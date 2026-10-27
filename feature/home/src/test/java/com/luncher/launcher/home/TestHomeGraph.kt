package com.luncher.launcher.home

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.AppearanceSettings
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.appearance.HorizontalAlignment
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.VerticalAlignment
import com.luncher.domain.apps.AppArrangements
import com.luncher.domain.apps.AppCustomizations
import com.luncher.domain.apps.AppImages
import com.luncher.domain.apps.AppInfoScreen
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeAppCustomizations
import com.luncher.domain.apps.FakeAppInfoScreen
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.InstalledApps
import com.luncher.domain.apps.PreparedTileImages
import com.luncher.domain.apps.PreparedTiles
import com.luncher.domain.clock.Clock
import com.luncher.domain.clock.FakeClock
import com.luncher.domain.jobs.ManualJobs
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.FakePreparedPictures
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.pictures.PreparedPictures
import com.luncher.domain.wallpaper.DrawnWallpaper
import com.luncher.domain.wallpaper.FakeStoredWallpaper
import com.luncher.domain.wallpaper.Wallpaper
import com.luncher.launcher.apps.BannerImages
import com.luncher.launcher.apps.FilePreparedTiles
import com.luncher.launcher.wallpaper.BlurredWallpaperFrost

/**
 * The default tile size, in rows from the top left with the short row last, so a tile's index
 * tells its row and column: what the tests of keys, focus and moving apps are written for. The
 * default look is the screenshots' and [HomeLayoutTest]'s.
 */
val topLeftTiles = TileGeometry(
    verticalAlignment = VerticalAlignment.TOP,
    horizontalAlignment = HorizontalAlignment.LEFT,
    fullBottomRow = false,
)

/**
 * The ports a test gives the home screen: fakes, which a test replaces with its own
 * (`object : TestHomeGraph(application) { override … }`), the tiles in [topLeftTiles], and the
 * real images, the pictures on tiles prepared as the app does, so the screenshots show the tiles
 * as the app draws them.
 */
open class TestHomeGraph(context: Context) : HomeGraph {
    override val installedApps: InstalledApps = FakeInstalledApps()
    override val appArrangements: AppArrangements = FakeAppArrangements()
    override val appCustomizations: AppCustomizations = FakeAppCustomizations()

    override val appImages: AppImages<Bitmap> by lazy { BannerImages(context) }

    /** What [preparedTileImages] prepares the tiles' pictures' copies with: every one prepared, unless a test says otherwise. */
    open val preparedPictures: PreparedPictures = FakePreparedPictures()

    /**
     * How a tile's picture is prepared and kept: from its working copy, in the test's own files, as
     * the app does; a test that shows one has its copy made (`WorkingCopyImages`, as
     * [preparedPictures]), then the jobs prepare its tile.
     */
    open val preparedTiles: PreparedTiles<Bitmap> by lazy { FilePreparedTiles(context) }

    // Made on first use, from what a test's subclass gives.
    override val preparedTileImages by lazy { PreparedTileImages(preparedPictures, preparedTiles, jobs) { Handler(Looper.getMainLooper()).post(it) } }
    override val appInfoScreen: AppInfoScreen = FakeAppInfoScreen()
    override val clock: Clock = FakeClock()
    override val appearanceSettings: AppearanceSettings = FakeAppearanceSettings(Appearance(topLeftTiles))

    /** How [wallpaper] draws a picture; the screenshots give the real drawing. */
    open val pictureImages: PictureImages<Bitmap> = FakePictureImages(::wallpaper)

    /** What [wallpaper] keeps: nothing at first. */
    open val storedWallpaper = FakeStoredWallpaper<Bitmap>()

    /** A wallpaper not drawn yet is drawn when a test runs these, and shown when it runs the main looper. */
    open val jobs = ManualJobs()

    // Made on first use, from what a test's subclass gives.
    override val wallpaper by lazy {
        Wallpaper(storedWallpaper, pictureImages, BlurredWallpaperFrost(), appearanceSettings, jobs) { Handler(Looper.getMainLooper()).post(it) }
    }
}

/** [image] as a kept wallpaper, with its frosted copy made as the app makes it. */
fun withFrost(image: Bitmap) = DrawnWallpaper(image, BlurredWallpaperFrost().frost(image))

/**
 * A sample wallpaper: a gradient whose colors and direction tell the style apart, darker with its
 * dim, with fewer bands with its blur, so that every value of the background's options draws
 * differently.
 */
fun wallpaper(@Suppress("UNUSED_PARAMETER") picture: PictureId, width: Int, height: Int, style: PictureStyle): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val fit = if (style.framing.fit == Fit.FILL) 0f else 0.25f
    val position = PicturePosition.entries.indexOf(style.framing.position) / 10f
    val end = if (style.blur.ordinal == 0) height.toFloat() else height.toFloat() * (1 + style.blur.ordinal)
    val paint = Paint().apply {
        shader = LinearGradient(width * (fit + position), 0f, width.toFloat(), end, Color.rgb(230, 120, 60), Color.rgb(40, 60, 160), Shader.TileMode.MIRROR)
    }
    val canvas = Canvas(bitmap)
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    canvas.drawColor(Color.argb(style.dim * 255 / 100, 0, 0, 0))
    return bitmap
}

/** The Application of this module's JVM tests (robolectric.properties): it holds the graph a test sets. */
class HomeTestApplication : Application(), HomeGraph.Owner {

    /** Set by each test before it starts the home screen. */
    lateinit var graph: HomeGraph

    override val homeGraph: HomeGraph get() = graph
}
