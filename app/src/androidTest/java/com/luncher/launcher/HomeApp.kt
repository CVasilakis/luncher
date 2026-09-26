package com.luncher.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
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
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    if (Build.VERSION.SDK_INT < 24) {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return instrumentation.targetContext.packageManager
            .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }
    val output = instrumentation.uiAutomation.executeShellCommand(
        "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME",
    )
    val lines = ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readLines() }
    // The last line is the activity, e.g. com.luncher.launcher/.home.HomeActivity.
    return lines.lastOrNull { it.isNotBlank() }?.takeIf { '/' in it }?.substringBefore('/')
}
