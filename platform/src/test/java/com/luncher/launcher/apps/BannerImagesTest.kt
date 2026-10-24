package com.luncher.launcher.apps

import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import com.luncher.domain.apps.Banner
import com.luncher.domain.apps.AppCustomization
import com.luncher.domain.apps.FakeInstalledApps.Companion.homeApp
import com.luncher.domain.apps.HomeApp
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.apps.LaunchableApp
import com.luncher.launcher.platform.R
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowPackageManager

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // to read pixels back
@Config(qualifiers = TV_1080P)
class BannerImagesTest {

    private val context = RuntimeEnvironment.getApplication()
    private val banners = BannerImages(context)

    @Suppress("DEPRECATION")
    private val cardColor = context.resources.getColor(R.color.banner_card)

    /** An installed app whose banner is plain white, a resource of the framework's that any app can load. */
    private val movies = install("movies", activityBanner = android.R.color.white)

    /**
     * An installed app with the banners given, the framework's resources that any app can load:
     * [activityBanner] on its launcher activity, [applicationBanner] on its application.
     */
    private fun install(appName: String, activityBanner: Int = 0, applicationBanner: Int = 0): HomeApp {
        val app = LaunchableApp("com.example.$appName", "com.example.$appName.Main")
        val application = ApplicationInfo().apply { packageName = app.packageName; banner = applicationBanner }
        val shadow = shadowOf(context.packageManager)
        shadow.installPackage(PackageInfo().apply { packageName = app.packageName; applicationInfo = application })
        // Its resources for the test's screen, as Android gives them; Robolectric's own are for an mdpi one.
        ShadowPackageManager.resources[app.packageName] = context.resources
        shadow.addOrUpdateActivity(
            ActivityInfo().apply {
                packageName = app.packageName
                name = app.activityName
                applicationInfo = application
                banner = activityBanner
            },
        )
        return HomeApp(InstalledApp(app, label = appName, hasBanner = true, updated = 0))
    }

    /** [drawable] drawn [width] x [height] px, as Android's own `Drawable` draws it. */
    private fun drawn(drawable: Drawable, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        return bitmap
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
    fun `draws the name the user gave on the card`() {
        val named = banners.render(movies.copy(customization = AppCustomization(name = "Cinema")), Banner.AppIcon, 300, 169)

        assertFalse(named.sameAs(banners.render(movies, Banner.AppIcon, 300, 169)))
        assertTrue(named.sameAs(banners.render(HomeApp(movies.installed.copy(label = "Cinema")), Banner.AppIcon, 300, 169)))
    }

    @Test
    fun `draws a card instead of failing when the app is gone`() {
        val image = banners.render(homeApp("uninstalled", hasBanner = true), Banner.AppBanner, 300, 169)

        assertEquals(cardColor, image.getPixel(1, 1))
    }

    @Test
    fun `draws the application's banner when the activity has none`() {
        val image = banners.render(install("music", applicationBanner = android.R.color.white), Banner.AppBanner, 300, 169)

        assertEquals(Color.WHITE, image.getPixel(1, 1))
    }

    // 720p at tvdpi: the framework has the image for hdpi and others, not tvdpi. Android's own
    // Drawable would scale it to tvdpi first, and the tile's size from there.
    @Test
    @Config(qualifiers = "+tvdpi")
    fun `scales a banner file's own pixels to the tile's size, once`() {
        val image = banners.render(install("photos", activityBanner = android.R.drawable.btn_star_big_on), Banner.AppBanner, 300, 169)

        val file = BitmapFactory.decodeResource(context.resources, android.R.drawable.btn_star_big_on, BitmapFactory.Options().apply { inScaled = false })
        val expected = Bitmap.createBitmap(300, 169, Bitmap.Config.ARGB_8888)
        Canvas(expected).drawBitmap(file, null, Rect(0, 0, 300, 169), Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
        assertTrue(image.sameAs(expected))
    }

    @Test
    fun `keeps a banner file's transparency`() {
        val image = banners.render(install("photos", activityBanner = android.R.drawable.btn_star_big_on), Banner.AppBanner, 300, 169)

        assertEquals(Color.TRANSPARENT, image.getPixel(0, 0))
    }

    @Test
    fun `draws a nine-patch banner as Android does, its edges not stretched`() {
        val image = banners.render(install("games", activityBanner = android.R.drawable.picture_frame), Banner.AppBanner, 300, 169)

        @Suppress("DEPRECATION")
        val expected = drawn(context.resources.getDrawable(android.R.drawable.picture_frame), 300, 169)
        assertTrue(image.sameAs(expected))
    }
}
