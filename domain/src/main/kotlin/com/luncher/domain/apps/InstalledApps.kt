package com.luncher.domain.apps

/**
 * Port: the apps installed on the device. Implemented in :platform on top of PackageManager.
 *
 * A screen that shows the apps reads them, then again whenever a listener is called: an app
 * installed, updated or removed while the home screen is shown shows at once
 * (docs/ARCHITECTURE.md#background-work-and-changes-while-shown).
 */
interface InstalledApps {

    /** Apps with a TV launcher entry (`MAIN` + `LEANBACK_LAUNCHER`), in no particular order. */
    fun tvApps(): List<InstalledApp>

    /**
     * Calls [listener] on the main thread after apps were installed, updated, removed, enabled or
     * disabled, any app, not only TV ones: once for changes that come together, as an app store's
     * updates do.
     */
    fun addListener(listener: () -> Unit)

    fun removeListener(listener: () -> Unit)
}
