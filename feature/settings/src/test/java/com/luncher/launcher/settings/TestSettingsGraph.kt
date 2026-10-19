package com.luncher.launcher.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.apps.AppArrangements
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.InstalledApps
import com.luncher.domain.appearance.SAMPLE_WALLPAPER
import com.luncher.domain.jobs.ManualJobs
import com.luncher.domain.pictures.FakeFolderPictures
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.FakePreparedPictures
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.pictures.ReceivedPictures
import com.luncher.domain.wallpaper.FakeStoredWallpaper
import com.luncher.domain.wallpaper.Wallpaper

/**
 * The ports a test gives the settings screens: fakes, which a test replaces with its own
 * (`object : TestSettingsGraph() { override … }`). [library] is both the library and what adds to
 * it, as in the app: by default it holds the sample wallpaper's picture.
 */
open class TestSettingsGraph(
    val library: FakePictures<Uri> = FakePictures(Picture(SAMPLE_WALLPAPER, "sunset.jpg", 1920, 1080, 1, 0)),
) : SettingsGraph {
    override val installedApps: InstalledApps = FakeInstalledApps()
    override val appArrangements: AppArrangements = FakeAppArrangements()
    override val appearanceSettings = FakeAppearanceSettings()
    override val pictures get() = library
    override val pictureImport get() = library

    /** Made on first use, on what the test's [pictureImport] is. */
    override val receivedPictures by lazy { ReceivedPictures(pictureImport) }
    override val folderPictures = FakeFolderPictures<Uri, Bitmap>(draw = { picture, width, height -> sample(picture.name, width, height) })
    override val pictureImages = FakePictureImages(::sampleWallpaper)
    override val thumbnails = FakePictureImages(::sampleWallpaper)
    override val preparedPictures = FakePreparedPictures()

    /** The preview's wallpaper is drawn when a test runs these, and a result is shown when it runs the main looper. */
    override val jobs = ManualJobs()

    /** What [wallpaper] keeps: nothing at first. */
    open val storedWallpaper = FakeStoredWallpaper<Bitmap>()

    // Made on first use, from what a test's subclass gives.
    override val wallpaper by lazy { Wallpaper(storedWallpaper, pictureImages, appearanceSettings, jobs) { Handler(Looper.getMainLooper()).post(it) } }
}

/**
 * A sample wallpaper: a gradient whose colors and direction tell the style apart, darker with its
 * dim, with fewer bands with its blur, so that every value of the background's options draws
 * differently.
 */
fun sampleWallpaper(@Suppress("UNUSED_PARAMETER") picture: com.luncher.domain.pictures.PictureId, width: Int, height: Int, style: PictureStyle): Bitmap {
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
class SettingsTestApplication : Application(), SettingsGraph.Owner {

    /** Set by each test before it starts a settings screen; the panel alone needs no port. */
    var graph: SettingsGraph = TestSettingsGraph()

    override val settingsGraph: SettingsGraph get() = graph
}
