package com.luncher.launcher

import android.content.Context
import com.luncher.domain.apps.AppArrangements
import com.luncher.domain.apps.InstalledApps
import com.luncher.domain.clock.Clock
import com.luncher.launcher.apps.PackageManagerInstalledApps
import com.luncher.launcher.apps.PreferencesAppArrangements
import com.luncher.launcher.clock.AndroidClock
import com.luncher.launcher.home.BannerImages

/**
 * Composition root: the only place that creates adapters and decides which implementation
 * backs each domain port. Everything is created on first use, so startup only pays for what
 * the first screen needs. See docs/ARCHITECTURE.md.
 *
 * Open so tests can replace single ports with fakes (`object : AppGraph(app) { override … }`)
 * and install the result as [LuncherApplication.graph]; see docs/TESTING.md.
 */
open class AppGraph(context: Context) {

    protected val appContext: Context = context.applicationContext

    open val installedApps: InstalledApps by lazy { PackageManagerInstalledApps(appContext.packageManager) }

    open val appArrangements: AppArrangements by lazy {
        PreferencesAppArrangements(appContext.getSharedPreferences("arrangement", Context.MODE_PRIVATE))
    }

    val bannerImages: BannerImages by lazy { BannerImages(appContext) }

    open val clock: Clock by lazy { AndroidClock(appContext) }
}
