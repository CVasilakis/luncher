package com.luncher.domain.appearance

/**
 * Port: where the user's [Appearance] is kept. Implemented in :platform on SharedPreferences.
 *
 * A screen that shows the appearance reads it, then again whenever a listener is called, so
 * nothing has to poll (docs/ARCHITECTURE.md#background-work-and-changes-while-shown).
 */
interface AppearanceSettings {

    fun read(): Appearance

    /** Stores [appearance] at once, and tells the listeners. */
    fun write(appearance: Appearance)

    /** Calls [listener] on the main thread after every [write], whichever screen made it. */
    fun addListener(listener: () -> Unit)

    fun removeListener(listener: () -> Unit)
}
