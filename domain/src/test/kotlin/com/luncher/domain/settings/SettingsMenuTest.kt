package com.luncher.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMenuTest {

    private val menu = settingsMenu()

    private fun entries() = menu.groups.flatMap { it.entries }

    @Test
    fun `offers the system settings`() {
        assertTrue(SettingsEntry.SystemSettings in entries())
    }

    @Test
    fun `offers hiding apps, before the system settings`() {
        assertTrue(entries().indexOf(SettingsEntry.HideApps) in 0 until entries().indexOf(SettingsEntry.SystemSettings))
    }

    @Test
    fun `lists each entry once`() {
        assertEquals(entries().distinct(), entries())
    }

    // The panel would show nothing to focus, or a gap for an empty group.
    @Test
    fun `has no empty group`() {
        assertTrue(menu.groups.isNotEmpty())
        menu.groups.forEach { assertTrue(it.entries.isNotEmpty()) }
    }
}
