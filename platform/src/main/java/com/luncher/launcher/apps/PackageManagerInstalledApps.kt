package com.luncher.launcher.apps

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.apps.InstalledApps
import com.luncher.domain.apps.LaunchableApp
import java.io.File

/** [InstalledApps] backed by PackageManager. Needs the manifest's `<queries>` on API 30+. */
class PackageManagerInstalledApps(private val packageManager: PackageManager) : InstalledApps {

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

    private companion object {
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
