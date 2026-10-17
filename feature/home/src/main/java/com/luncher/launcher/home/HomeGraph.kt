package com.luncher.launcher.home

import android.app.Activity
import android.graphics.Bitmap
import com.luncher.domain.appearance.AppearanceSettings
import com.luncher.domain.apps.AppArrangements
import com.luncher.domain.apps.AppImages
import com.luncher.domain.apps.InstalledApps
import com.luncher.domain.clock.Clock
import com.luncher.domain.wallpaper.Wallpaper

/**
 * The ports the home screen needs. The app's composition root implements it with the adapters,
 * and tests with fakes; this module sees the ports only, never an adapter.
 */
interface HomeGraph {
    val installedApps: InstalledApps
    val appArrangements: AppArrangements
    val appImages: AppImages<Bitmap>
    val clock: Clock
    val appearanceSettings: AppearanceSettings

    /** The wallpaper, kept drawn at the screen's size: one for the process. */
    val wallpaper: Wallpaper<Bitmap>

    /** Implemented by the Application, which holds the process's graph. */
    interface Owner {
        val homeGraph: HomeGraph
    }
}

/** The process's [HomeGraph], for the home screen: `val apps = graph.installedApps`. */
internal val Activity.graph: HomeGraph
    get() = (application as HomeGraph.Owner).homeGraph
