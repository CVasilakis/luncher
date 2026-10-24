package com.luncher.domain.apps

/**
 * An app as the user set it up: the [installed] app, as the device reports it, with what the
 * user set for it ([customization]), matched by [homeApps]. What the screens show and act on.
 *
 * The home screen names it [name]; the device's own label is [installed]'s, which the Hide apps
 * list shows as the real name. One type for both, so every place that names an app says which.
 */
data class HomeApp(
    val installed: InstalledApp,
    val customization: AppCustomization = AppCustomization.NONE,
) {
    /** The app's identity, the installed app's. */
    val launchable: LaunchableApp get() = installed.launchable

    /** The app's name on the home screen: the user's, or else the device's label. */
    val name: String get() = customization.name ?: installed.label
}
