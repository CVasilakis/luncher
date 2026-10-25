package com.luncher.domain.apps

import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.FakeInstalledApps.Companion.homeApp
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrangedAppsTest {

    private val installed = listOf(app("games"), app("movies"), app("music"), app("news"))

    private fun arranged(arrangement: AppArrangement = AppArrangement.NONE) =
        homeApps(installed, "com.luncher.launcher", arrangement, emptyMap(), Locale.ENGLISH)

    private fun labels(apps: List<HomeApp>) = apps.map { it.name }

    private fun id(name: String) = app(name).launchable

    @Test
    fun `hiding an app puts it first among the hidden apps`() {
        val result = arranged().hide(homeApp("music")).hide(homeApp("games"))

        assertEquals(listOf("Movies", "News"), labels(result.shown))
        assertEquals(listOf("Games", "Music"), labels(result.hidden))
    }

    @Test
    fun `hiding keeps the order of the shown apps`() {
        val order = listOf(id("news"), id("games"), id("music"), id("movies"))

        val result = arranged(AppArrangement(order, hidden = emptyList())).hide(homeApp("games"))

        assertEquals(listOf("News", "Music", "Movies"), labels(result.shown))
    }

    @Test
    fun `showing an app puts it in its place by label while there's no order`() {
        val result = arranged(AppArrangement(order = null, hidden = listOf(id("games")))).show(homeApp("games"))

        assertEquals(listOf("Games", "Movies", "Music", "News"), labels(result.shown))
        assertEquals(emptyList<HomeApp>(), result.hidden)
    }

    @Test
    fun `showing an app puts it last in the user's order`() {
        val arrangement = AppArrangement(order = listOf(id("news"), id("movies"), id("music")), hidden = listOf(id("games")))

        val result = arranged(arrangement).show(homeApp("games"))

        assertEquals(listOf("News", "Movies", "Music", "Games"), labels(result.shown))
    }

    @Test
    fun `hiding and showing doesn't fix the order`() {
        val result = arranged().hide(homeApp("music")).show(homeApp("music"))

        assertNull(result.arrangement.order)
        assertEquals(emptyList<LaunchableApp>(), result.arrangement.hidden)
    }

    @Test
    fun `stores the hidden apps in their order`() {
        val result = arranged().hide(homeApp("music")).hide(homeApp("games"))

        assertEquals(AppArrangement(order = null, hidden = listOf(id("games"), id("music"))), result.arrangement)
    }

    @Test
    fun `stores the shown apps in their order, including apps installed since it was stored`() {
        val arrangement = AppArrangement(order = listOf(id("news"), id("movies")), hidden = emptyList())

        val result = arranged(arrangement).hide(homeApp("movies"))

        assertEquals(listOf(id("news"), id("games"), id("music")), result.arrangement.order)
    }

    @Test
    fun `keeps apps that aren't installed after the app they followed`() {
        val away = LaunchableApp("com.example.away", "com.example.away.MainActivity")
        val arrangement = AppArrangement(order = listOf(id("news"), away, id("movies")), hidden = emptyList())

        val result = arranged(arrangement).hide(homeApp("games"))

        assertEquals(listOf(id("news"), away, id("movies"), id("music")), result.arrangement.order)
    }

    @Test
    fun `keeps an app that isn't installed after the nearest app still before it`() {
        val away = LaunchableApp("com.example.away", "com.example.away.MainActivity")
        val arrangement = AppArrangement(order = listOf(id("news"), id("movies"), away), hidden = emptyList())

        val result = arranged(arrangement).hide(homeApp("movies"))

        assertEquals(listOf(id("news"), away, id("games"), id("music")), result.arrangement.order)
    }

    @Test
    fun `keeps an app that isn't installed first when nothing was before it`() {
        val away = LaunchableApp("com.example.away", "com.example.away.MainActivity")
        val arrangement = AppArrangement(order = null, hidden = listOf(away, id("news")))

        val result = arranged(arrangement).hide(homeApp("games"))

        assertEquals(listOf(away, id("games"), id("news")), result.arrangement.hidden)
    }

    @Test
    fun `lists all apps by label with whether each is hidden`() {
        val arrangement = AppArrangement(order = listOf(id("news"), id("games")), hidden = listOf(id("music")))

        val list = arranged(arrangement).byLabel()

        assertEquals(
            listOf("Games" to false, "Movies" to false, "Music" to true, "News" to false),
            list.map { it.app.installed.label to it.hidden },
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `can't hide an app that isn't shown`() {
        arranged().hide(homeApp("music")).hide(homeApp("music"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `can't show an app that isn't hidden`() {
        arranged().show(homeApp("music"))
    }

    // What the user named an app: the home screen's order follows it, the Hide apps list the label.

    private fun named(app: String, name: String) = mapOf(id(app) to AppCustomization(name = name))

    @Test
    fun `showing an app puts it in its place by the name the user gave`() {
        val arrangement = AppArrangement(order = null, hidden = listOf(id("news")))
        val arranged = homeApps(installed, "com.luncher.launcher", arrangement, named("news", "Arts"), Locale.ENGLISH)

        val result = arranged.show(arranged.hidden.single())

        assertEquals(listOf("Arts", "Games", "Movies", "Music"), labels(result.shown))
    }

    @Test
    fun `lists all apps by the device's label, whatever the user named them`() {
        val arranged = homeApps(installed, "com.luncher.launcher", AppArrangement.NONE, named("news", "Arts"), Locale.ENGLISH)

        assertEquals(listOf("Arts", "Games", "Movies", "Music"), labels(arranged.shown))
        assertEquals(listOf("Games", "Movies", "Music", "News"), arranged.byLabel().map { it.app.installed.label })
    }

    // Renaming: what to store.

    @Test
    fun `renaming an app stores its name with what was stored for the others`() {
        val arranged = homeApps(installed, "com.luncher.launcher", AppArrangement.NONE, named("news", "Arts"), Locale.ENGLISH)

        val stored = arranged.renamed(arranged.shown.first { it.installed.label == "Games" }, "Play")

        assertEquals(named("news", "Arts") + named("games", "Play"), stored)
    }

    @Test
    fun `renaming an app again replaces its name`() {
        val arranged = homeApps(installed, "com.luncher.launcher", AppArrangement.NONE, named("news", "Arts"), Locale.ENGLISH)

        assertEquals(named("news", "Daily"), arranged.renamed(arranged.shown.first { it.name == "Arts" }, "Daily"))
    }

    @Test
    fun `an app named by its own label again leaves nothing stored for it`() {
        val arranged = homeApps(installed, "com.luncher.launcher", AppArrangement.NONE, named("news", "Arts") + named("games", "Play"), Locale.ENGLISH)

        assertEquals(named("games", "Play"), arranged.renamed(arranged.shown.first { it.name == "Arts" }, null))
    }

    @Test
    fun `renaming keeps the names of apps that aren't installed`() {
        val away = LaunchableApp("com.example.away", "com.example.away.MainActivity")
        val stored = mapOf(away to AppCustomization(name = "Away"))
        val arranged = homeApps(installed, "com.luncher.launcher", AppArrangement.NONE, stored, Locale.ENGLISH)

        assertEquals(stored + named("music", "Songs"), arranged.renamed(homeApp("music"), "Songs"))
    }

    // An update renamed the activity: its name is stored under the activity as it is now.
    @Test
    fun `renaming stores an app whose activity an update renamed under its new activity`() {
        val old = LaunchableApp("com.example.news", "com.example.news.OldActivity")
        val arranged = homeApps(installed, "com.luncher.launcher", AppArrangement.NONE, mapOf(old to AppCustomization(name = "Arts")), Locale.ENGLISH)

        assertEquals(named("news", "Daily"), arranged.renamed(arranged.shown.first { it.name == "Arts" }, "Daily"))
    }

    @Test
    fun `a hidden app can be renamed`() {
        val arranged = arranged(AppArrangement(order = null, hidden = listOf(id("news"))))

        assertEquals(named("news", "Arts"), arranged.renamed(homeApp("news"), "Arts"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `can't rename an app that isn't installed`() {
        arranged().renamed(homeApp("away"), "Away")
    }

    // A tile's image: what to store.

    private val beach = PictureId("beach")
    private val alps = PictureId("alps")

    private fun showing(name: String, picture: PictureId, given: String? = null) =
        id(name) to AppCustomization(name = given, image = TileImage(picture))

    @Test
    fun `an image chosen for a tile is stored with what's set for it and for the others`() {
        val arranged = homeApps(installed, "com.luncher.launcher", AppArrangement.NONE, named("news", "Arts") + named("games", "Play"), Locale.ENGLISH)
        val image = TileImage(beach, Framing(Fit.STRETCH))

        val stored = arranged.withImage(arranged.shown.first { it.name == "Arts" }, image)

        assertEquals(mapOf(id("news") to AppCustomization(name = "Arts", image = image)) + named("games", "Play"), stored)
    }

    @Test
    fun `the app's own banner again, or an image without a picture, stores none, and its name stays`() {
        val stored = mapOf(showing("news", beach, given = "Arts"), showing("games", alps))
        val arranged = homeApps(installed, "com.luncher.launcher", AppArrangement.NONE, stored, Locale.ENGLISH)

        assertEquals(named("news", "Arts") + showing("games", alps), arranged.withImage(arranged.shown.first { it.name == "Arts" }, null))
        assertEquals(mapOf(showing("news", beach, given = "Arts")), arranged.withImage(homeApp("games"), TileImage(null, Framing(Fit.STRETCH))))
    }

    @Test
    fun `a hidden app's tile can have an image, and an app not installed can't`() {
        val arranged = arranged(AppArrangement(order = null, hidden = listOf(id("news"))))

        assertEquals(mapOf(showing("news", beach)), arranged.withImage(homeApp("news"), TileImage(beach)))
        assertThrows { arranged.withImage(homeApp("away"), TileImage(beach)) }
    }

    @Test
    fun `deleting pictures puts every tile that shows one back to its own banner, hidden and absent apps too`() {
        val away = LaunchableApp("com.example.away", "com.example.away.MainActivity")
        val stored = mapOf(
            showing("news", beach, given = "Arts"),
            showing("games", beach),
            showing("music", alps),
            away to AppCustomization(image = TileImage(beach)),
        ) + named("movies", "Films")
        val arranged = homeApps(installed, "com.luncher.launcher", AppArrangement(order = null, hidden = listOf(id("games"))), stored, Locale.ENGLISH)

        assertEquals(
            named("news", "Arts") + showing("music", alps) + named("movies", "Films"),
            arranged.withoutPictures(setOf(beach, PictureId("other"))),
        )
    }

    @Test
    fun `deleting pictures no tile shows stores nothing`() {
        val arranged = homeApps(installed, "com.luncher.launcher", AppArrangement.NONE, mapOf(showing("music", alps)) + named("news", "Arts"), Locale.ENGLISH)

        assertNull(arranged.withoutPictures(setOf(beach)))
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            return
        }
        throw AssertionError("not refused")
    }
}
