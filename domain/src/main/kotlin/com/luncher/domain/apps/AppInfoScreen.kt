package com.luncher.domain.apps

/**
 * Port: the page about one app in the device's settings (its storage, permissions, force stop,
 * uninstall), which a tile's menu opens (App info). Implemented in :platform on top of
 * PackageManager.
 *
 * Not every device has it: the Android TV 5.1 (API 22) emulator's settings have only the list of
 * all apps.
 */
interface AppInfoScreen {

    /**
     * Whether the device has the page. Asked each time a menu opens, not once: the settings app
     * that has it can be disabled or updated meanwhile, and asking is quick.
     */
    fun exists(): Boolean
}
