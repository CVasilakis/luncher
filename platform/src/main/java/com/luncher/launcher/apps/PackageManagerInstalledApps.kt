package com.luncher.launcher.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.apps.InstalledApps
import com.luncher.domain.apps.LaunchableApp
import java.io.File

/**
 * [InstalledApps] backed by PackageManager. Needs the manifest's `<queries>` on API 30+.
 *
 * Changes come from [LauncherApps.Callback], which Android calls, through the main thread's
 * queue, once for each app installed, updated, removed, enabled or disabled, or on storage that
 * came or went: nothing runs between changes. It's registered only while there are listeners, so
 * a hidden home screen costs nothing. The listeners are told [SETTLE_MILLIS] after the last of
 * changes that come together, so an app store updating many apps reads the apps once.
 */
class PackageManagerInstalledApps(context: Context) : InstalledApps {

    private val packageManager = context.packageManager

    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    private val main = Handler(Looper.getMainLooper())

    private val listeners = mutableListOf<() -> Unit>()

    private val tellListeners = Runnable { listeners.toList().forEach { it() } }

    // Android's own reports, once per change; a suspended app stays listed, so those aren't heard.
    private val changes = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed()

        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed()

        override fun onPackageChanged(packageName: String, user: UserHandle) = changed()

        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = changed()

        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = changed()
    }

    private fun changed() {
        main.removeCallbacks(tellListeners)
        main.postDelayed(tellListeners, SETTLE_MILLIS)
    }

    override fun tvApps(): List<InstalledApp> =
        packageManager.queryIntentActivities(TV_LAUNCHER_INTENT, 0).map {
            val activity = it.activityInfo
            InstalledApp(
                LaunchableApp(activity.packageName, activity.name),
                label = it.loadLabel(packageManager).toString(),
                // Only whether one is declared: loading the image is left to the screen that shows it.
                hasBanner = activity.banner != 0 || activity.applicationInfo.banner != 0,
                updated = activity.applicationInfo.installTime(),
            )
        }

    override fun addListener(listener: () -> Unit) {
        if (listeners.isEmpty()) launcherApps.registerCallback(changes, main)
        listeners += listener
    }

    override fun removeListener(listener: () -> Unit) {
        if (listeners.remove(listener) && listeners.isEmpty()) {
            launcherApps.unregisterCallback(changes)
            main.removeCallbacks(tellListeners)
        }
    }

    private companion object {
        /** How long after a change the listeners are told, unless another change comes first. */
        const val SETTLE_MILLIS = 500L

        val TV_LAUNCHER_INTENT: Intent =
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    }
}

/**
 * When the app's APK was written, which an install or an update does: from the file, which the
 * query already names, rather than from PackageManager, a call to the system for each app. 0 if
 * the file can't be read.
 */
internal fun ApplicationInfo.installTime(): Long = sourceDir?.let { File(it).lastModified() } ?: 0L
