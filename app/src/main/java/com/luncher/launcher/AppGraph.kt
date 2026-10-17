package com.luncher.launcher

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.luncher.domain.appearance.AppearanceSettings
import com.luncher.domain.apps.AppArrangements
import com.luncher.domain.apps.AppImages
import com.luncher.domain.apps.InstalledApps
import com.luncher.domain.clock.Clock
import com.luncher.domain.jobs.Jobs
import com.luncher.domain.pictures.FolderPictures
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.pictures.PictureImport
import com.luncher.domain.pictures.Pictures
import com.luncher.domain.wallpaper.Wallpaper
import com.luncher.launcher.appearance.PreferencesAppearanceSettings
import com.luncher.launcher.apps.BannerImages
import com.luncher.launcher.apps.CachedBannerImages
import com.luncher.launcher.apps.PackageManagerInstalledApps
import com.luncher.launcher.apps.PreferencesAppArrangements
import com.luncher.launcher.clock.AndroidClock
import com.luncher.launcher.home.HomeGraph
import com.luncher.launcher.jobs.BackgroundThreadJobs
import com.luncher.launcher.pictures.CachedPictureImages
import com.luncher.launcher.pictures.FilePictureImages
import com.luncher.launcher.pictures.FileStoredWallpaper
import com.luncher.launcher.pictures.FilesPictures
import com.luncher.launcher.pictures.MediaFolderPictures
import com.luncher.launcher.settings.SettingsGraph

/**
 * Composition root: the only place that creates adapters and decides which implementation
 * backs each domain port. It gives every feature the ports that feature declares it needs.
 * Everything is created on first use, so startup only pays for what the first screen needs. See
 * docs/ARCHITECTURE.md.
 *
 * Open so instrumented tests can replace single ports with fakes (`object : AppGraph(app) {
 * override … }`) and install the result as [LuncherApplication.graph]; see docs/TESTING.md.
 */
open class AppGraph(context: Context) : HomeGraph, SettingsGraph {

    protected val appContext: Context = context.applicationContext

    override val installedApps: InstalledApps by lazy { PackageManagerInstalledApps(appContext) }

    override val appArrangements: AppArrangements by lazy { PreferencesAppArrangements(appContext) }

    override val appImages: AppImages<Bitmap> by lazy { CachedBannerImages(appContext, BannerImages(appContext), jobs) }

    /** The background thread, for the adapters' and screens' jobs; open so a test can run them when it says. */
    override val jobs: Jobs by lazy { BackgroundThreadJobs() }

    override val clock: Clock by lazy { AndroidClock(appContext) }

    override val appearanceSettings: AppearanceSettings by lazy { PreferencesAppearanceSettings(appContext) }

    /** The picture library: both ports on the same files and index. */
    private val library by lazy { FilesPictures(appContext, jobs) }

    override val pictures: Pictures by lazy { library }

    override val pictureImport: PictureImport<Uri> by lazy { library }

    override val folderPictures: FolderPictures<Uri, Bitmap> by lazy { MediaFolderPictures(appContext, jobs) }

    override val pictureImages: PictureImages<Bitmap> by lazy { CachedPictureImages(appContext, FilePictureImages(appContext)) }

    /**
     * The wallpaper, kept in its own file at the screen's size and drawn by the uncached drawing:
     * the image cache keeps none of it.
     */
    override val wallpaper: Wallpaper<Bitmap> by lazy {
        Wallpaper(FileStoredWallpaper(appContext), FilePictureImages(appContext), appearanceSettings, jobs) { Handler(Looper.getMainLooper()).post(it) }
    }
}
