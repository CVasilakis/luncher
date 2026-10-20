package com.luncher.launcher.testing

import android.app.Activity
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

/**
 * Waits until no [activity] is left but destroyed ones, after the key that closed it, before a test
 * looks with Espresso at the screen it returns to. Until it's destroyed, about half a second after
 * the screen under it resumed, its window is still among the app's, on top; a look that begins
 * meanwhile can pick that window, and Espresso then waits for it to get the focus, which it never
 * does, and fails after 10 s (RootViewWithoutFocusException), although the screen under it has the
 * focus by then. Read on the main thread, so once it returns, the window is gone too. Fails after
 * [timeoutMs], saying the stage it was left in.
 */
fun waitUntilDestroyed(activity: Class<out Activity>, timeoutMs: Long = DESTROYED_TIMEOUT_MS) {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (true) {
        val stage = liveStageOf(activity) ?: return
        if (SystemClock.uptimeMillis() > deadline) {
            throw AssertionError("${activity.simpleName} wasn't destroyed in ${timeoutMs / 1000} s: it's $stage")
        }
        SystemClock.sleep(DESTROYED_POLL_MS)
    }
}

/** The stage of an [activity] not destroyed yet, or null if there's none. */
private fun liveStageOf(activity: Class<out Activity>): Stage? {
    var found: Stage? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        found = Stage.values().filter { it != Stage.DESTROYED }.firstOrNull { stage ->
            monitor.getActivitiesInStage(stage).any { activity.isInstance(it) }
        }
    }
    return found
}

private const val DESTROYED_TIMEOUT_MS = 10_000L
private const val DESTROYED_POLL_MS = 50L
