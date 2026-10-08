package com.luncher.domain.apps

import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAppsTest {

    private val launcher = "com.luncher.launcher"

    private fun labels(apps: List<InstalledApp>) = apps.map { it.label }

    private fun shown(
        installed: List<InstalledApp>,
        arrangement: AppArrangement = AppArrangement.NONE,
        locale: Locale = Locale.ENGLISH,
    ) = labels(homeApps(installed, launcher, arrangement, locale).shown)

    /** An app for each of [labels], in that order. */
    private fun labeled(vararg labels: String) = labels.mapIndexed { i, label -> app("app$i").copy(label = label) }

    private fun hidden(installed: List<InstalledApp>, arrangement: AppArrangement) =
        labels(homeApps(installed, launcher, arrangement, Locale.ENGLISH).hidden)

    private fun id(name: String) = app(name).launchable

    /** [name]'s app, as an update that renamed its launcher activity installs it. */
    private fun renamed(name: String) =
        app(name).copy(launchable = LaunchableApp("com.example.$name", "com.example.$name.ui.TvActivity"))

    @Test
    fun `sorts apps by label`() {
        assertEquals(listOf("Games", "Movies", "News"), shown(listOf(app("news"), app("movies"), app("games"))))
    }

    @Test
    fun `sorts regardless of upper and lower case`() {
        val installed = listOf(app("b").copy(label = "b"), app("a").copy(label = "A"), app("c").copy(label = "C"))

        assertEquals(listOf("A", "b", "C"), shown(installed))
    }

    @Test
    fun `sorts by what the label shows, skipping invisible formatting characters`() {
        // As Google TV's Settings app reports its label: wrapped in text direction marks. And in
        // isolates, which the JDK's collation, unlike Android's, would sort after every letter.
        val settings = app("settings").copy(label = "\u200e\u200f\u200e\u200eSettings\u200e\u200f")
        val news = app("news").copy(label = "\u2068News\u2069")
        val installed = listOf(app("youtube").copy(label = "YouTube"), settings, news, app("play").copy(label = "Play Store"))

        assertEquals(listOf(news.label, "Play Store", settings.label, "YouTube"), shown(installed))
    }

    @Test
    fun `sorts an accented letter with its letter`() {
        assertEquals(listOf("Eagle", "Éclair", "Zoom"), shown(labeled("Zoom", "Éclair", "Eagle")))
    }

    // By character code, Greek's accented vowels come before or after every letter.
    @Test
    fun `sorts as the language sorts words`() {
        val greek = Locale.forLanguageTag("el")

        assertEquals(
            listOf("Αθήνα", "Άρης", "Βήτα", "Ήλιος", "Ωμέγα", "Ώρα"),
            shown(labeled("Ώρα", "Ωμέγα", "Ήλιος", "Βήτα", "Άρης", "Αθήνα"), locale = greek),
        )
    }

    @Test
    fun `a label that's a prefix of another sorts first`() {
        val installed = listOf(app("b").copy(label = "YouTube Music"), app("a").copy(label = "YouTube"))

        assertEquals(listOf("YouTube", "YouTube Music"), shown(installed))
    }

    @Test
    fun `leaves out the launcher itself`() {
        val self = InstalledApp(LaunchableApp(launcher, "$launcher.home.HomeActivity"), "Luncher", hasBanner = true)

        assertEquals(listOf("Movies"), shown(listOf(self, app("movies"))))
    }

    @Test
    fun `keeps a fixed order for apps with the same label`() {
        val first = app("a").copy(label = "Same")
        val second = app("b").copy(label = "Same")

        assertEquals(
            homeApps(listOf(first, second), launcher, AppArrangement.NONE, Locale.ENGLISH).shown,
            homeApps(listOf(second, first), launcher, AppArrangement.NONE, Locale.ENGLISH).shown,
        )
    }

    @Test
    fun `shows nothing when nothing is installed`() {
        assertEquals(emptyList<String>(), shown(emptyList()))
    }

    @Test
    fun `leaves out hidden apps, and lists them in their stored order`() {
        val installed = listOf(app("games"), app("movies"), app("music"), app("news"))
        val arrangement = AppArrangement(order = null, hidden = listOf(id("news"), id("games")))

        assertEquals(listOf("Movies", "Music"), shown(installed, arrangement))
        assertEquals(listOf("News", "Games"), hidden(installed, arrangement))
    }

    @Test
    fun `shows apps in the stored order`() {
        val installed = listOf(app("games"), app("movies"), app("news"))
        val arrangement = AppArrangement(order = listOf(id("news"), id("games"), id("movies")), hidden = emptyList())

        assertEquals(listOf("News", "Games", "Movies"), shown(installed, arrangement))
    }

    @Test
    fun `shows apps the stored order doesn't name after it, by label`() {
        val installed = listOf(app("zoo"), app("music"), app("news"), app("art"))
        val arrangement = AppArrangement(order = listOf(id("news"), id("music")), hidden = emptyList())

        assertEquals(listOf("News", "Music", "Art", "Zoo"), shown(installed, arrangement))
    }

    @Test
    fun `an app both hidden and in the order is hidden`() {
        val installed = listOf(app("movies"), app("news"))
        val arrangement = AppArrangement(order = listOf(id("news"), id("movies")), hidden = listOf(id("news")))

        assertEquals(listOf("Movies"), shown(installed, arrangement))
        assertEquals(listOf("News"), hidden(installed, arrangement))
    }

    @Test
    fun `recognizes an app whose update renamed its activity`() {
        val installed = listOf(app("movies"), renamed("news"))
        val arrangement = AppArrangement(order = null, hidden = listOf(id("news")))

        val arranged = homeApps(installed, launcher, arrangement, Locale.ENGLISH)

        assertEquals(listOf("News"), labels(arranged.hidden))
        assertEquals(listOf(renamed("news").launchable), arranged.arrangement.hidden)
    }

    @Test
    fun `recognizes a renamed activity in the order too`() {
        val installed = listOf(app("movies"), renamed("news"))
        val arrangement = AppArrangement(order = listOf(id("news"), id("movies")), hidden = emptyList())

        assertEquals(listOf("News", "Movies"), shown(installed, arrangement))
    }

    @Test
    fun `doesn't guess when the package has several apps the arrangement doesn't name`() {
        val second = app("news").copy(launchable = LaunchableApp("com.example.news", "com.example.news.Kids"))
        val installed = listOf(renamed("news"), second)
        val arrangement = AppArrangement(order = null, hidden = listOf(id("news")))

        val arranged = homeApps(installed, launcher, arrangement, Locale.ENGLISH)

        assertEquals(emptyList<InstalledApp>(), arranged.hidden)
        assertEquals(emptyList<LaunchableApp>(), arranged.arrangement.hidden)
    }

    @Test
    fun `drops an entry whose package is installed but has no such app any more`() {
        // The package's one TV app is named in the arrangement already, so it isn't a rename.
        val installed = listOf(app("news"))
        val gone = LaunchableApp("com.example.news", "com.example.news.OldActivity")
        val arrangement = AppArrangement(order = listOf(gone, id("news")), hidden = emptyList())

        assertEquals(listOf(id("news")), homeApps(installed, launcher, arrangement, Locale.ENGLISH).arrangement.order)
    }

    @Test
    fun `keeps the entries of apps that aren't installed`() {
        val arrangement = AppArrangement(order = listOf(id("games"), id("news")), hidden = listOf(id("music")))

        val arranged = homeApps(listOf(app("news")), launcher, arrangement, Locale.ENGLISH)

        assertEquals(listOf("News"), labels(arranged.shown))
        assertEquals(emptyList<InstalledApp>(), arranged.hidden)
        assertEquals(arrangement, arranged.arrangement)
    }

    @Test
    fun `an app that comes back gets its place back`() {
        val arrangement = AppArrangement(order = listOf(id("news"), id("games"), id("movies")), hidden = emptyList())
        val withoutGames = homeApps(listOf(app("movies"), app("news")), launcher, arrangement, Locale.ENGLISH).arrangement

        assertEquals(listOf("News", "Games", "Movies"), shown(listOf(app("games"), app("movies"), app("news")), withoutGames))
    }

    @Test
    fun `a hidden app that comes back is still hidden`() {
        val arrangement = AppArrangement(order = null, hidden = listOf(id("games")))
        val withoutGames = homeApps(listOf(app("movies")), launcher, arrangement, Locale.ENGLISH).arrangement

        assertEquals(listOf("Games"), hidden(listOf(app("games"), app("movies")), withoutGames))
    }
}
