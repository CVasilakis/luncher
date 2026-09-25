package com.luncher.domain.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LaunchableAppTest {

    @Test
    fun `the same package and activity are the same app`() {
        assertEquals(
            LaunchableApp("com.example.tv", "com.example.tv.Main"),
            LaunchableApp("com.example.tv", "com.example.tv.Main"),
        )
    }

    @Test
    fun `two launcher activities of one package are two apps`() {
        // Hiding or moving one of them must not affect the other.
        val apps = setOf(
            LaunchableApp("com.example.tv", "com.example.tv.Movies"),
            LaunchableApp("com.example.tv", "com.example.tv.Music"),
        )
        assertEquals(2, apps.size)
    }

    @Test
    fun `the same activity name in two packages is two apps`() {
        assertNotEquals(
            LaunchableApp("com.example.a", "MainActivity"),
            LaunchableApp("com.example.b", "MainActivity"),
        )
    }
}
