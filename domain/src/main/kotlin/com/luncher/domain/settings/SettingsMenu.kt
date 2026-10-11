package com.luncher.domain.settings

/** The settings Luncher offers, in the order the panel lists them: Luncher's own first, then the device's. */
fun settingsMenu(): List<SettingsEntry> =
    listOf(SettingsEntry.HideApps, SettingsEntry.Appearance, SettingsEntry.SystemSettings)
