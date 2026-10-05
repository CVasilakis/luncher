package com.luncher.launcher

import kotlin.math.roundToLong

/**
 * The first line of the last crash of [packageName]'s app since [sinceMs] (the device's clock, as
 * `System.currentTimeMillis()`), e.g. `java.lang.NullPointerException: …`, from Android's crash log
 * (`logcat -b crash`), or null if it logged none. For a test whose wait for another app's screen
 * failed: that app may have crashed, which the failed wait alone doesn't say.
 */
fun crashSince(packageName: String, sinceMs: Long): String? =
    crashIn(shell("logcat -b crash -d -v epoch"), packageName, sinceMs)

/**
 * [crashSince], from the crash log as `logcat -b crash -v epoch` prints it: a Java crash as
 * "AndroidRuntime: FATAL EXCEPTION: <thread>", "AndroidRuntime: Process: <process>, PID: <pid>",
 * then the exception's lines, each line starting with its time (seconds since the epoch, to the
 * millisecond). The app's processes are [packageName] and those named `<packageName>:<name>`.
 */
internal fun crashIn(log: String, packageName: String, sinceMs: Long): String? {
    val process = Regex("""AndroidRuntime: Process: ${Regex.escape(packageName)}(:\S+)?, PID: """)
    val lines = log.lines()
    var crash: String? = null
    lines.forEachIndexed { i, line ->
        if (!process.containsMatchIn(line)) return@forEachIndexed
        val time = line.trim().substringBefore(' ').toDoubleOrNull() ?: return@forEachIndexed
        if ((time * 1000).roundToLong() < sinceMs) return@forEachIndexed
        crash = lines.getOrNull(i + 1)?.substringAfter("AndroidRuntime: ", "")?.trim()?.takeIf { it.isNotEmpty() }
            ?: "no exception logged"
    }
    return crash
}
