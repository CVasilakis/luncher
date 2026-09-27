package com.luncher.domain.apps

import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrangedAppsTest {

    private val installed = listOf(app("games"), app("movies"), app("music"), app("news"))

    private fun arranged(arrangement: AppArrangement = AppArrangement.NONE) =
        homeApps(installed, "com.luncher.launcher", arrangement)

    private fun labels(apps: List<InstalledApp>) = apps.map { it.label }

    private fun id(name: String) = app(name).launchable

    @Test
    fun `hiding an app puts it first among the hidden apps`() {
        val result = arranged().hide(app("music")).hide(app("games"))

        assertEquals(listOf("Movies", "News"), labels(result.shown))
        assertEquals(listOf("Games", "Music"), labels(result.hidden))
    }

    @Test
    fun `hiding keeps the order of the shown apps`() {
        val order = listOf(id("news"), id("games"), id("music"), id("movies"))

        val result = arranged(AppArrangement(order, hidden = emptyList())).hide(app("games"))

        assertEquals(listOf("News", "Music", "Movies"), labels(result.shown))
    }

    @Test
    fun `showing an app puts it in its place by label while there's no order`() {
        val result = arranged(AppArrangement(order = null, hidden = listOf(id("games")))).show(app("games"))

        assertEquals(listOf("Games", "Movies", "Music", "News"), labels(result.shown))
        assertEquals(emptyList<InstalledApp>(), result.hidden)
    }

    @Test
    fun `showing an app puts it last in the user's order`() {
        val arrangement = AppArrangement(order = listOf(id("news"), id("movies"), id("music")), hidden = listOf(id("games")))

        val result = arranged(arrangement).show(app("games"))

        assertEquals(listOf("News", "Movies", "Music", "Games"), labels(result.shown))
    }

    @Test
    fun `hiding and showing doesn't fix the order`() {
        val result = arranged().hide(app("music")).show(app("music"))

        assertNull(result.arrangement.order)
        assertEquals(emptyList<LaunchableApp>(), result.arrangement.hidden)
    }

    @Test
    fun `stores the hidden apps in their order`() {
        val result = arranged().hide(app("music")).hide(app("games"))

        assertEquals(AppArrangement(order = null, hidden = listOf(id("games"), id("music"))), result.arrangement)
    }

    @Test
    fun `stores the shown apps in their order, including apps installed since it was stored`() {
        val arrangement = AppArrangement(order = listOf(id("news"), id("movies")), hidden = emptyList())

        val result = arranged(arrangement).hide(app("movies"))

        assertEquals(listOf(id("news"), id("games"), id("music")), result.arrangement.order)
    }

    @Test
    fun `keeps apps that aren't installed after the app they followed`() {
        val away = LaunchableApp("com.example.away", "com.example.away.MainActivity")
        val arrangement = AppArrangement(order = listOf(id("news"), away, id("movies")), hidden = emptyList())

        val result = arranged(arrangement).hide(app("games"))

        assertEquals(listOf(id("news"), away, id("movies"), id("music")), result.arrangement.order)
    }

    @Test
    fun `keeps an app that isn't installed after the nearest app still before it`() {
        val away = LaunchableApp("com.example.away", "com.example.away.MainActivity")
        val arrangement = AppArrangement(order = listOf(id("news"), id("movies"), away), hidden = emptyList())

        val result = arranged(arrangement).hide(app("movies"))

        assertEquals(listOf(id("news"), away, id("games"), id("music")), result.arrangement.order)
    }

    @Test
    fun `keeps an app that isn't installed first when nothing was before it`() {
        val away = LaunchableApp("com.example.away", "com.example.away.MainActivity")
        val arrangement = AppArrangement(order = null, hidden = listOf(away, id("news")))

        val result = arranged(arrangement).hide(app("games"))

        assertEquals(listOf(away, id("games"), id("news")), result.arrangement.hidden)
    }

    @Test
    fun `lists all apps by label with whether each is hidden`() {
        val arrangement = AppArrangement(order = listOf(id("news"), id("games")), hidden = listOf(id("music")))

        val list = arranged(arrangement).byLabel()

        assertEquals(
            listOf("Games" to false, "Movies" to false, "Music" to true, "News" to false),
            list.map { it.app.label to it.hidden },
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `can't hide an app that isn't shown`() {
        arranged().hide(app("music")).hide(app("music"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `can't show an app that isn't hidden`() {
        arranged().show(app("music"))
    }
}
