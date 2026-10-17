package com.luncher.domain.pictures

import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.VerticalAlignment
import org.junit.Assert.assertEquals
import org.junit.Test

class PictureUseTest {

    private val sunset = PictureId("sunset")
    private val wallpaper = Appearance(TileGeometry(verticalAlignment = VerticalAlignment.TOP), background = Background(sunset, Fit.STRETCH, dim = 40, blur = Blur.LIGHT))

    @Test
    fun `the wallpaper's picture is in use as the wallpaper`() {
        assertEquals(mapOf(sunset to listOf(PictureUse.Wallpaper)), pictureUses(wallpaper))
    }

    @Test
    fun `without a wallpaper nothing is in use`() {
        assertEquals(emptyMap<PictureId, List<PictureUse>>(), pictureUses(Appearance()))
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

    @Test
    fun `a picture received can be made the wallpaper`() {
        assertEquals(listOf(PictureUse.Wallpaper), pictureUseMenu)
    }
}
