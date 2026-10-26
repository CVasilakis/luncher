package com.luncher.domain.apps

import com.luncher.domain.apps.TileImageOption.Backdrop
import com.luncher.domain.apps.TileImageOption.Focus
import com.luncher.domain.apps.TileImageOption.TileFit
import com.luncher.domain.apps.TileImageOption.TilePosition
import com.luncher.domain.options.step
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureCut
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.choosing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileImageOptionTest {

    private val beach = PictureId("beach")
    private val alps = PictureId("alps")

    // A tile is 16:9: a 4:1 panorama is cut at its sides, a 3:4 portrait and a 4:3 photo at their
    // top and bottom, a 1920 x 1080 picture not at all.
    private val panorama = TileImageContext.of(4000, 1000)
    private val portrait = TileImageContext.of(900, 1200)
    private val photo = TileImageContext.of(4000, 3000)
    private val tileShaped = TileImageContext.of(1920, 1080)

    @Test
    fun `what filling a tile cuts follows the tile's shape, not the screen's`() {
        assertEquals(PictureCut.SIDES, panorama.cut)
        assertEquals(PictureCut.TOP_AND_BOTTOM, portrait.cut)
        assertEquals(PictureCut.TOP_AND_BOTTOM, photo.cut)
        assertEquals(PictureCut.NONE, tileShaped.cut)
        // Under 1% off a tile's shape: nothing a position would move.
        assertEquals(PictureCut.NONE, TileImageContext.of(1366, 768).cut)
    }

    @Test
    fun `the position offers the ends of the side filling cuts, the middle first`() {
        assertEquals(listOf(PicturePosition.CENTER, PicturePosition.LEFT, PicturePosition.RIGHT), TilePosition.values(panorama))
        assertEquals(listOf(PicturePosition.CENTER, PicturePosition.TOP, PicturePosition.BOTTOM), TilePosition.values(portrait))
        assertEquals(listOf(PicturePosition.CENTER), TilePosition.values(tileShaped))
        assertEquals(PicturePosition.entries, TilePosition.values(TileImageContext.UNKNOWN))
    }

    @Test
    fun `the fit offers fill and stretch everywhere`() {
        for (context in listOf(panorama, portrait, tileShaped, TileImageContext.UNKNOWN)) assertEquals(Fit.entries, TileFit.values(context))
    }

    @Test
    fun `both apply to a picture that's cut, the position only while it fills`() {
        val filling = TileImage(beach)
        val stretched = TileImage(beach, Framing(Fit.STRETCH))

        assertTrue(TileFit.appliesTo(filling, panorama))
        assertTrue(TilePosition.appliesTo(filling, panorama))
        assertTrue(TileFit.appliesTo(stretched, portrait))
        assertFalse(TilePosition.appliesTo(stretched, portrait))
    }

    @Test
    fun `neither applies to the app's own banner, nor to a picture of the tile's shape`() {
        val banner = TileImage(null)
        val tileShapedPicture = TileImage(beach)

        assertFalse(TileFit.appliesTo(banner, TileImageContext.UNKNOWN))
        assertFalse(TilePosition.appliesTo(banner, TileImageContext.UNKNOWN))
        assertFalse(TileFit.appliesTo(tileShapedPicture, tileShaped))
        assertFalse(TilePosition.appliesTo(tileShapedPicture, tileShaped))
    }

    @Test
    fun `each option reads and changes only its part of the framing`() {
        val image = TileImage(beach, Framing(Fit.FILL, PicturePosition.LEFT))

        assertEquals(TileImage(beach, Framing(Fit.STRETCH, PicturePosition.LEFT)), TileFit.set(image, Fit.STRETCH))
        assertEquals(TileImage(beach, Framing(Fit.FILL, PicturePosition.RIGHT)), TilePosition.step(image, forward = true, panorama))
        assertEquals(PicturePosition.LEFT, TilePosition.get(image))
        assertEquals(Fit.FILL, TileFit.get(image))
    }

    @Test
    fun `the defaults are what a tile's first picture starts as`() {
        assertEquals(Fit.FILL, TileFit.default)
        assertEquals(PicturePosition.CENTER, TilePosition.default)
        assertEquals(TileBackdrop.LIGHT_GLASS, Backdrop.default)
        assertEquals(TileFocusShape.TILE, Focus.default)
    }

    @Test
    fun `the picker shows the fit, the position, the backdrop and the focus, in that order`() {
        assertEquals(listOf(TileFit, TilePosition, Backdrop, Focus), tileImageOptions)
    }

    @Test
    fun `the backdrop and the focus offer every value everywhere, in order`() {
        val seeThrough = TileImageContext.of(1920, 1080, seeThrough = true)
        for (context in listOf(panorama, tileShaped, seeThrough, TileImageContext.UNKNOWN)) {
            assertEquals(listOf(TileBackdrop.NONE, TileBackdrop.LIGHT_GLASS, TileBackdrop.DARK_GLASS, TileBackdrop.FROSTED), Backdrop.values(context))
            assertEquals(listOf(TileFocusShape.TILE, TileFocusShape.SHAPE), Focus.values(context))
        }
    }

    @Test
    fun `the backdrop and the focus apply only to a see-through picture`() {
        val picture = TileImage(beach)
        val seeThrough = TileImageContext.of(4000, 1000, seeThrough = true)

        for (option in listOf(Backdrop, Focus)) {
            assertTrue(option.appliesTo(picture, seeThrough))
            assertTrue("whatever its shape", option.appliesTo(picture, TileImageContext.of(1920, 1080, seeThrough = true)))
            assertFalse("opaque", option.appliesTo(picture, panorama))
            assertFalse("not known yet", option.appliesTo(picture, TileImageContext.UNKNOWN))
            assertFalse("the app's own banner", option.appliesTo(TileImage(null), seeThrough))
        }
    }

    @Test
    fun `the backdrop and the focus read and change only their part of the look`() {
        val image = TileImage(beach, Framing(Fit.STRETCH, PicturePosition.LEFT), TileLook(TileBackdrop.DARK_GLASS, TileFocusShape.SHAPE))

        assertEquals(image.copy(look = TileLook(TileBackdrop.FROSTED, TileFocusShape.SHAPE)), Backdrop.set(image, TileBackdrop.FROSTED))
        assertEquals(image.copy(look = TileLook(TileBackdrop.DARK_GLASS, TileFocusShape.TILE)), Focus.set(image, TileFocusShape.TILE))
        assertEquals(TileBackdrop.DARK_GLASS, Backdrop.get(image))
        assertEquals(TileFocusShape.SHAPE, Focus.get(image))
    }

    @Test
    fun `stepping the backdrop and the focus goes through their values, around`() {
        val seeThrough = TileImageContext.of(1920, 1080, seeThrough = true)
        var image = TileImage(beach, look = TileLook(TileBackdrop.NONE))
        val backdrops = List(4) { image = Backdrop.step(image, forward = true, seeThrough); Backdrop.get(image) }

        assertEquals(listOf(TileBackdrop.LIGHT_GLASS, TileBackdrop.DARK_GLASS, TileBackdrop.FROSTED, TileBackdrop.NONE), backdrops)
        assertEquals(TileBackdrop.FROSTED, Backdrop.get(Backdrop.step(TileImage(beach, look = TileLook(TileBackdrop.NONE)), forward = false, seeThrough)))
        assertEquals(TileFocusShape.SHAPE, Focus.get(Focus.step(TileImage(beach), forward = true, seeThrough)))
        assertEquals(TileFocusShape.TILE, Focus.get(Focus.step(TileImage(beach, look = TileLook(focus = TileFocusShape.SHAPE)), forward = true, seeThrough)))
    }

    @Test
    fun `a draft keeps only the position its picture offers in a tile`() {
        val top = TileImage(beach, Framing(Fit.FILL, PicturePosition.TOP))

        assertEquals(TileImage(beach), top.offeredIn(panorama))
        assertEquals(top, top.offeredIn(portrait))
        assertEquals(top, top.offeredIn(TileImageContext.UNKNOWN))
    }

    @Test
    fun `another picture chosen starts in the middle with the fit kept, then offers its own positions`() {
        val stored = TileImage(beach, Framing(Fit.STRETCH))
        val draft = TileImage(alps, Framing(Fit.FILL, PicturePosition.RIGHT))

        val chosen = draft.choosing(beach, stored).choosing(alps, stored)

        assertEquals(TileImage(alps, Framing(Fit.STRETCH)), chosen)
        assertEquals(chosen, chosen.offeredIn(portrait))
    }
}
