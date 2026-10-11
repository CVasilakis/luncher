package com.luncher.domain.apps

/**
 * Port: where the user's [AppArrangement] is kept. Implemented in :platform on top of preferences.
 *
 * A screen that shows the apps reads it, then again whenever a listener is called: the home screen
 * shows an app hidden in the settings at once, behind the panel
 * (docs/ARCHITECTURE.md#background-work-and-changes-while-shown).
 */
interface AppArrangements {

    /** The stored arrangement; [AppArrangement.NONE] if there's none yet. */
    fun read(): AppArrangement

    /** Stores [arrangement] in place of the previous one, and tells the listeners. */
    fun save(arrangement: AppArrangement)

    /** Calls [listener] on the main thread after every [save], whichever screen made it. */
    fun addListener(listener: () -> Unit)

    fun removeListener(listener: () -> Unit)
}
