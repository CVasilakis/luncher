package com.luncher.domain.apps

import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAppsTest {

    private val launcher = "com.luncher.launcher"

    private fun labels(apps: List<InstalledApp>) = apps.map { it.label }

    @Test
    fun `sorts apps by label`() {
        val shown = homeApps(listOf(app("news"), app("movies"), app("games")), launcher)

        assertEquals(listOf("Games", "Movies", "News"), labels(shown))
    }

    @Test
    fun `sorts regardless of upper and lower case`() {
        val installed = listOf(app("b").copy(label = "b"), app("a").copy(label = "A"), app("c").copy(label = "C"))

        assertEquals(listOf("A", "b", "C"), labels(homeApps(installed, launcher)))
    }

    @Test
    fun `leaves out the launcher itself`() {
        val self = InstalledApp(LaunchableApp(launcher, "$launcher.home.HomeActivity"), "Luncher", hasBanner = true)

        assertEquals(listOf("Movies"), labels(homeApps(listOf(self, app("movies")), launcher)))
    }

    @Test
    fun `keeps a fixed order for apps with the same label`() {
        val first = app("a").copy(label = "Same")
        val second = app("b").copy(label = "Same")

        assertEquals(homeApps(listOf(first, second), launcher), homeApps(listOf(second, first), launcher))
    }

    @Test
    fun `shows nothing when nothing is installed`() {
        assertEquals(emptyList<InstalledApp>(), homeApps(emptyList(), launcher))
    }
}
