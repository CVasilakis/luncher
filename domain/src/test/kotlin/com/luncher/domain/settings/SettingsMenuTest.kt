package com.luncher.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMenuTest {

    private val menu = settingsMenu()

    private fun entries() = menu.tabs.flatMap { it.groups }.flatMap { it.entries }

    @Test
    fun `offers the system settings`() {
        assertTrue(SettingsEntry.SystemSettings in entries())
    }

    @Test
    fun `lists each entry once`() {
        assertEquals(entries().distinct(), entries())
    }

    // The panel would show an empty tab or a gap for an empty group.
    @Test
    fun `has no empty tab or group`() {
        assertTrue(menu.tabs.isNotEmpty())
        menu.tabs.forEach { tab ->
            assertTrue(tab.groups.isNotEmpty())
            tab.groups.forEach { assertTrue(it.entries.isNotEmpty()) }
        }
    }
}
