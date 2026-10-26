package com.luncher.domain.apps

import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureCut
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.choosing
import com.luncher.domain.pictures.offeredIn
import org.junit.Assert.assertEquals
import org.junit.Test

// The rules of choosing come from PictureChoice (PictureChoiceTest): here, only what TileImage says of itself.
class TileImageTest {

    private val beach = PictureId("beach")
    private val alps = PictureId("alps")

    @Test
    fun `another picture starts in the middle, its fit kept, the stored one comes back as stored`() {
        val stored = TileImage(beach, Framing(Fit.FILL, PicturePosition.TOP))
        val draft = TileImage(null, Framing(Fit.STRETCH))

        assertEquals(TileImage(alps, Framing(Fit.STRETCH)), draft.choosing(alps, stored))
        assertEquals(stored, draft.choosing(alps, stored).choosing(beach, stored))
    }

    @Test
    fun `the app's own banner is a choice without a picture, the framing kept`() {
        val draft = TileImage(beach, Framing(Fit.STRETCH))

        assertEquals(TileImage(null, Framing(Fit.STRETCH)), draft.choosing(null, TileImage(alps)))
    }

    @Test
    fun `the tile's look is kept whichever picture is chosen, the stored one's too, and with the app's own banner`() {
        val look = TileLook(TileBackdrop.FROSTED, TileFocusShape.SHAPE)
        val stored = TileImage(beach, Framing(Fit.FILL, PicturePosition.TOP), TileLook(TileBackdrop.DARK_GLASS))
        val draft = TileImage(null, Framing(Fit.STRETCH), look)

        assertEquals(TileImage(alps, Framing(Fit.STRETCH), look), draft.choosing(alps, stored))
        assertEquals("the stored framing, the draft's look", stored.copy(look = look), draft.choosing(alps, stored).choosing(beach, stored))
        assertEquals(TileImage(null, Framing(Fit.STRETCH), look), draft.choosing(alps, stored).choosing(null, stored))
    }

    @Test
    fun `the look counts only for a see-through picture`() {
        val look = TileLook(TileBackdrop.NONE, TileFocusShape.SHAPE)
        val seeThrough = TileImageContext.of(4000, 1000, seeThrough = true)
        val opaque = TileImageContext.of(4000, 1000)
        val image = TileImage(beach, Framing(Fit.FILL, PicturePosition.TOP), look)

        assertEquals(TileImage(beach, look = look), image.counted(seeThrough))
        assertEquals("opaque: the default look", TileImage(beach), image.counted(opaque))
        assertEquals("not known yet", image.copy(look = TileLook()), image.counted(TileImageContext.UNKNOWN))
        assertEquals("the app's own banner", TileImage(null), TileImage(null, look = look).counted(seeThrough))
        assertEquals("the draft keeps it", image.copy(framing = Framing()), image.offeredIn(opaque))
    }

    @Test
    fun `only a position that moves the picture in the tile is kept`() {
        val image = TileImage(beach, Framing(Fit.FILL, PicturePosition.TOP))

        assertEquals(image.copy(framing = Framing(Fit.FILL)), image.offeredIn(PictureCut.SIDES))
        assertEquals(image, image.offeredIn(PictureCut.TOP_AND_BOTTOM))
    }
}
