package com.luncher.domain.apps

/**
 * [AppCustomizations] in memory; [customizations] is what's stored, and what the test checks after
 * a save. Setting it tells the listeners, as a save does.
 */
class FakeAppCustomizations(customizations: Map<LaunchableApp, AppCustomization> = emptyMap()) : AppCustomizations {

    private val listeners = mutableListOf<() -> Unit>()

    /** How many listeners are registered: 0 while nothing shows the apps. */
    val listenerCount: Int get() = listeners.size

    var customizations: Map<LaunchableApp, AppCustomization> = customizations
        set(value) {
            field = value
            listeners.toList().forEach { it() }
        }

    override fun read(): Map<LaunchableApp, AppCustomization> = customizations

    override fun save(customizations: Map<LaunchableApp, AppCustomization>) {
        this.customizations = customizations
    }

    override fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    override fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    companion object {
        /** [app] named [name] by the user. */
        fun named(app: InstalledApp, name: String): Pair<LaunchableApp, AppCustomization> =
            app.launchable to AppCustomization(name = name)
    }
}
