package com.luncher.domain.apps

import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.FakeInstalledApps.Companion.homeApp
import com.luncher.domain.layout.TileSize
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PicturePosition
import org.junit.Assert.assertEquals
import org.junit.Test

class BannerTest {

    private val beach = PictureId("beach")

    @Test
    fun `an app with a banner shows its banner`() {
        assertEquals(Banner.AppBanner, bannerFor(homeApp("movies", hasBanner = true)))
    }

    @Test
    fun `an app without a banner shows its icon`() {
        assertEquals(Banner.AppIcon, bannerFor(homeApp("movies", hasBanner = false)))
    }

    @Test
    fun `an app with a picture of its own shows it, framed as set, its banner where it can't be drawn`() {
        val framing = Framing(Fit.FILL, PicturePosition.LEFT)
        val app = HomeApp(app("movies", hasBanner = true), AppCustomization(image = TileImage(beach, framing)))

        assertEquals(Banner.Picture(beach, framing, TileLook(), Banner.AppBanner), bannerFor(app))
    }

    @Test
    fun `an app without a banner falls back to its icon from its picture`() {
        val app = HomeApp(app("movies", hasBanner = false), AppCustomization(image = TileImage(beach, Framing(Fit.STRETCH))))

        assertEquals(Banner.Picture(beach, Framing(Fit.STRETCH), TileLook(), Banner.AppIcon), bannerFor(app))
    }

    @Test
    fun `a picture's tile shows it with the tile's look, and is prepared from its picture, framing and focus at a size`() {
        val look = TileLook(TileBackdrop.FROSTED, TileFocusShape.SHAPE)
        val app = HomeApp(app("movies", hasBanner = true), AppCustomization(image = TileImage(beach, Framing(Fit.STRETCH), look)))

        val banner = bannerFor(app) as Banner.Picture

        assertEquals(look, banner.look)
        assertEquals(TileDrawing(beach, Framing(Fit.STRETCH), TileFocusShape.SHAPE, TileSize(345, 194)), banner.drawing(TileSize(345, 194)))
    }

    @Test
    fun `an image without a picture is the app's own banner`() {
        val app = HomeApp(app("movies", hasBanner = true), AppCustomization(image = TileImage(null)))

        assertEquals(Banner.AppBanner, bannerFor(app))
    }

    @Test
    fun `a name of the user's own changes nothing of the image`() {
        val app = HomeApp(app("movies", hasBanner = true), AppCustomization(name = "Films"))

        assertEquals(Banner.AppBanner, bannerFor(app))
        assertEquals(Banner.AppBanner, ownBanner(HomeApp(app.installed, AppCustomization(name = "Films", image = TileImage(beach)))))
    }
}
