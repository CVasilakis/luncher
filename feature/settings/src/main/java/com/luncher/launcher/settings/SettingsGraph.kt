package com.luncher.launcher.settings

import android.app.Activity
import android.graphics.Bitmap
import com.luncher.domain.appearance.AppearanceSettings
import com.luncher.domain.apps.AppArrangements
import com.luncher.domain.apps.InstalledApps
import com.luncher.domain.jobs.Jobs
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.pictures.Pictures

/**
 * The ports the settings screens need. The app's composition root implements it with the
 * adapters, and tests with fakes; this module sees the ports only, never an adapter.
 */
interface SettingsGraph {
    val installedApps: InstalledApps
    val appArrangements: AppArrangements
    val appearanceSettings: AppearanceSettings

    /** The picture library, for the wallpaper's name in the appearance editor. */
    val pictures: Pictures

    /** The wallpaper in the appearance editor's preview, drawn at its size. */
    val pictureImages: PictureImages<Bitmap>

    /** The background thread, for the preview's wallpaper. */
    val jobs: Jobs

    /** Implemented by the Application, which holds the process's graph. */
    interface Owner {
        val settingsGraph: SettingsGraph
    }
}

/** The process's [SettingsGraph], for the settings screens: `val apps = graph.installedApps`. */
internal val Activity.graph: SettingsGraph
    get() = (application as SettingsGraph.Owner).settingsGraph
