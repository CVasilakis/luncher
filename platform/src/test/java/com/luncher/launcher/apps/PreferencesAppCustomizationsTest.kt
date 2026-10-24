package com.luncher.launcher.apps

import android.content.Context
import android.content.SharedPreferences
import com.luncher.domain.apps.AppCustomization
import com.luncher.domain.apps.LaunchableApp
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

    // The stored format, written out: once released, a TV keeps it across updates, so a change of
    // it has to read this one too. The other tests save and read back, which a change of both
    // sides would pass. (Not released yet: until then it may change without a migration.)
    private val stored = mapOf(
        "com.example.movies/com.example.movies.MainActivity" to """{"name":"Cinema"}""",
        "com.example.news/.NewsActivity" to """{"name":"Täglich \/ News\nΝέα ☀"}""",
    )

    @Test
    fun `reads the stored format`() {
        preferences.edit().apply { stored.forEach { (key, value) -> putString(key, value) } }.commit()

        assertEquals(
            mapOf(movies to AppCustomization(name = "Cinema"), news to AppCustomization(name = "Täglich / News\nΝέα ☀")),
            PreferencesAppCustomizations(context).read(),
        )
    }

    @Test
    fun `saves in the stored format`() {
        PreferencesAppCustomizations(context).save(
            mapOf(movies to AppCustomization(name = "Cinema"), news to AppCustomization(name = "Täglich / News\nΝέα ☀")),
        )

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
