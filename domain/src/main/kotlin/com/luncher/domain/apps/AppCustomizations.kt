package com.luncher.domain.apps

/**
 * Port: where what the user set for each app ([AppCustomization]) is kept. Implemented in
 * :platform on top of preferences.
 *
 * It may name apps that aren't installed right now, so that an app that comes back keeps what the
 * user set for it; [homeApps] matches it to the installed apps, as it does the arrangement. A
 * screen that names the apps reads it, then again whenever a listener is called
 * (docs/ARCHITECTURE.md#background-work-and-changes-while-shown).
 */
interface AppCustomizations {

    /** What's stored, by app; an app with nothing set isn't in it. Empty if nothing was ever set. */
    fun read(): Map<LaunchableApp, AppCustomization>

    /** Stores [customizations] in place of everything stored before, and tells the listeners. */
    fun save(customizations: Map<LaunchableApp, AppCustomization>)

    /** Calls [listener] on the main thread after every [save], whichever screen made it. */
    fun addListener(listener: () -> Unit)

    fun removeListener(listener: () -> Unit)
}
