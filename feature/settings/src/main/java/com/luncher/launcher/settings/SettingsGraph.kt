package com.luncher.launcher.settings

import android.app.Activity
import com.luncher.domain.apps.AppArrangements
import com.luncher.domain.apps.InstalledApps

/**
 * The ports the settings screens need. The app's composition root implements it with the
 * adapters, and tests with fakes; this module sees the ports only, never an adapter.
 */
interface SettingsGraph {
    val installedApps: InstalledApps
    val appArrangements: AppArrangements

    /** Implemented by the Application, which holds the process's graph. */
    interface Owner {
        val settingsGraph: SettingsGraph
    }
}

/** The process's [SettingsGraph], for the settings screens: `val apps = graph.installedApps`. */
internal val Activity.graph: SettingsGraph
    get() = (application as SettingsGraph.Owner).settingsGraph
