package com.luncher.domain.apps

/** [InstalledApps] with a list the test controls; change [apps] to simulate installs. */
class FakeInstalledApps(var apps: List<InstalledApp> = emptyList()) : InstalledApps {

    constructor(vararg apps: InstalledApp) : this(apps.toList())

    override fun tvApps(): List<InstalledApp> = apps

    companion object {
        /**
         * An installed app with a plausible package and activity name, labeled [name] with a
         * capital first letter ("movies" is "Movies").
         */
        fun app(name: String, hasBanner: Boolean = false) = InstalledApp(
            LaunchableApp("com.example.$name", "com.example.$name.MainActivity"),
            label = name.replaceFirstChar { it.uppercaseChar() },
            hasBanner = hasBanner,
        )
    }
}
