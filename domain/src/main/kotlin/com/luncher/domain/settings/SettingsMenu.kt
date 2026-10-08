package com.luncher.domain.settings

/** What the settings panel lists: groups of entries, in the order shown. */
data class SettingsMenu(val groups: List<SettingsGroup>)

/** Entries shown together, set apart from the other groups. */
data class SettingsGroup(val entries: List<SettingsEntry>)

/** The settings Luncher offers, and where each one goes: Luncher's own first, then the device's. */
fun settingsMenu(): SettingsMenu =
    SettingsMenu(
        listOf(
            SettingsGroup(listOf(SettingsEntry.HideApps)),
            SettingsGroup(listOf(SettingsEntry.SystemSettings)),
        ),
    )
