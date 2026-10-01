package com.luncher.launcher

import android.content.ComponentName
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
 * focus on a busy host (on CI's API 34, no test's window ever got it). Google TV's launcher (API
 * 30-33) then first shows `DispatchActivity`, in a task of its own, while it decides what to show
 * (over 160 s on a starved emulator), and then brings its home screen over it, and over anything
 * a test started meanwhile.
 *
 * Settled is when the same activity and window, at two looks in a row, are the home screen
 * ([HomeLook.isSettled]): the activity is the top one, in the home app's task, resumed and idle,
 * and its own window has the focus (keys go only there). The home app's task is the task of the
 * activity HOME resolves to, from API 24 on, once that's no longer Settings' FallbackHome (which
 * holds the screen until the user is unlocked after a boot). API 22 and 23 have neither `cmd` to
 * ask nor FallbackHome: there it's a task a HOME intent started. That's also how API 22's "choose
 * home app" dialog counts, which is in front after a test there. A screen the home app opens over
 * itself, in its task, like that promotion or the profile chooser, is the home screen once it is
 * the top activity, and whatever a test starts goes above it. A trampoline like DispatchActivity
 * is in a task of its own, so it never counts. Idle is Android's own mark that an activity has
 * finished starting: its main thread has gone idle after the resume (or 10 s have passed). After
 * Home, Google TV's launcher opened its chooser before that, every time. What no look can foresee
 * is a screen the home app decides to open later, once some background work of its own is done:
 * after the boot's DispatchActivity, Google TV's HomeActivity was in front, idle, for 4 s and for
 * 37 s before it opened its chooser (on a starved emulator), and that promotion can come after a
 * cold start (`HomeKeyTest` waits 30 s after the one it causes). Fails after [timeoutMs], saying
 * what was in front.
 *
 * The one key it presses is Back on [FIRST_BOOT_SCREENS], screens a first boot opens in front of
 * the home app, which stay until Back, and only while one of them has the focus. Nothing else
 * gets a key, on the stock launcher's own screens or any other: the tests can run on someone's TV.
 */
fun waitForHomeScreen(timeoutMs: Long = HOME_TIMEOUT_MS) {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    var settled: Focus? = null
    var nextBack = 0L
    while (true) {
        val home = if (Build.VERSION.SDK_INT >= 24) resolvedHomeActivity() else null
        val look = HomeLook(Build.VERSION.SDK_INT, home, shell("dumpsys window"), shell("dumpsys activity activities"))
        val focus = look.focus
        val now = SystemClock.uptimeMillis()
        if (look.isSettled) {
            if (focus == settled) return
            settled = focus
        } else {
            settled = null
            // The activity and its window, so the key reaches that screen and nothing else.
            if ((focus.activity to focus.window) in FIRST_BOOT_SCREENS && now >= nextBack) {
                shell("input keyevent KEYCODE_BACK")
                nextBack = now + FIRST_BOOT_BACK_INTERVAL_MS
            }
        }
        if (now > deadline) {
            throw AssertionError("The home screen didn't settle in ${timeoutMs / 1000} s: $look")
        }
        SystemClock.sleep(HOME_POLL_MS)
    }
}

/** The activity a HOME intent resolves to (API 24+), e.g. com.luncher.launcher/.home.HomeActivity. */
private fun resolvedHomeActivity(): String? = resolvedActivity(Intent.ACTION_MAIN, Intent.CATEGORY_HOME)

/**
 * The activity an intent with [action] (and [category]) resolves to, from API 24 on, e.g.
 * com.android.tv.settings/.MainSettings for android.settings.SETTINGS.
 */
fun resolvedActivity(action: String, category: String? = null): String? =
    // The last line is the activity.
    shell("cmd package resolve-activity --brief -a $action" + (category?.let { " -c $it" } ?: ""))
        .lines().lastOrNull { it.isNotBlank() }?.trim()?.takeIf { '/' in it }

/**
 * One look at the device, from the output of `cmd package resolve-activity` for HOME ([home], from
 * [sdk] 24 on; null before, or if nothing handles Home), `dumpsys window` ([windows]) and `dumpsys
 * activity activities` ([activities]): whether it shows the home screen, as [waitForHomeScreen]
 * means it. Apart from the shell, so the decision can be tested on dumps the emulators produced.
 */
internal class HomeLook(private val sdk: Int, private val home: String?, windows: String, activities: String) {

