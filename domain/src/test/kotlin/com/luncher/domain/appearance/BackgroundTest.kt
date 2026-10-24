package com.luncher.domain.appearance

import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureCut
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.pictures.choosing
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundTest {

    @Test
    fun `draws its picture as its options say`() {
        val background = Background(PictureId("sunset"), Framing(Fit.FILL, PicturePosition.BOTTOM), 20, Blur.STRONG)

        assertEquals(PictureStyle(Framing(Fit.FILL, PicturePosition.BOTTOM), 20, Blur.STRONG), background.style)
    }

    @Test
    fun `has no picture by default, drawn as it is`() {
        assertEquals(null, Background().picture)
        assertEquals(PictureStyle(), Background().style)
    }

    private val wide = AppearanceContext(backgroundCut = PictureCut.SIDES)
    private val tall = AppearanceContext(backgroundCut = PictureCut.TOP_AND_BOTTOM)
    private val sameShape = AppearanceContext(backgroundCut = PictureCut.NONE)

    @Test
    fun `a background keeps only a position its picture offers, and its fit`() {
        val top = Background(PictureId("sunset"), Framing(Fit.FILL, PicturePosition.TOP), 20, Blur.LIGHT)

        assertEquals(top.copy(framing = Framing(Fit.FILL, PicturePosition.CENTER)), top.offeredIn(wide))
        assertEquals(top, top.offeredIn(tall))
        assertEquals(top.copy(framing = Framing(Fit.FILL, PicturePosition.CENTER)), top.offeredIn(sameShape))
        assertEquals(top, top.offeredIn(AppearanceContext.UNKNOWN))
        val left = top.copy(framing = Framing(Fit.STRETCH, PicturePosition.LEFT))
        assertEquals(left, left.offeredIn(wide))
        assertEquals("the fit kept", left.copy(framing = Framing(Fit.STRETCH, PicturePosition.CENTER)), left.offeredIn(sameShape))
    }

    @Test
    fun `another picture chosen starts in the middle, neither dimmed nor blurred, its fit kept`() {
        val stored = Background(PictureId("sunset"), Framing(Fit.FILL, PicturePosition.TOP), 20, Blur.LIGHT)
        val draft = stored.copy(framing = Framing(Fit.STRETCH, PicturePosition.TOP), dim = 40)

        assertEquals(Background(PictureId("beach"), Framing(Fit.STRETCH)), draft.choosing(PictureId("beach"), stored))
        assertEquals("no picture alike", Background(null, Framing(Fit.STRETCH)), draft.choosing(null, stored))
    }

    @Test
    fun `the stored picture chosen comes back as it's stored`() {
        val stored = Background(PictureId("sunset"), Framing(Fit.FILL, PicturePosition.TOP), 20, Blur.LIGHT)
        val draft = Background(PictureId("beach"), Framing(Fit.STRETCH), dim = 60)

        assertEquals(stored, draft.choosing(PictureId("sunset"), stored))
        assertEquals("no picture stored", Background(dim = 20), Background(PictureId("beach")).choosing(null, Background(dim = 20)))
    }

    @Test
    fun `the picture it has chosen again changes nothing`() {
        val draft = Background(PictureId("beach"), Framing(Fit.FILL, PicturePosition.LEFT), 60, Blur.STRONG)

        assertEquals(draft, draft.choosing(PictureId("beach"), Background(PictureId("sunset"))))
    }
}
