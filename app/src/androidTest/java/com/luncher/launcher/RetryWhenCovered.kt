package com.luncher.launcher

import android.app.Activity
import android.content.ComponentName
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * Runs a test once more when it failed while the stock launcher had come over its screen, which no
 * wait before the test can foresee: the stock launcher can bring its home task to the front at any
 * time, over whatever a test has started. On Google TV (API 33), Play Store updates Google Play
 * services about 20 s after a boot; the launcher dies with it, restarts, and sends itself a HOME
 * intent, about 25 s after the boot's wait for the home screen returned. On a starved Google TV
 * (API 30-33), the launcher opened its profile chooser seconds after its home screen looked
 * settled ([waitForHomeScreen] says why no look can tell). A covered test fails within its own
 * time limits, with an error that says nothing about the cover: Espresso gives up after 33-38 s
 * without a resumed activity (NoActivityResumedException), [waitForFocus] and UI Automator's waits
 * after 10 s.
 *
 * The cover is identified while the test runs, not when it has failed: by then the test has closed
 * its activity (ActivityScenario's `use`), and the home app is in front after any test. When an
 * activity of the app is paused or stopped without finishing, a look waits until the focus has left
 * it. It's a cover only when the focus went to a screen of the app HOME resolves to (on API 22, the
 * "choose home app" dialog of package android), that app isn't Luncher (`HomeKeyTest` makes it the
 * home app), and the activity was still paused or stopped, not finishing, before and after the
 * focus was read: under the stock launcher. If the test then fails, it logs that (logcat, tag
 * [TAG]), finishes the app's activities as the runner does between tests, waits for the settled
 * home screen, and runs the test again, `@Before` and `@After` included. Once: if the retry fails
 * too, its failure comes with a message saying that the first attempt was covered, and the first
 * failure attached. Any other failure is rethrown unchanged, at once: a retry would hide a real
 * failure, or one whose screen something else covered (one of the app's own screens, the device's
 * settings).
 *
 * As a `@Rule` it wraps `@Before` and `@After`; give any other rule that sets up or tears down
 * state an order that keeps it inside this one. The retry runs on the same instance of the test
 * class, so what a test changes is created anew in `@Before` or in the test, not in a field's
 * initializer.
 */
