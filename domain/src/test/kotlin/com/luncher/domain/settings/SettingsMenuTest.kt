package com.luncher.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsMenuTest {

    @Test
    fun `lists Luncher's own settings, then the system settings`() {
        assertEquals(
            listOf(SettingsEntry.HideApps, SettingsEntry.Appearance, SettingsEntry.SystemSettings),
            settingsMenu(),
        )
    }

    @Test
    fun `lists each entry once`() {
        assertEquals(settingsMenu().distinct(), settingsMenu())
    }
}
