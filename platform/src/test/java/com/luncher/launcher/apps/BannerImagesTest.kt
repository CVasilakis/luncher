package com.luncher.launcher.apps

import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Color
import com.luncher.domain.apps.Banner
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.apps.LaunchableApp
import com.luncher.launcher.platform.R
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // to read pixels back
@Config(qualifiers = TV_1080P)
class BannerImagesTest {

    private val context = RuntimeEnvironment.getApplication()
    private val banners = BannerImages(context)

    @Suppress("DEPRECATION")
    private val cardColor = context.resources.getColor(R.color.banner_card)

    /** An installed app whose banner is plain white, a resource of the framework's that any app can load. */
    private val movies = run {
        val app = LaunchableApp("com.example.movies", "com.example.movies.Main")
        val application = ApplicationInfo().apply { packageName = app.packageName }
        val shadow = shadowOf(context.packageManager)
        shadow.installPackage(PackageInfo().apply { packageName = app.packageName; applicationInfo = application })
        shadow.addOrUpdateActivity(
            ActivityInfo().apply {
                packageName = app.packageName
                name = app.activityName
                applicationInfo = application
                banner = android.R.color.white
            },
        )
        InstalledApp(app, label = "Movies", hasBanner = true)
    }

    @Test
    fun `draws at the tile's size`() {
        val image = banners.render(movies, Banner.AppBanner, 300, 169)

        assertEquals(300, image.width)
        assertEquals(169, image.height)
    }

    @Test
    fun `draws the app's banner`() {
        val image = banners.render(movies, Banner.AppBanner, 300, 169)

        assertEquals(Color.WHITE, image.getPixel(1, 1))
    }

    @Test
    fun `draws a card with the icon when the domain chose the icon`() {
        val image = banners.render(movies, Banner.AppIcon, 300, 169)

        assertEquals(cardColor, image.getPixel(1, 1))
    }

    @Test
    fun `draws a card instead of failing when the app is gone`() {
        val image = banners.render(app("uninstalled", hasBanner = true), Banner.AppBanner, 300, 169)

        assertEquals(cardColor, image.getPixel(1, 1))
    }
}