class RetryWhenCovered : TestRule {

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val first = attempt(base) ?: return
            val cover = first.cover ?: throw first.failure
            Log.w(
                TAG,
                "${description.displayName} failed while the stock launcher covered its screen ($cover). " +
                    "Running it once more, from the settled home screen. The failure: ${first.failure}",
            )
            val second = try {
                finishActivities()
                waitForHomeScreen()
                attempt(base)
            } catch (e: AssumptionViolatedException) {
                throw e
            } catch (e: Throwable) {
                Attempt(e, null)
            }
            if (second == null) {
                Log.i(TAG, "${description.displayName} passed on the retry")
                return
            }
            val again = second.cover?.let { "; the retry was covered too ($it)" } ?: ""
            throw AssertionError(
                "Failed twice. The first attempt failed while the stock launcher covered its screen ($cover), " +
                    "so it ran once more$again. The retry's failure: ${second.failure}",
                second.failure,
            ).apply { addSuppressed(first.failure) }
        }
    }

    /** A failed run of a test, and the cover seen while it ran (null: none). */
    private class Attempt(val failure: Throwable, val cover: String?)

    /** Runs the test once: null if it passed. An assumption that fails is rethrown: a skip, not a failure. */
    private fun attempt(base: Statement): Attempt? {
        val watch = CoverWatch()
        try {
            base.evaluate()
        } catch (e: AssumptionViolatedException) {
            watch.stop()
            throw e
        } catch (e: Throwable) {
            return Attempt(e, watch.awaitCover())
        }
        watch.stop()
        return null
    }

    /** Finishes the app's activities left from the failed attempt, as the runner does after every test. */
    private fun finishActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            Stage.values().filter { it != Stage.DESTROYED }
                .flatMap { monitor.getActivitiesInStage(it) }
                .forEach { if (!it.isFinishing) it.finish() }
        }
    }

    /**
     * Watches one attempt for the stock launcher covering an activity of the app. Activities are
     * reported on the main thread; the looks run on threads of their own, so neither the app nor
     * the test waits for them.
     */
    private class CoverWatch {
        private val target = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        private val looks = Executors.newCachedThreadPool { Thread(it, TAG).apply { isDaemon = true } }

        /** The app's activities paused or stopped, not finishing: under some other screen. */
        private val behind: MutableSet<Activity> = Collections.synchronizedSet(mutableSetOf())

        /** The activities a look is watching, one look each. */
        private val watched: MutableSet<Activity> = Collections.synchronizedSet(mutableSetOf())

        @Volatile
        private var cover: String? = null

        /** Set once the attempt has failed: each look ends after one more look at the focus. */
        @Volatile
        private var ending = false

        // The monitor holds its callbacks weakly: this field keeps it registered.
        private val callback = ActivityLifecycleCallback { activity, stage ->
            if ((stage == Stage.PAUSED || stage == Stage.STOPPED) && !activity.isFinishing) {
                behind += activity
                if (watched.add(activity)) {
                    val since = SystemClock.uptimeMillis()
                    try {
                        looks.execute { look(activity, stage, since) }
                    } catch (e: RejectedExecutionException) {
                        // The attempt has ended.
                    }
                }
            } else {
                behind -= activity
            }
        }

        init {
            ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback)
        }

        /**
         * Watches [activity], paused or stopped at [since], until the focus has left it, then
         * decides whether the stock launcher took it. A screen of the app itself over it, e.g. the
         * settings panel over the home screen, isn't a cover; nor is a screen of any other app.
         * No time limit: it ends when the activity comes back or is closed, or the attempt ends.
         * On a starved emulator the stock launcher's screen took the focus up to 10.6 s after the
         * test's activity was paused.
         */
        private fun look(activity: Activity, stage: Stage, since: Long) {
            val component = ComponentName(activity, activity.javaClass).flattenToShortString()
            try {
                while (cover == null && activity in behind) {
                    val last = ending
                    val focus = focus()
                    val focused = focus.activity
                    val window = focus.window
                    if (focused != null && focused != component && window != null) {
                        // Some other screen has the focus. Ours, or another app's:
                        if (focused.startsWith("$target/") || window.startsWith("$target/")) return
                        val home = resolvedHome()
                        if (home != null && home != target && focus.isIn(home) && activity in behind) {
                            val after = SystemClock.uptimeMillis() - since
                            cover = "$focus, over $component, seen $after ms after it was ${stage.name.lowercase()}"
                        }
                        return
                    }
                    if (last) return
                    Thread.sleep(LOOK_POLL_MS)
                }
            } catch (e: InterruptedException) {
                // The attempt has passed.
            } finally {
                watched -= activity
            }
        }

        /**
         * Ends the watch after a test that failed: lets each look under way take one more look at
         * the focus (a covered test fails seconds after the cover came), and returns the cover seen.
         */
        fun awaitCover(): String? {
            ending = true
            looks.shutdown()
            looks.awaitTermination(LAST_LOOK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback)
            behind.clear()
            return cover
        }

        /** Ends the watch, after a test that passed or was skipped: the looks under way no longer matter. */
        fun stop() {
            looks.shutdownNow()
            ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback)
            behind.clear()
        }
    }

    companion object {
        const val TAG = "RetryWhenCovered"

        private const val LOOK_POLL_MS = 200L

        /**
         * How long a failed attempt waits for the looks under way to end: one more `dumpsys window`
         * and resolve each, which took about 1 s on a starved emulator.
         */
        private const val LAST_LOOK_TIMEOUT_MS = 10_000L
    }
}
