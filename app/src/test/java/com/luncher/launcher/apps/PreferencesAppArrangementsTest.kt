package com.luncher.launcher.apps

import android.content.Context
import android.content.SharedPreferences
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.LaunchableApp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PreferencesAppArrangementsTest {

    private val preferences: SharedPreferences =
        RuntimeEnvironment.getApplication().getSharedPreferences("arrangement", Context.MODE_PRIVATE)

    private val movies = LaunchableApp("com.example.movies", "com.example.movies.MainActivity")
    private val news = LaunchableApp("com.example.news", ".NewsActivity")
    private val music = LaunchableApp("com.example.music", "com.example.music.tv.Main")

    @Test
    fun `has no arrangement before one is saved`() {
        assertEquals(AppArrangement.NONE, PreferencesAppArrangements(preferences).read())
    }

    @Test
    fun `reads back what was saved, in order, after the process restarts`() {
        val arrangement = AppArrangement(order = listOf(news, movies), hidden = listOf(music))

        PreferencesAppArrangements(preferences).save(arrangement)

        assertEquals(arrangement, PreferencesAppArrangements(preferences).read())
    }

    @Test
    fun `tells no order from an empty one`() {
        val arrangements = PreferencesAppArrangements(preferences)

        arrangements.save(AppArrangement(order = emptyList(), hidden = listOf(music)))
        assertEquals(emptyList<LaunchableApp>(), PreferencesAppArrangements(preferences).read().order)

        arrangements.save(AppArrangement(order = null, hidden = listOf(music)))
        assertEquals(AppArrangement(order = null, hidden = listOf(music)), PreferencesAppArrangements(preferences).read())
    }

    @Test
    fun `reads the storage once`() {
        val arrangements = PreferencesAppArrangements(preferences)
        arrangements.read()

        preferences.edit().putString("hidden", "${news.packageName}/${news.activityName}").commit()

        assertEquals(AppArrangement.NONE, arrangements.read())
    }

    @Test
    fun `a save is read back at once`() {
        val arrangements = PreferencesAppArrangements(preferences)
        arrangements.read()
        val arrangement = AppArrangement(order = null, hidden = listOf(news))

        arrangements.save(arrangement)

        assertEquals(arrangement, arrangements.read())
    }

    @Test
    fun `skips stored lines that aren't apps`() {
        preferences.edit().putString("hidden", "com.example.news/.NewsActivity\n\nno-slash\n/x\ny/").commit()

        assertEquals(listOf(news), PreferencesAppArrangements(preferences).read().hidden)
    }
}
