package com.luncher.launcher.testing

import android.graphics.Bitmap
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.test.espresso.Espresso
import androidx.test.espresso.FailureHandler
import androidx.test.espresso.base.DefaultFailureHandler
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.hamcrest.Matcher
import org.junit.runner.Description
import org.junit.runner.notification.RunListener

/**
 * Saves what the device showed at the moment a test failed, while the failing screen is still
 * open: once the test has failed, `ActivityScenario.use` closes its activity, and the home app
 * comes to the front, so what CI saves after the run shows only that. Each failure gets two files
 * in the test's additional output (the folder Espresso saves its own screenshots in, which AGP and
 * CI pull): `failure-<n>-<Class>.<test>.txt` and a screenshot `….png`. The text names the error
 * and holds:
 *
 * - the app's windows, each attached or not, visible or not, and with the window focus or not,
 *   the topmost last: a closing activity's window stays among them, on top, until it's destroyed,
 *   and Espresso can wait on it ([waitUntilDestroyed]);
 * - each of the app's activities in its lifecycle stage;
 * - what has the input focus ([focus]), then `dumpsys window windows` and
 *   `dumpsys activity activities`.
 *
 * Espresso's failures come here through [handler], installed for every look as the run starts;
 * [waitUntilMatches] uses it for its last look only. The helpers that wait without Espresso
 * ([waitForFocus], [waitUntilSeen], [waitUntil], [waitUntilDestroyed]) call [dump] before they
 * fail. A JUnit `RunListener` (the runner's `listener` argument, in `app/build.gradle.kts`), so
 * it's installed once for every test class, and knows each test's name for the files.
 */
class FailureCapture : RunListener() {

    override fun testRunStarted(description: Description) {
        Espresso.setFailureHandler(handler)
    }

    override fun testStarted(description: Description) {
        test = "${description.testClass?.simpleName ?: description.className}.${description.methodName}"
    }

    companion object {
        private const val TAG = "FailureCapture"

        @Volatile
        private var test = "no-test"
        private var count = 0

        /** Espresso's own handling (a screenshot and the view hierarchy, in the error), then [dump]. */
        val handler: FailureHandler = object : FailureHandler {
            private val default by lazy { DefaultFailureHandler(InstrumentationRegistry.getInstrumentation().targetContext) }

            override fun handle(error: Throwable, viewMatcher: Matcher<View>) {
                try {
                    default.handle(error, viewMatcher)   // throws the error, with what Espresso adds
                } finally {
                    dump(error)
                }
            }
        }

        /**
         * Saves the device's state for [error], as the class says. Never fails itself: a dump that
         * can't be made is logged, and the test fails with its own error.
         */
        fun dump(error: Throwable) {
            try {
                val name = "failure-${++count}-$test"
                val text = StringBuilder()
                text.append(test).append(" failed at uptime ").append(SystemClock.uptimeMillis()).append(" ms\n")
                text.append(error.toString().lineSequence().take(3).joinToString("\n")).append("\n\n")
                text.append(onMain(::appState))
                text.append("\n===== focus\n").append(focus()).append('\n')
                for (command in listOf("dumpsys window windows", "dumpsys activity activities")) {
                    text.append("\n===== ").append(command).append('\n').append(shell(command))
                }
                val storage = PlatformTestStorageRegistry.getInstance()
                storage.openOutputFile("$name.txt").use { it.write(text.toString().toByteArray()) }
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { screen ->
                    storage.openOutputFile("$name.png").use { screen.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                Log.w(TAG, "$test failed: saved the device's state as $name.txt and .png")
            } catch (e: Exception) {
                Log.w(TAG, "$test failed: couldn't save the device's state", e)
            }
        }

        /** The app's windows and activities, read on the main thread, where they change. */
        private fun appState(): String {
            val text = StringBuilder("===== the app's windows, the topmost last\n")
            try {
                // Espresso finds its windows the same way (RootsOracle).
                val global = Class.forName("android.view.WindowManagerGlobal")
                val instance = global.getMethod("getInstance").invoke(null)
                val views = global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<*>
                for (view in views.filterIsInstance<View>()) {
                    text.append(view).append(": attached=").append(view.isAttachedToWindow)
                        .append(", visibility=").append(visibility(view.visibility))
                        .append(", window focus=").append(view.hasWindowFocus()).append('\n')
                }
            } catch (e: ReflectiveOperationException) {
                text.append("(unreadable: ").append(e).append(")\n")
            }
            text.append("\n===== the app's activities\n")
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            for (stage in Stage.values()) {
                for (activity in monitor.getActivitiesInStage(stage)) {
                    text.append(stage).append(": ").append(activity)
                        .append(if (activity.isFinishing) ", finishing" else "")
                        .append(", window focus=").append(activity.hasWindowFocus()).append('\n')
                }
            }
            return text.toString()
        }

        private fun visibility(value: Int) = when (value) {
            View.VISIBLE -> "VISIBLE"
            View.INVISIBLE -> "INVISIBLE"
            else -> "GONE"
        }

        private fun <T> onMain(read: () -> T): T {
            if (Looper.myLooper() == Looper.getMainLooper()) return read()
            var result: T? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync { result = read() }
            @Suppress("UNCHECKED_CAST")
            return result as T
        }
    }
}
