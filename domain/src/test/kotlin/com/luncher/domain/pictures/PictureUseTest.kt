package com.luncher.domain.pictures

import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.VerticalAlignment
import com.luncher.domain.apps.AppCustomization
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.FakeInstalledApps.Companion.homeApp
import com.luncher.domain.apps.HomeApp
import com.luncher.domain.apps.TileImage
import org.junit.Assert.assertEquals
import org.junit.Test

class PictureUseTest {

    private val sunset = PictureId("sunset")
    private val beach = PictureId("beach")
    private val wallpaper = Appearance(TileGeometry(verticalAlignment = VerticalAlignment.TOP), background = Background(sunset, Framing(Fit.STRETCH), dim = 40, blur = Blur.LIGHT))

    private fun showing(name: String, picture: PictureId) = HomeApp(app(name), AppCustomization(image = TileImage(picture)))

    @Test
    fun `the wallpaper's picture is in use as the wallpaper`() {
        assertEquals(mapOf(sunset to listOf(PictureUse.Wallpaper)), pictureUses(wallpaper, emptyList()))
    }

    @Test
    fun `without a wallpaper nothing is in use`() {
        assertEquals(emptyMap<PictureId, List<PictureUse>>(), pictureUses(Appearance(), listOf(homeApp("movies"))))
    }

    @Test
    fun `a picture on tiles is in use on each, as the wallpaper too`() {
        val movies = showing("movies", sunset)
        val music = showing("music", beach)
        val news = showing("news", sunset)

        assertEquals(
            mapOf(
                sunset to listOf(PictureUse.Wallpaper, PictureUse.Tile(movies.launchable), PictureUse.Tile(news.launchable)),
                beach to listOf(PictureUse.Tile(music.launchable)),
            ),
            pictureUses(wallpaper, listOf(movies, homeApp("games"), music, news)),
        )
    }

    @Test
    fun `deleting the wallpaper's picture leaves no wallpaper, and the rest as it was`() {
        val after = withoutPictures(wallpaper, setOf(sunset, PictureId("other")))

        assertEquals(null, after.background.picture)
        assertEquals(wallpaper.copy(background = wallpaper.background.copy(picture = null)), after)
    }

    @Test
    fun `deleting other pictures changes nothing`() {
        assertEquals(wallpaper, withoutPictures(wallpaper, setOf(PictureId("other"))))
        assertEquals(Appearance(), withoutPictures(Appearance(), setOf(sunset)))
    }
}
