package com.luncher.launcher.apps

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.luncher.domain.apps.LaunchableApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PackageManagerInstalledAppsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var packageManager: PackageManager
    private lateinit var installedApps: PackageManagerInstalledApps
    private val installedPackages = mutableSetOf<String>()

    @Before
    fun setUp() {
        packageManager = RuntimeEnvironment.getApplication().packageManager
        installedApps = PackageManagerInstalledApps(packageManager)
    }

    /** Installs an app whose activity handles MAIN + [category], as its manifest would declare. */
    private fun install(
        packageName: String,
        activity: String,
        category: String,
        label: String? = null,
        banner: Int = 0,
        applicationBanner: Int = 0,
        apk: File? = null,
    ): LaunchableApp {
        val shadow = shadowOf(packageManager)
        if (installedPackages.add(packageName)) {   // installing again would replace the package
            shadow.installPackage(
                PackageInfo().apply {
                    this.packageName = packageName
                    applicationInfo = ApplicationInfo().apply {
                        this.packageName = packageName
                        this.banner = applicationBanner
                        sourceDir = apk?.path
                    }
                },
            )
        }
        val component = ComponentName(packageName, activity)
        shadow.addOrUpdateActivity(
            ActivityInfo().apply {
                this.packageName = packageName
                name = activity
                nonLocalizedLabel = label
                this.banner = banner
            },
        )
        shadow.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(category) },
        )
        return LaunchableApp(packageName, activity)
    }

    private fun tvApps() = installedApps.tvApps().map { it.launchable }

    private fun installed(app: LaunchableApp) = installedApps.tvApps().single { it.launchable == app }

    @Test
    fun `lists apps with a TV launcher entry`() {
        val movies = install("com.example.movies", "com.example.movies.Main", Intent.CATEGORY_LEANBACK_LAUNCHER)
        val music = install("com.example.music", "com.example.music.Main", Intent.CATEGORY_LEANBACK_LAUNCHER)

        val apps = tvApps()

        assertTrue(apps.containsAll(listOf(movies, music)))
    }

    @Test
    fun `leaves out apps that only have a phone launcher entry`() {
        val phoneOnly = install("com.example.phone", "com.example.phone.Main", Intent.CATEGORY_LAUNCHER)

        assertFalse(phoneOnly in tvApps())
    }

    @Test
    fun `lists each TV launcher activity of a package separately`() {
        val movies = install("com.example.media", "com.example.media.Movies", Intent.CATEGORY_LEANBACK_LAUNCHER)
        val music = install("com.example.media", "com.example.media.Music", Intent.CATEGORY_LEANBACK_LAUNCHER)

        assertTrue(tvApps().containsAll(listOf(movies, music)))
    }

    @Test
    fun `reads each app's label`() {
        val movies = install("com.example.movies", "com.example.movies.Main", Intent.CATEGORY_LEANBACK_LAUNCHER, label = "Movies")

        assertEquals("Movies", installed(movies).label)
    }

    @Test
    fun `tells whether an app declares a banner, on its activity or its application`() {
        val onActivity = install("com.example.a", "com.example.a.Main", Intent.CATEGORY_LEANBACK_LAUNCHER, banner = BANNER)
        val onApplication =
            install("com.example.b", "com.example.b.Main", Intent.CATEGORY_LEANBACK_LAUNCHER, applicationBanner = BANNER)
        val without = install("com.example.c", "com.example.c.Main", Intent.CATEGORY_LEANBACK_LAUNCHER)

        assertTrue(installed(onActivity).hasBanner)
        assertTrue(installed(onApplication).hasBanner)
        assertFalse(installed(without).hasBanner)
    }

    @Test
    fun `tells when an app was installed or updated, from when its APK was written`() {
        val apk = folder.newFile("base.apk").apply { setLastModified(1_700_000_000_000) }
        val movies = install("com.example.movies", "com.example.movies.Main", Intent.CATEGORY_LEANBACK_LAUNCHER, apk = apk)
        val unreadable = install("com.example.music", "com.example.music.Main", Intent.CATEGORY_LEANBACK_LAUNCHER, apk = File(folder.root, "gone.apk"))

        assertEquals(1_700_000_000_000, installed(movies).updated)
        assertEquals(0, installed(unreadable).updated)
    }

    private companion object {
        /** A resource ID, as a banner declared in a manifest has; only whether it's 0 matters. */
        const val BANNER = 0x7f010001
    }
}
