package com.luncher.launcher

import android.content.Context
import android.graphics.Bitmap
import com.luncher.domain.appearance.AppearanceSettings
import com.luncher.domain.apps.AppArrangements
import com.luncher.domain.apps.AppImages
import com.luncher.domain.apps.InstalledApps
import com.luncher.domain.clock.Clock
import com.luncher.domain.jobs.Jobs
import com.luncher.launcher.appearance.PreferencesAppearanceSettings
import com.luncher.launcher.apps.BannerImages
import com.luncher.launcher.apps.CachedBannerImages
import com.luncher.launcher.apps.PackageManagerInstalledApps
import com.luncher.launcher.apps.PreferencesAppArrangements
import com.luncher.launcher.clock.AndroidClock
import com.luncher.launcher.home.HomeGraph
import com.luncher.launcher.jobs.BackgroundThreadJobs
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

    /** The background thread, for the adapters' jobs; open so a test can run them when it says. */
    open val jobs: Jobs by lazy { BackgroundThreadJobs() }

    override val clock: Clock by lazy { AndroidClock(appContext) }

    override val appearanceSettings: AppearanceSettings by lazy { PreferencesAppearanceSettings(appContext) }
}
