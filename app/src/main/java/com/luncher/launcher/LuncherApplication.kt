package com.luncher.launcher

import android.app.Application
import com.luncher.launcher.home.HomeGraph
import com.luncher.launcher.settings.SettingsGraph

/** Holds the single [AppGraph] for the process, and gives each feature its part of it. */
class LuncherApplication : Application(), HomeGraph.Owner, SettingsGraph.Owner {

    private var current: AppGraph? = null

    /**
     * Created on first use. Instrumented tests set a graph with fakes before starting an activity,
     * and set a fresh `AppGraph(app)` afterwards; nothing else assigns it.
     */
    var graph: AppGraph
        get() = current ?: AppGraph(this).also { current = it }
        set(value) {
            current = value
        }

    override val homeGraph: HomeGraph get() = graph

    override val settingsGraph: SettingsGraph get() = graph
}
