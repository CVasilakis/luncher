package com.luncher.domain.settings

/** What the settings panel lists: tabs, each a list of groups of entries, in the order shown. */
data class SettingsMenu(val tabs: List<SettingsTab>)

/** One tab of the panel. */
data class SettingsTab(val groups: List<SettingsGroup>)

/** Entries shown together, set apart from the other groups of their tab. */
data class SettingsGroup(val entries: List<SettingsEntry>)

/** The settings Luncher offers, and where each one goes: Luncher's own first, then the device's. */
fun settingsMenu(): SettingsMenu =
    SettingsMenu(
        listOf(
            SettingsTab(
                listOf(
                    SettingsGroup(listOf(SettingsEntry.HideApps)),
                    SettingsGroup(listOf(SettingsEntry.SystemSettings)),
                ),
            ),
        ),
    )
