package com.luncher.launcher.apps

import android.content.Intent
import android.content.pm.PackageManager
import com.luncher.domain.apps.InstalledApps
import com.luncher.domain.apps.LaunchableApp

/** [InstalledApps] backed by PackageManager. Needs the manifest's `<queries>` on API 30+. */
class PackageManagerInstalledApps(private val packageManager: PackageManager) : InstalledApps {

    override fun tvApps(): List<LaunchableApp> =
        packageManager.queryIntentActivities(TV_LAUNCHER_INTENT, 0).map {
            LaunchableApp(it.activityInfo.packageName, it.activityInfo.name)
        }

    private companion object {
        val TV_LAUNCHER_INTENT: Intent =
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    }
}
