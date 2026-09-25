package com.luncher.launcher.apps

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.luncher.domain.apps.LaunchableApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PackageManagerInstalledAppsTest {

    private lateinit var packageManager: PackageManager
    private lateinit var installedApps: PackageManagerInstalledApps
    private val installedPackages = mutableSetOf<String>()

    @Before
    fun setUp() {
        packageManager = RuntimeEnvironment.getApplication().packageManager
        installedApps = PackageManagerInstalledApps(packageManager)
    }

    /** Installs an app whose activity handles MAIN + [category], as its manifest would declare. */
    private fun install(packageName: String, activity: String, category: String): LaunchableApp {
        val shadow = shadowOf(packageManager)
        if (installedPackages.add(packageName)) {   // installing again would replace the package
            shadow.installPackage(PackageInfo().apply { this.packageName = packageName })
        }
        val component = ComponentName(packageName, activity)
        shadow.addActivityIfNotPresent(component)
        shadow.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(category) },
        )
        return LaunchableApp(packageName, activity)
    }

    @Test
    fun `lists apps with a TV launcher entry`() {
        val movies = install("com.example.movies", "com.example.movies.Main", Intent.CATEGORY_LEANBACK_LAUNCHER)
        val music = install("com.example.music", "com.example.music.Main", Intent.CATEGORY_LEANBACK_LAUNCHER)

        val apps = installedApps.tvApps()

        assertTrue(apps.containsAll(listOf(movies, music)))
    }

    @Test
    fun `leaves out apps that only have a phone launcher entry`() {
        val phoneOnly = install("com.example.phone", "com.example.phone.Main", Intent.CATEGORY_LAUNCHER)

        assertFalse(phoneOnly in installedApps.tvApps())
    }

    @Test
    fun `lists each TV launcher activity of a package separately`() {
        val movies = install("com.example.media", "com.example.media.Movies", Intent.CATEGORY_LEANBACK_LAUNCHER)
        val music = install("com.example.media", "com.example.media.Music", Intent.CATEGORY_LEANBACK_LAUNCHER)

        assertTrue(installedApps.tvApps().containsAll(listOf(movies, music)))
    }
}
