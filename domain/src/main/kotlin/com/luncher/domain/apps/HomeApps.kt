package com.luncher.domain.apps

/**
 * The apps the home screen shows, in the order it shows them: every TV app except the launcher
 * itself ([launcherPackage]), sorted by label. Apps with the same label keep a fixed order, so the
 * screen doesn't reshuffle them between visits.
 */
fun homeApps(installed: List<InstalledApp>, launcherPackage: String): List<InstalledApp> =
    installed
        .filter { it.launchable.packageName != launcherPackage }
        .sortedWith(BY_LABEL)

private val BY_LABEL: Comparator<InstalledApp> =
    compareBy<InstalledApp, String>(String.CASE_INSENSITIVE_ORDER) { it.label }
        .thenBy { it.launchable.packageName }
        .thenBy { it.launchable.activityName }
