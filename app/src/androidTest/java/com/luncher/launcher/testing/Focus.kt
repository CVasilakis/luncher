package com.luncher.launcher.testing

import android.app.Activity
import android.content.ComponentName
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry

/**
 * What has the input focus, from `dumpsys window`: the focused activity (mFocusedApp, e.g.
 * com.luncher.launcher/.home.HomeActivity) and the focused window's title (mCurrentFocus, for an
 * activity's window e.g. com.luncher.launcher/com.luncher.launcher.home.HomeActivity), each null
 * when there's none. Keys go to that window only. Two are equal only if they're the very same
 * activity and window, not a new one with the same name.
 */
data class Focus(
    val activity: String?,
    val window: String?,
    /** The focused activity's record (ActivityRecord{<hash> …}), which `dumpsys activity activities` names too. */
    internal val record: String?,
    private val lines: String,
) {

    /** Whether an activity of [packageName] is the focused one, and one of its windows has the focus. */
    fun isIn(packageName: String): Boolean =
        activity?.substringBefore('/') == packageName && window?.startsWith("$packageName/") == true

    /** Whether [component] is the focused activity, and its own window has the focus. */
    fun isOf(component: ComponentName): Boolean =
        activity == component.flattenToShortString() && window == component.flattenToString()

    override fun toString() = "the focused activity is ${activity ?: "none"}, the focused window ${window ?: "none"}"
}

fun focus(): Focus = focusIn(shell("dumpsys window"))

/** What has the input focus, from the output of `dumpsys window`. */
internal fun focusIn(windows: String): Focus {
    val focus = windows.lines()
    // mFocusedApp names an ActivityRecord{<hash> u0 <package>/<class> t<task>}, alone or inside a
    // token (before API 29); mCurrentFocus=Window{<hash> u0 <title>}, or mCurrentFocus=null. The
    // last ones: after an ANR, the dump starts with the state at that time (WINDOW MANAGER LAST
    // ANR), with these lines too, and on a starved Google TV emulator they then stayed in the dump.
    val app = focus.lastOrNull { "mFocusedApp=" in it }?.trim()
    val window = focus.lastOrNull { "mCurrentFocus=" in it }?.trim()
    val record = app?.let { FOCUSED_ACTIVITY.find(it) }
    return Focus(
        activity = record?.groupValues?.get(2),
        window = window?.let { FOCUSED_WINDOW.find(it)?.groupValues?.get(1) },
        record = record?.groupValues?.get(1),
        lines = "$app $window",
    )
}

/**
 * Waits until [activity], of the app under test, has the input focus, before a test sends it
 * keys. Seeing its window (UI Automator's `Until.hasObject`) isn't enough: a window shows before it
 * gets the focus, and a key sent meanwhile goes to the window that has it, or waits for one. With
 * none focused at all, a key the app under test injects waits up to 60 s, then is dropped without
 * an error. Nor is the focus alone: a window can get it while it's still being added, before its
 * first layout. So first see the screen, then wait for this. Fails after [timeoutMs], saying what
 * has the focus.
 */
fun waitForFocus(activity: Class<out Activity>, timeoutMs: Long = FOCUS_TIMEOUT_MS) {
    val component = ComponentName(InstrumentationRegistry.getInstrumentation().targetContext, activity)
    waitForFocus(activity.simpleName, timeoutMs) { it.isOf(component) }
}

/** Like the other `waitForFocus`, for any activity of [packageName] and one of its windows. */
fun waitForFocus(packageName: String, timeoutMs: Long = FOCUS_TIMEOUT_MS) {
    waitForFocus(packageName, timeoutMs) { it.isIn(packageName) }
}

private fun waitForFocus(name: String, timeoutMs: Long, has: (Focus) -> Boolean) {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (true) {
        val focus = focus()
        if (has(focus)) return
        if (SystemClock.uptimeMillis() > deadline) {
            throw AssertionError("$name didn't get the input focus in ${timeoutMs / 1000} s: $focus")
        }
        SystemClock.sleep(FOCUS_POLL_MS)
    }
}

private val FOCUSED_ACTIVITY = Regex("""ActivityRecord\{(\S+) \S+ ([^ }]+)""")
private val FOCUSED_WINDOW = Regex("""mCurrentFocus=Window\{\S+ \S+ (.*)\}$""")
private const val FOCUS_TIMEOUT_MS = 10_000L
private const val FOCUS_POLL_MS = 200L
