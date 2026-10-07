package com.luncher.launcher.testing

import android.os.Build
import org.junit.runner.Description
import org.junit.runner.manipulation.Filter

/**
 * Leaves the system tier (every test in the `system` package: UI Automator, across apps) out on
 * devices below [MIN_SDK]. Older Android versions differ most in exactly what those tests drive
 * (no `cmd` before API 24, a home chooser on API 22), and UI Automator itself needs API 23; the
 * in-app tests still run there. The runner's `filter` argument (app/build.gradle.kts) applies it
 * to every run, so a new system test needs nothing of its own.
 */
class SystemTierFilter : Filter() {

    override fun shouldRun(description: Description): Boolean =
        Build.VERSION.SDK_INT >= MIN_SDK || description.className?.startsWith(SYSTEM_PACKAGE) != true

    override fun describe(): String = "system tier from API $MIN_SDK on"

    companion object {
        const val MIN_SDK = 24
        const val SYSTEM_PACKAGE = "com.luncher.launcher.system."
    }
}