    /** What has the input focus. */
    val focus = focusIn(windows)

    private val entries = activitiesIn(activities)

    /** The activity in front of all others: the dump lists them from the top down. */
    private val top = entries.firstOrNull()

    /** The home app's tasks: of the activity HOME resolves to, or before API 24 those HOME started. */
    private val homeTasks: Set<String> = when {
        sdk < 24 -> tasksStartedByHome(activities)
        home == null || home.endsWith("FallbackHome") -> emptySet()
        else -> entries.filter { it.component == home }.map { it.task }.toSet()
    }

    /**
     * Whether the top activity is in a task of the home app, resumed and idle, the focused
     * activity, and its own window has the focus. Whether it stays so takes a second look.
     */
    val isSettled: Boolean =
        top != null && top.task in homeTasks && top.state == "RESUMED" && top.idle == true &&
            focus.record == top.record && focus.activity == top.component &&
            focus.window == ComponentName.unflattenFromString(top.component)?.flattenToString()

    override fun toString(): String {
        val homeFound = if (sdk >= 24) "HOME resolves to $home" else "the tasks HOME started are $homeTasks"
        return "$homeFound, $focus, the top activity is ${top ?: "none"}"
    }
}

/** An activity of `dumpsys activity activities`: its record, component, task, state and idle mark. */
private data class ActivityEntry(
    val record: String,
    val component: String,
    val task: String,
    val state: String? = null,
    val idle: Boolean? = null,
) {
    override fun toString() =
        "$component in task $task, ${state ?: "no state"}, ${if (idle == true) "idle" else "not idle"}"
}

/**
 * The activities, from the top down: each a "* Hist #<n>: ActivityRecord{<hash> u0 <activity>
 * t<task>…" line, then "state=<state> …" and "… idle=<true|false> …" lines of its own.
 */
private fun activitiesIn(activities: String): List<ActivityEntry> {
    val entries = mutableListOf<ActivityEntry>()
    for (line in activities.lines().map { it.trim() }) {
        val hist = HIST.find(line)
        if (hist != null) {
            val (record, component, task) = hist.destructured
            entries += ActivityEntry(record, component, task)
            continue
        }
        // Each activity's first state and idle mark: its own.
        val last = entries.lastOrNull() ?: continue
        val state = last.state ?: STATE.find(line)?.groupValues?.get(1)
        val idle = last.idle ?: IDLE.find(line)?.groupValues?.get(1)?.toBoolean()
        entries[entries.lastIndex] = last.copy(state = state, idle = idle)
    }
    return entries
}

/**
 * Before API 24: the tasks a HOME intent started. Their stack doesn't tell: on API 22 an app
 * started from the chooser joins the chooser's stack.
 */
private fun tasksStartedByHome(activities: String): Set<String> {
    // Each task is listed as "* TaskRecord{<hash> #<task> ...", followed by the intent={...} that started it.
    val homeTasks = mutableSetOf<String>()
    var task: String? = null
    for (line in activities.lines().map { it.trim() }) {
        TASK.find(line)?.let { task = it.groupValues[1] }
        if (line.startsWith("intent={") && "android.intent.category.HOME" in line) task?.let { homeTasks += it }
    }
    return homeTasks
}

internal fun shell(command: String): String {
    val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    return ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
}

private val TASK = Regex("""^\* TaskRecord\{\S+ #(\d+) """)

// The task follows the activity, in its braces (… <activity> t5}), or on API 33 after them
// (… <activity>} t5}); " f}" marks a finishing one.
private val HIST = Regex("""^\* Hist +#\d+: ActivityRecord\{(\S+) \S+ ([^ }]+)\}? t(\d+)""")
private val STATE = Regex("""^state=(\w+)""")
private val IDLE = Regex("""(?:^| )idle=(true|false)\b""")

/**
 * Screens a first boot opens in front of the home app, which stay until Back: activity and window
 * title. "USB drive connected", for the SD card, on a new emulator's first boot, API 23 and 29
 * (app/README.md).
 */
private val FIRST_BOOT_SCREENS = listOf(
    "com.android.tv.settings/.device.storage.NewStorageActivity" to
        "com.android.tv.settings/com.android.tv.settings.device.storage.NewStorageActivity",
)
private const val FIRST_BOOT_BACK_INTERVAL_MS = 2_000L
private const val HOME_TIMEOUT_MS = 60_000L
private const val HOME_POLL_MS = 500L
