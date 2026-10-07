package com.luncher.launcher.testing

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
 * cold start (such as the one `HomeKeyTest` causes as it ends); the tests that open screens
 * recover from that with [RetryWhenCovered].
 *
 * Fails after [timeoutMs], saying what was in front: a long limit, for the device on its way to
 * its home screen, which on a slow host takes minutes ([HomeLook.anotherApp] says which states
 * count: the home app's own screens, Settings' FallbackHome, no focused window). A screen of
 * another app is never on that way: once the same one has kept the focus for [otherAppTimeoutMs]
 * (an app left open on someone's TV, a dialog), it fails, naming that screen, rather than make every
 * test wait the long limit on a device whose home screen can't come.
 *
 * The one key it presses is Back on [FIRST_BOOT_SCREENS], screens a first boot opens in front of
 * the home app, which stay until Back, and only while one of them has the focus. Nothing else
 * gets a key, on the stock launcher's own screens or any other: the tests can run on someone's TV.
 */
fun waitForHomeScreen(timeoutMs: Long = HOME_TIMEOUT_MS, otherAppTimeoutMs: Long = OTHER_APP_TIMEOUT_MS) {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    var settled: Focus? = null
    var other: Focus? = null
    var otherSince = 0L
    var nextBack = 0L
    while (true) {
        val home = if (Build.VERSION.SDK_INT >= 24) resolvedHomeActivity() else null
        val look = HomeLook(Build.VERSION.SDK_INT, home, shell("dumpsys window"), shell("dumpsys activity activities"))
        val focus = look.focus
        val now = SystemClock.uptimeMillis()
        if (look.isSettled) {
            if (focus == settled) return
            settled = focus
            other = null
        } else {
            settled = null
            // The activity and its window, so the key reaches that screen and nothing else.
            val firstBootScreen = (focus.activity to focus.window) in FIRST_BOOT_SCREENS
            if (firstBootScreen && now >= nextBack) {
                shell("input keyevent KEYCODE_BACK")
                nextBack = now + FIRST_BOOT_BACK_INTERVAL_MS
            }
            // The same screen of another app all along, other than one that gets Back.
            val another = look.anotherApp.takeUnless { firstBootScreen }
            if (another != other) {
                other = another
                otherSince = now
            } else if (another != null && now - otherSince >= otherAppTimeoutMs) {
                throw AssertionError(
                    "A screen of another app than the home app has kept the focus for ${otherAppTimeoutMs / 1000} s, " +
                        "so the home screen can't come; the tests press no key on it. Close it on the device. $look",
                )
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
     * The home app's packages: the one HOME resolves to, or before API 24 those of the activities
     * in the tasks HOME started. None while it's not known: HOME resolves to nothing yet, or to
     * Settings' FallbackHome, before the user is unlocked.
     */
    private val homePackages: Set<String> = when {
        sdk < 24 -> entries.filter { it.task in homeTasks }.map { it.component.substringBefore('/') }.toSet()
        home == null || home.endsWith("FallbackHome") -> emptySet()
        else -> setOf(home.substringBefore('/'))
    }

    /**
     * What has the focus, when that's a screen of another app than the home app; null while the
     * device is on its way to its home screen: no window has the focus (right after a boot, or
     * while one screen hands over to the next), the home app isn't known yet ([homePackages]), or
     * the focused window is the home app's, whichever of its screens and tasks (Google TV's
     * DispatchActivity in a task of its own, its profile chooser or sign-in screen while it
     * starts; on API 22 the "choose home app" dialog, of package android, in the task Home
     * started). A window that isn't an activity's (its title has no package, e.g. a dialog of the
     * system) is another app's too.
     */
    val anotherApp: Focus? = focus.takeIf {
        it.window != null && homePackages.isNotEmpty() && it.window.substringBefore('/') !in homePackages
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

/**
 * Whether `dumpsys activity activities` ([activities]) lists a "choose home app" dialog that hasn't
 * begun to finish. On API 22, with a second home app, Home opens that dialog (from API 23 on Home
 * never asks, app/README.md), and the dialog finishes itself once it's stopped. Covered by another
 * screen, it stays paused, not finishing, for most of a second (0.7 s on the emulator), and a HOME
 * intent that comes then goes to it and is lost with it: nothing comes to the front. Once it has
 * begun to finish it's listed as finishing, for seconds more, and Home opens a new dialog.
 */
internal fun homeChooserNotFinishing(activities: String): Boolean =
    activitiesIn(activities).any { it.component == HOME_CHOOSER && it.finishing == false }

/** An activity of `dumpsys activity activities`: its record, component, task, state, finishing and idle marks. */
private data class ActivityEntry(
    val record: String,
    val component: String,
    val task: String,
    val state: String? = null,
    val finishing: Boolean? = null,
    val idle: Boolean? = null,
) {
    override fun toString() =
        "$component in task $task, ${state ?: "no state"}, ${if (idle == true) "idle" else "not idle"}"
}

/**
 * The activities, from the top down: each a "* Hist #<n>: ActivityRecord{<hash> u0 <activity>
 * t<task>…" line, then "state=<state> … finishing=<true|false>" and "… idle=<true|false> …" lines
 * of its own.
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
        // Each activity's first state, finishing and idle marks: its own.
        val last = entries.lastOrNull() ?: continue
        val state = last.state ?: STATE.find(line)?.groupValues?.get(1)
        val finishing = last.finishing ?: FINISHING.find(line)?.groupValues?.get(1)?.toBoolean()
        val idle = last.idle ?: IDLE.find(line)?.groupValues?.get(1)?.toBoolean()
        entries[entries.lastIndex] = last.copy(state = state, finishing = finishing, idle = idle)
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
private val FINISHING = Regex("""(?:^| )finishing=(true|false)\b""")
private val IDLE = Regex("""(?:^| )idle=(true|false)\b""")

/** API 22's "choose home app" dialog, as `dumpsys activity activities` names it. */
private const val HOME_CHOOSER = "android/com.android.internal.app.ResolverActivity"

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
/**
 * How long [waitForHomeScreen] waits for the device on its way to its home screen; it returns as
 * soon as the home screen has settled, so this only decides how long a failure takes. On an
 * emulator starved of CPU (one CPU shared with busy loops), Google TV's launcher held its
 * `DispatchActivity` in front for over 4 minutes when it started again before a test, and a first
 * test right after a boot waited 101.6 s. This leaves room for a host slower still.
 */
private const val HOME_TIMEOUT_MS = 600_000L

/**
 * How long a screen of another app may keep the focus in [waitForHomeScreen]. Nothing on the way to
 * the home screen holds it long: a screen of the app closing after a test, or Luncher's home screen
 * while the stock launcher starts cold after `HomeKeyTest`, gives way within the whole wait, which
 * took 37.2 s at the longest on a starved emulator apart from Google TV's DispatchActivity (the home
 * app's own screen, under the long limit), and seconds unstarved.
 */
private const val OTHER_APP_TIMEOUT_MS = 60_000L
private const val HOME_POLL_MS = 500L
