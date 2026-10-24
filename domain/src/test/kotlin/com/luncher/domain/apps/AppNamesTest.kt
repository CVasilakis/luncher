package com.luncher.domain.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppNamesTest {

    @Test
    fun `keeps the name typed`() {
        assertEquals("Kids videos", appName("Kids videos", "YouTube"))
    }

    @Test
    fun `trims the name`() {
        assertEquals("Kids videos", appName("  Kids videos \t", "YouTube"))
    }

    @Test
    fun `drops control characters`() {
        assertEquals("Kids videos", appName("Kids\u0000 videos\n", "YouTube"))
    }

    @Test
    fun `nothing typed is the app's own name again`() {
        assertNull(appName("", "YouTube"))
        assertNull(appName("  \n ", "YouTube"))
    }

    @Test
    fun `the app's own label is no name of the user's`() {
        assertNull(appName("YouTube", "YouTube"))
        assertNull(appName(" YouTube ", "YouTube"))
    }

    // Some system apps' labels carry invisible formatting characters: left in the field, still the label.
    @Test
    fun `the label with its formatting characters is the label`() {
        assertNull(appName("⁨Settings⁩", "⁨Settings⁩"))
    }

    @Test
    fun `another case is another name`() {
        assertEquals("youtube", appName("youtube", "YouTube"))
    }

    @Test
    fun `keeps a name of the longest length whole`() {
        val name = "a".repeat(MAX_APP_NAME)
        assertEquals(name, appName(name, "YouTube"))
    }

    @Test
    fun `cuts a longer name at its end`() {
        assertEquals("a".repeat(MAX_APP_NAME), appName("a".repeat(MAX_APP_NAME + 10), "YouTube"))
    }

    @Test
    fun `cuts where a character ends, never inside an emoji`() {
        val typed = "a".repeat(MAX_APP_NAME - 1) + "😀" + "b"   // the emoji's two units across the limit

        assertEquals("a".repeat(MAX_APP_NAME - 1), appName(typed, "YouTube"))
    }

    @Test
    fun `a name cut before a space ends without it`() {
        assertEquals("a".repeat(MAX_APP_NAME - 1), appName("a".repeat(MAX_APP_NAME - 1) + " bcd", "YouTube"))
    }
}
