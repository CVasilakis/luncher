package com.luncher.launcher

import android.content.Context
import android.graphics.Bitmap
import com.luncher.domain.apps.AppArrangements
import com.luncher.domain.apps.AppImages
import com.luncher.domain.apps.InstalledApps
import com.luncher.domain.clock.Clock
import com.luncher.launcher.apps.BannerImages
import com.luncher.launcher.apps.PackageManagerInstalledApps
import com.luncher.launcher.apps.PreferencesAppArrangements
import com.luncher.launcher.clock.AndroidClock
import com.luncher.launcher.home.HomeGraph
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

    override val installedApps: InstalledApps by lazy { PackageManagerInstalledApps(appContext.packageManager) }

    override val appArrangements: AppArrangements by lazy { PreferencesAppArrangements(appContext) }

    override val appImages: AppImages<Bitmap> by lazy { BannerImages(appContext) }

    override val clock: Clock by lazy { AndroidClock(appContext) }
}
