package com.luncher.domain.appearance

import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
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
}
