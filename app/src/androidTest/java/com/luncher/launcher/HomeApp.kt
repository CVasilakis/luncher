package com.luncher.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Package of the activity Android starts for Home: "android" when it would ask which one, null
 * when nothing handles Home.
 *
 * From API 24 on it asks the shell: from API 30, package visibility hides other home apps from
 * Luncher's own PackageManager, which would then find Luncher alone. Before API 24 there's no
 * `cmd`, but no package visibility either. Runs the command through UiAutomation, not UI
 * Automator, which needs API 23.
 */
fun resolvedHome(): String? {
    if (Build.VERSION.SDK_INT < 24) {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return InstrumentationRegistry.getInstrumentation().targetContext.packageManager
            .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }
    return resolvedHomeActivity()?.substringBefore('/')
}

/**
 * Waits until the device shows its home screen, settled. Every instrumented test that opens a
 * screen starts with it, so none depends on how an earlier test or the boot left the device: an
 * activity a test starts while the home app is still starting can end up behind that app's
 * windows, never getting the focus. The home app starts whenever a test closes its activities, and
 * starts cold after `HomeKeyTest` re-enables it. E.g. on API 36, the stock launcher starting cold
 * opens `.dialog.ShowDialogsActivity` over itself seconds later (a "Buy and rent movies on your
 * TV" promotion, which stays until dismissed), and a settings panel opened meanwhile was paused
 * before it was ever shown; on Google TV, the stock launcher coming back opens its profile chooser
 * over itself, and an in-app test's home screen started meanwhile never came up. Right after a
 * boot, sys.boot_completed comes before the home app is in front, and before any window has the
 * focus on a busy host (on CI's API 34, no test's window ever got it).
 *
 * Settled, as android-tv-wsl-dev-tools' `start-emulator.sh --wait-for-home` has it: the home app
 * is known, an activity of it is the focused one, and one of its windows has the focus (keys go
 * only there). From API 24 on, the home app is the one HOME resolves to, once that's no longer
 * Settings' FallbackHome (which holds the screen until the user is unlocked after a boot). API 22
 * and 23 have neither `cmd` to ask nor FallbackHome: there it's the focused activity, if a HOME
 * intent started its task. That's also how API 22's "choose home app" dialog counts, which is in
 * front after a test there. Also, stricter than there, the very same activity and window for
 * [HOME_STABLE_MS] in a row: a screen the home app opens over itself, like that promotion or the
 * profile chooser, is the home screen once it has come up, and whatever a test starts goes above
 * it. Fails after [timeoutMs], saying what was in front.
 *
 * The one key it presses is Back on [FIRST_BOOT_SCREENS], screens a first boot opens in front of
 * the home app, which stay until Back, and only while one of them has the focus. Nothing else
 * gets a key, on the stock launcher's own screens or any other: the tests can run on someone's TV.
 */
fun waitForHomeScreen(timeoutMs: Long = HOME_TIMEOUT_MS) {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    var front: String? = null
    var since = 0L
    var nextBack = 0L
    while (true) {
        val home = if (Build.VERSION.SDK_INT >= 24) resolvedHomeActivity() else focusedHomeTaskActivity()
        val focus = shell("dumpsys window").lines()
        // mFocusedApp names an ActivityRecord{<hash> u0 <package>/<class> t<task>}, alone or
        // inside a token (before API 29); mCurrentFocus=Window{<hash> u0 <title>}, where an
        // activity's window has the title <package>/<class>, or mCurrentFocus=null.
        val app = focus.firstOrNull { "mFocusedApp=" in it }?.trim()
        val window = focus.firstOrNull { "mCurrentFocus=" in it }?.trim()
        val activity = app?.let { FOCUSED_ACTIVITY.find(it)?.groupValues?.get(1) }
        val title = window?.let { FOCUSED_WINDOW.find(it)?.groupValues?.get(1) }
        val now = SystemClock.uptimeMillis()
        val homePackage = home?.substringBefore('/')
        if (home != null && !home.endsWith("FallbackHome") &&
            activity?.substringBefore('/') == homePackage && title?.startsWith("$homePackage/") == true
        ) {
            val state = "$app $window"
            if (state != front) {
                front = state
                since = now
            } else if (now - since >= HOME_STABLE_MS) {
                return
            }
        } else {
            front = null
            // The activity and its window, so the key reaches that screen and nothing else.
            if ((activity to title) in FIRST_BOOT_SCREENS && now >= nextBack) {
                shell("input keyevent KEYCODE_BACK")
                nextBack = now + FIRST_BOOT_BACK_INTERVAL_MS
            }
        }
        if (now > deadline) {
            val homeFound = if (Build.VERSION.SDK_INT >= 24) {
                "HOME resolves to $home"
            } else {
                "the focused activity's task ${if (home == null) "wasn't" else "was"} started by HOME"
            }
            throw AssertionError(
                "The home screen didn't settle in ${timeoutMs / 1000} s: $homeFound, " +
                    "the focused activity is ${activity ?: "none"}, the focused window ${title ?: "none"}",
            )
        }
        SystemClock.sleep(HOME_POLL_MS)
    }
}

/** The activity a HOME intent resolves to (API 24+), e.g. com.luncher.launcher/.home.HomeActivity. */
private fun resolvedHomeActivity(): String? =
    // The last line is the activity.
    shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        .lines().lastOrNull { it.isNotBlank() }?.trim()?.takeIf { '/' in it }

/**
 * Before API 24: the focused activity, if a HOME intent started its task, e.g.
 * com.google.android.leanbacklauncher/.MainActivity. Its stack doesn't tell: on API 22 an app
 * started from the chooser joins the chooser's stack.
 */
private fun focusedHomeTaskActivity(): String? {
    // Each task is listed as "* TaskRecord{<hash> #<task> ...", followed by the intent={...} that
    // started it, and the dump ends with mFocusedActivity: ActivityRecord{<hash> u0 <activity> t<task>}.
    val homeTasks = mutableSetOf<String>()
    var task: String? = null
    for (line in shell("dumpsys activity activities").lines().map { it.trim() }) {
        TASK.find(line)?.let { task = it.groupValues[1] }
        if (line.startsWith("intent={") && "android.intent.category.HOME" in line) task?.let { homeTasks += it }
        val focused = FOCUSED_TASK_ACTIVITY.find(line) ?: continue
        return focused.groupValues[1].takeIf { focused.groupValues[2] in homeTasks }
    }
    return null
}

private fun shell(command: String): String {
    val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    return ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
}

private val FOCUSED_ACTIVITY = Regex("""ActivityRecord\{\S+ \S+ ([^ }]+)""")
private val FOCUSED_WINDOW = Regex("""mCurrentFocus=Window\{\S+ \S+ (.*)\}$""")
private val TASK = Regex("""^\* TaskRecord\{\S+ #(\d+) """)
private val FOCUSED_TASK_ACTIVITY = Regex("""^mFocusedActivity: ActivityRecord\{\S+ \S+ (\S+) t(\d+)\}""")

/**
 * Screens a first boot opens in front of the home app, which stay until Back: activity and window
 * title. "USB drive connected", on a new emulator's first boot, API 23 and 29 (app/README.md).
 */
private val FIRST_BOOT_SCREENS = listOf(
    "com.android.tv.settings/.device.storage.NewStorageActivity" to
        "com.android.tv.settings/com.android.tv.settings.device.storage.NewStorageActivity",
)
private const val FIRST_BOOT_BACK_INTERVAL_MS = 2_000L
private const val HOME_TIMEOUT_MS = 60_000L
private const val HOME_STABLE_MS = 3_000L
private const val HOME_POLL_MS = 500L
