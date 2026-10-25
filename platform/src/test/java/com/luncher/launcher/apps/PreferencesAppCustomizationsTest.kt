package com.luncher.launcher.apps

import android.content.Context
import android.content.SharedPreferences
import com.luncher.domain.apps.AppCustomization
import com.luncher.domain.apps.LaunchableApp
import com.luncher.domain.apps.TileImage
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PicturePosition
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PreferencesAppCustomizationsTest {

    private val context = RuntimeEnvironment.getApplication()

    // The file the adapter keeps what the user set in, named here as the stored format below is.
    private val preferences: SharedPreferences = context.getSharedPreferences("app_customizations", Context.MODE_PRIVATE)

    private val movies = LaunchableApp("com.example.movies", "com.example.movies.MainActivity")
    private val news = LaunchableApp("com.example.news", ".NewsActivity")
    private val music = LaunchableApp("com.example.music", ".Music")

    private val beach = TileImage(PictureId("3f9a"), Framing(Fit.FILL, PicturePosition.LEFT))

    // The stored format, written out: once released, a TV keeps it across updates, so a change of
    // it has to read this one too. The other tests save and read back, which a change of both
    // sides would pass. (Not released yet: until then it may change without a migration.)
    private val stored = mapOf(
        "com.example.movies/com.example.movies.MainActivity" to """{"name":"Cinema"}""",
        "com.example.news/.NewsActivity" to """{"name":"Täglich \/ News\nΝέα ☀"}""",
        "com.example.music/.Music" to """{"name":"Songs","image":{"picture":"3f9a","fit":"FILL","position":"LEFT"}}""",
        "com.example.movies/.Trailers" to """{"image":{"picture":"77c0","fit":"STRETCH","position":"CENTER"}}""",
    )

    /** What [stored] holds. */
    private val customizations = mapOf(
        movies to AppCustomization(name = "Cinema"),
        news to AppCustomization(name = "Täglich / News\nΝέα ☀"),
        music to AppCustomization(name = "Songs", image = beach),
        LaunchableApp("com.example.movies", ".Trailers") to AppCustomization(image = TileImage(PictureId("77c0"), Framing(Fit.STRETCH))),
    )

    @Test
    fun `reads the stored format`() {
        preferences.edit().apply { stored.forEach { (key, value) -> putString(key, value) } }.commit()

        assertEquals(customizations, PreferencesAppCustomizations(context).read())
    }

    @Test
    fun `saves in the stored format`() {
        PreferencesAppCustomizations(context).save(customizations)

        assertEquals(stored, preferences.all)
    }

    @Test
    fun `has nothing before something is saved`() {
        assertEquals(emptyMap<LaunchableApp, AppCustomization>(), PreferencesAppCustomizations(context).read())
    }

    @Test
    fun `reads back what was saved after the process restarts, any character in a name`() {
        val customizations = mapOf(
            movies to AppCustomization(name = "Line\nbreak, slash / and \"quotes\" \\ {}"),
            news to AppCustomization(name = "أخبار ⁦News⁩ 📺"),
        )

        PreferencesAppCustomizations(context).save(customizations)

        assertEquals(customizations, PreferencesAppCustomizations(context).read())
    }

    @Test
    fun `a save replaces everything stored before`() {
        val customizations = PreferencesAppCustomizations(context)
        customizations.save(mapOf(movies to AppCustomization(name = "Cinema"), news to AppCustomization(name = "Headlines")))

        customizations.save(mapOf(news to AppCustomization(name = "Daily")))

        assertEquals(mapOf(news to AppCustomization(name = "Daily")), customizations.read())
        assertEquals(mapOf(news to AppCustomization(name = "Daily")), PreferencesAppCustomizations(context).read())
        assertEquals(setOf("com.example.news/.NewsActivity"), preferences.all.keys)
    }

    @Test
    fun `keeps nothing for an app with nothing set`() {
        PreferencesAppCustomizations(context).save(mapOf(movies to AppCustomization.NONE, news to AppCustomization(name = "Daily")))

        assertEquals(setOf("com.example.news/.NewsActivity"), preferences.all.keys)
    }

    @Test
    fun `skips what it can't read, and a value it doesn't know`() {
        preferences.edit()
            .putString("com.example.movies/com.example.movies.MainActivity", """{"name":"Cinema"}""")
            .putString("not an app", """{"name":"Nobody"}""")
            .putString("com.example.broken/.Main", "{not json")
            .putString("com.example.number/.Main", """{"name":42}""")
            .putString("com.example.newer/.Main", """{"picture":"abc"}""")
            .putInt("com.example.int/.Main", 3)
            .commit()

        assertEquals(mapOf(movies to AppCustomization(name = "Cinema")), PreferencesAppCustomizations(context).read())
    }

    @Test
    fun `an image without a picture it can read is none, and a fit or position it doesn't know the default`() {
        preferences.edit()
            .putString("com.example.movies/com.example.movies.MainActivity", """{"name":"Cinema","image":{"fit":"FILL"}}""")
            .putString("com.example.news/.NewsActivity", """{"name":"Daily","image":"beach"}""")
            .putString("com.example.music/.Music", """{"image":{"picture":"3f9a","fit":"TILE","position":"MIDDLE"}}""")
            .commit()

        assertEquals(
            mapOf(
                movies to AppCustomization(name = "Cinema"),
                news to AppCustomization(name = "Daily"),
                music to AppCustomization(image = TileImage(PictureId("3f9a"))),
            ),
            PreferencesAppCustomizations(context).read(),
        )
    }

    @Test
    fun `an image without a picture isn't stored`() {
        PreferencesAppCustomizations(context).save(mapOf(movies to AppCustomization(name = "Cinema", image = TileImage(null))))

        assertEquals(mapOf("com.example.movies/com.example.movies.MainActivity" to """{"name":"Cinema"}"""), preferences.all)
    }

    @Test
    fun `reads the storage once`() {
        val customizations = PreferencesAppCustomizations(context)
        customizations.read()
        preferences.edit().putString("com.example.movies/com.example.movies.MainActivity", """{"name":"Cinema"}""").commit()

        assertEquals(emptyMap<LaunchableApp, AppCustomization>(), customizations.read())
    }

    @Test
    fun `tells its listeners after each save, and no longer once removed`() {
        val customizations = PreferencesAppCustomizations(context)
        var told = 0
        val listener: () -> Unit = { told++ }
        customizations.addListener(listener)

        customizations.save(mapOf(movies to AppCustomization(name = "Cinema")))
        customizations.removeListener(listener)
        customizations.save(emptyMap())

        assertEquals(1, told)
    }
}
