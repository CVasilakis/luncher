package com.luncher.launcher.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Whether one of Luncher's own screens is in front: one has started and not stopped. Made for the
 * share window, which Android opens with a task switch that plays no animation of the window's own
 * while Luncher is in front. Registered once, by the application; unregistered, nothing is in
 * front. Main thread only.
 */
object Foreground {

    private var started = 0

    /** Whether a screen of Luncher's is in front now. */
    val inFront: Boolean get() = started > 0

    /** Starts counting [application]'s screens. */
    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started++
            }

            override fun onActivityStopped(activity: Activity) {
                started--
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
