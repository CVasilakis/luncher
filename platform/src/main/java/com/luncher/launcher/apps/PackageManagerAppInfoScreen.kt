package com.luncher.launcher.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.luncher.domain.apps.AppInfoScreen

/**
 * [AppInfoScreen] on PackageManager: whether an activity answers the intent the home screen
 * starts for an app's info, `ACTION_APPLICATION_DETAILS_SETTINGS` with a `package:` Uri. Needs
 * the manifest's `<queries>` for it on API 30+, else the settings app is hidden from Luncher and
 * the page seems missing.
 */
class PackageManagerAppInfoScreen(private val context: Context) : AppInfoScreen {

    // With MATCH_DEFAULT_ONLY, as startActivity finds an implicit intent's activity (it adds
    // CATEGORY_DEFAULT). The settings apps' filter takes any package: Luncher's own stands for all.
    override fun exists(): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        return context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
    }
}
