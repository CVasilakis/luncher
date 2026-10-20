package com.luncher.domain.appearance

import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureCut
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.PictureStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundTest {

    @Test
    fun `draws its picture as its options say`() {
        val background = Background(PictureId("sunset"), Fit.FILL, PicturePosition.BOTTOM, 20, Blur.STRONG)

        assertEquals(PictureStyle(Framing(Fit.FILL, PicturePosition.BOTTOM), 20, Blur.STRONG), background.style)
    }

    @Test
    fun `has no picture by default, drawn as it is`() {
        assertEquals(null, Background().picture)
        assertEquals(PictureStyle(), Background().style)
    }

    private val wide = OptionContext(backgroundCut = PictureCut.SIDES)
    private val tall = OptionContext(backgroundCut = PictureCut.TOP_AND_BOTTOM)
    private val sameShape = OptionContext(backgroundCut = PictureCut.NONE)

    @Test
    fun `a background keeps only a position its picture offers, and its fit`() {
        val top = Background(PictureId("sunset"), Fit.FILL, PicturePosition.TOP, 20, Blur.LIGHT)

        assertEquals(top.copy(position = PicturePosition.CENTER), top.offeredIn(wide))
        assertEquals(top, top.offeredIn(tall))
        assertEquals(top.copy(position = PicturePosition.CENTER), top.offeredIn(sameShape))
        assertEquals(top, top.offeredIn(OptionContext.UNKNOWN))
        val left = top.copy(fit = Fit.STRETCH, position = PicturePosition.LEFT)
        assertEquals(left, left.offeredIn(wide))
        assertEquals("the fit kept", left.copy(position = PicturePosition.CENTER), left.offeredIn(sameShape))
    }
}
