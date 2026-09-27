package com.luncher.domain.settings

/**
 * One entry of the settings panel. Each kind of entry is a type of its own, so :app handles every
 * one of them (its label, what OK on it does) in an exhaustive `when`: a new entry that :app
 * doesn't handle yet doesn't compile.
 */
sealed interface SettingsEntry {

    /** Opens the device's own settings app. */
    data object SystemSettings : SettingsEntry
}
