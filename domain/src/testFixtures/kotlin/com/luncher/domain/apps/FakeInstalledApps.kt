package com.luncher.domain.apps

/** [InstalledApps] with a list the test controls; change [apps] to simulate installs. */
class FakeInstalledApps(var apps: List<LaunchableApp> = emptyList()) : InstalledApps {

    constructor(vararg apps: LaunchableApp) : this(apps.toList())

    override fun tvApps(): List<LaunchableApp> = apps

    companion object {
        /** A launchable app with a plausible package and activity name. */
        fun app(name: String) = LaunchableApp("com.example.$name", "com.example.$name.MainActivity")
    }
}
