package com.luncher.launcher.home

import com.luncher.domain.apps.Banner
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.apps.LaunchableApp
import com.luncher.launcher.R
import com.luncher.launcher.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // to read pixels back
@Config(qualifiers = TV_1080P)
class BannerImagesTest {

    private val context = RuntimeEnvironment.getApplication()
    private val banners = BannerImages(context)

    @Suppress("DEPRECATION")
    private val cardColor = context.resources.getColor(R.color.home_tile_card)

    /** Luncher itself is the one app Robolectric can load a banner of; it declares one in its manifest. */
    private val luncher = InstalledApp(
        LaunchableApp(context.packageName, HomeActivity::class.java.name),
        label = "Luncher",
        hasBanner = true,
    )

    @Test
    fun `draws at the tile's size`() {
        val image = banners.render(luncher, Banner.AppBanner, 300, 169)

        assertEquals(300, image.width)
        assertEquals(169, image.height)
    }

    @Test
    fun `draws the app's banner`() {
        val image = banners.render(luncher, Banner.AppBanner, 300, 169)

        assertNotEquals(cardColor, image.getPixel(1, 1))
    }

    @Test
    fun `draws a card with the icon when the domain chose the icon`() {
        val image = banners.render(luncher, Banner.AppIcon, 300, 169)

        assertEquals(cardColor, image.getPixel(1, 1))
    }

    @Test
    fun `draws a card instead of failing when the app is gone`() {
        val image = banners.render(app("uninstalled", hasBanner = true), Banner.AppBanner, 300, 169)

        assertEquals(cardColor, image.getPixel(1, 1))
    }
}
