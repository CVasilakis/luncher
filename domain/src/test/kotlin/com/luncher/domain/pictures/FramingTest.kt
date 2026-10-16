package com.luncher.domain.pictures

import org.junit.Assert.assertEquals
import org.junit.Test

class FramingTest {

    private fun area(left: Number, top: Number, right: Number, bottom: Number) =
        PictureArea(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

    private fun fill(position: PicturePosition) = Framing(Fit.FILL, position)

    @Test
    fun `a picture of the space's shape fills it whole, at any position`() {
        for (position in PicturePosition.entries) {
            assertEquals(position.name, area(0, 0, 3840, 2160), frame(3840, 2160, 1920, 1080, fill(position)))
        }
    }

    @Test
    fun `a 4 by 3 photo fills a 16 by 9 screen with its middle band, or its top or bottom`() {
        // 4000 x 3000 at the screen's shape: 4000 x 2250, 750 px to cut.
        assertEquals(area(0, 375, 4000, 2625), frame(4000, 3000, 1920, 1080, fill(PicturePosition.CENTER)))
        assertEquals(area(0, 0, 4000, 2250), frame(4000, 3000, 1920, 1080, fill(PicturePosition.TOP)))
        assertEquals(area(0, 750, 4000, 3000), frame(4000, 3000, 1920, 1080, fill(PicturePosition.BOTTOM)))
    }

    @Test
    fun `left and right keep the middle of a picture cut at the top and bottom`() {
        assertEquals(area(0, 375, 4000, 2625), frame(4000, 3000, 1920, 1080, fill(PicturePosition.LEFT)))
        assertEquals(area(0, 375, 4000, 2625), frame(4000, 3000, 1920, 1080, fill(PicturePosition.RIGHT)))
    }

    @Test
    fun `a panorama fills the screen with its middle, or its left or right end`() {
        // 6000 x 1000 at 16:9: 1777.8 x 1000.
        val width = 1000f * 1920 / 1080
        assertEquals(area((6000 - width) / 2, 0, (6000 + width) / 2, 1000), frame(6000, 1000, 1920, 1080, fill(PicturePosition.CENTER)))
        assertEquals(area(0, 0, width, 1000), frame(6000, 1000, 1920, 1080, fill(PicturePosition.LEFT)))
        assertEquals(area(6000 - width, 0, 6000, 1000), frame(6000, 1000, 1920, 1080, fill(PicturePosition.RIGHT)))
        assertEquals("top and bottom keep the middle", area((6000 - width) / 2, 0, (6000 + width) / 2, 1000), frame(6000, 1000, 1920, 1080, fill(PicturePosition.TOP)))
    }

    @Test
    fun `a portrait photo fills a landscape screen with a band across it`() {
        // 3024 x 4032 at 16:9: 3024 x 1701.
        assertEquals(area(0, 1165.5, 3024, 2866.5), frame(3024, 4032, 1920, 1080, fill(PicturePosition.CENTER)))
        assertEquals(area(0, 0, 3024, 1701), frame(3024, 4032, 1920, 1080, fill(PicturePosition.TOP)))
        assertEquals(area(0, 2331, 3024, 4032), frame(3024, 4032, 1920, 1080, fill(PicturePosition.BOTTOM)))
    }

    @Test
    fun `odd sizes keep the space's shape exactly`() {
        val area = frame(1001, 777, 333, 171, fill(PicturePosition.CENTER))
        assertEquals(333f / 171, (area.right - area.left) / (area.bottom - area.top), 0.0001f)
        assertEquals(1001f, area.right - area.left)
        assertEquals((777 - area.bottom) , area.top, 0.001f)   // centered
    }

    @Test
    fun `stretch shows the whole picture, whatever the position`() {
        for (position in PicturePosition.entries) {
            assertEquals(area(0, 0, 4000, 3000), frame(4000, 3000, 1920, 1080, Framing(Fit.STRETCH, position)))
        }
    }

    @Test
    fun `an empty picture or space is shown whole rather than divided by zero`() {
        assertEquals(area(0, 0, 0, 0), frame(0, 0, 1920, 1080, Framing()))
        assertEquals(area(0, 0, 4000, 3000), frame(4000, 3000, 0, 0, Framing()))
    }
}
