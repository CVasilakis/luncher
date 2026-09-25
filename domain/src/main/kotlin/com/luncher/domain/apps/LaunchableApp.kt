package com.luncher.domain.apps

/**
 * An app the home screen can start: the activity that handles its TV launcher intent.
 * Package and activity together identify it; one package can have several launchable activities.
 */
data class LaunchableApp(
    val packageName: String,
    val activityName: String,
)
