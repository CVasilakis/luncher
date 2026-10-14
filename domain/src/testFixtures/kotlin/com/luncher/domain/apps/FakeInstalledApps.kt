package com.luncher.domain.apps

/**
 * [InstalledApps] with a list the test controls; change [apps] to simulate installs, which tells
 * the listeners, as the real one does after the changes settled.
 */
class FakeInstalledApps(apps: List<InstalledApp> = emptyList()) : InstalledApps {

    constructor(vararg apps: InstalledApp) : this(apps.toList())

    private val listeners = mutableListOf<() -> Unit>()

    var apps: List<InstalledApp> = apps
        set(value) {
            field = value
            listeners.toList().forEach { it() }
        }

    override fun tvApps(): List<InstalledApp> = apps

    override fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    override fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    companion object {
        /**
         * An installed app with a plausible package and activity name, labeled [name] with a
         * capital first letter ("movies" is "Movies").
         */
        fun app(name: String, hasBanner: Boolean = false, updated: Long = 0) = InstalledApp(
            LaunchableApp("com.example.$name", "com.example.$name.MainActivity"),
            label = name.replaceFirstChar { it.uppercaseChar() },
            hasBanner = hasBanner,
            updated = updated,
        )
    }
}
