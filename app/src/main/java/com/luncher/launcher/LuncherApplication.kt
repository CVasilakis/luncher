package com.luncher.launcher

import android.app.Activity
import android.app.Application

/** Holds the single [AppGraph] for the process. */
class LuncherApplication : Application() {

    private var current: AppGraph? = null

    /**
     * Created on first use. Tests set a graph with fakes before starting an activity, and set a
     * fresh `AppGraph(app)` afterwards; nothing else assigns it.
     */
    var graph: AppGraph
        get() = current ?: AppGraph(this).also { current = it }
        set(value) {
            current = value
        }
}

/** The process's [AppGraph], for activities: `val apps = graph.installedApps`. */
val Activity.graph: AppGraph
    get() = (application as LuncherApplication).graph
