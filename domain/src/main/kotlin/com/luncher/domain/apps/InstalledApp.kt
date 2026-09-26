package com.luncher.domain.apps

/**
 * A TV app as the device reports it: which activity to start, and what the home screen needs to
 * show it. Only [launchable] identifies the app; the label changes with the language, for example.
 */
data class InstalledApp(
    val launchable: LaunchableApp,
    val label: String,
    /** Whether the app declares a TV banner (on its launcher activity or its application). */
    val hasBanner: Boolean,
)
