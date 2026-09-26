package com.luncher.launcher.apps

import android.content.Intent
import android.content.pm.PackageManager
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.apps.InstalledApps
import com.luncher.domain.apps.LaunchableApp

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
            )
        }

    private companion object {
        val TV_LAUNCHER_INTENT: Intent =
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    }
}
