package com.luncher.domain.pictures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `filling cuts a wide picture at the sides, a tall one at the top and bottom, one of the space's shape nowhere`() {
        assertEquals(PictureCut.SIDES, pictureCut(6000, 1000, 1920, 1080))
        assertEquals(PictureCut.TOP_AND_BOTTOM, pictureCut(4000, 3000, 1920, 1080))
        assertEquals(PictureCut.TOP_AND_BOTTOM, pictureCut(3024, 4032, 1920, 1080))
        assertEquals(PictureCut.NONE, pictureCut(3840, 2160, 1920, 1080))
        assertEquals(PictureCut.NONE, pictureCut(1920, 1080, 1920, 1080))
        assertEquals("on a 4:3 screen", PictureCut.SIDES, pictureCut(1920, 1080, 1024, 768))
        assertEquals("on a 4:3 screen", PictureCut.NONE, pictureCut(4000, 3000, 1024, 768))
    }

    // A 16:9 picture on a 1366 x 768 TV, whose shape is nearly 16:9: filling cuts 0.375 px.
    @Test
    fun `a cut of less than 1 percent of the screen counts as none`() {
        assertEquals(PictureCut.NONE, pictureCut(1920, 1080, 1366, 768))
        // On 1920 x 1080, 1% of the width is 19.2 px, of the height 10.8 px, the picture scaled to the screen.
        assertEquals(PictureCut.NONE, pictureCut(1939, 1080, 1920, 1080))
        assertEquals(PictureCut.SIDES, pictureCut(1940, 1080, 1920, 1080))
        assertEquals(PictureCut.NONE, pictureCut(1920, 1090, 1920, 1080))
        assertEquals(PictureCut.TOP_AND_BOTTOM, pictureCut(1920, 1091, 1920, 1080))
        // Measured on the screen, not the picture: the same shapes at another scale cut alike.
        assertEquals(PictureCut.NONE, pictureCut(3878, 2160, 1920, 1080))
        assertEquals(PictureCut.SIDES, pictureCut(970, 540, 1920, 1080))
    }

    @Test
    fun `a cut of exactly 1 percent counts`() {
        assertEquals(PictureCut.SIDES, pictureCut(101, 100, 100, 100))
        assertEquals(PictureCut.TOP_AND_BOTTOM, pictureCut(100, 101, 100, 100))
    }

    @Test
    fun `nothing is cut from an empty picture, or for an empty space`() {
        assertEquals(PictureCut.NONE, pictureCut(0, 0, 1920, 1080))
        assertEquals(PictureCut.NONE, pictureCut(4000, 0, 1920, 1080))
        assertEquals(PictureCut.NONE, pictureCut(4000, 3000, 0, 0))
        assertEquals(PictureCut.NONE, pictureCut(-1, 3000, 1920, 1080))
    }

    // The positions it offers have to move what frame() shows, and only those.
    @Test
    fun `the cut agrees with what filling shows`() {
        val screens = listOf(1920 to 1080, 1366 to 768, 1024 to 768, 2560 to 1080, 1280 to 800)
        val pictures = listOf(6000 to 1000, 4000 to 3000, 3024 to 4032, 1920 to 1080, 1939 to 1080, 1940 to 1080, 1001 to 777, 1 to 1, 8000 to 8000)
        for ((width, height) in screens) {
            for ((pictureWidth, pictureHeight) in pictures) {
                val cut = pictureCut(pictureWidth, pictureHeight, width, height)
                val left = frame(pictureWidth, pictureHeight, width, height, fill(PicturePosition.LEFT))
                val right = frame(pictureWidth, pictureHeight, width, height, fill(PicturePosition.RIGHT))
                val top = frame(pictureWidth, pictureHeight, width, height, fill(PicturePosition.TOP))
                val bottom = frame(pictureWidth, pictureHeight, width, height, fill(PicturePosition.BOTTOM))
                val what = "$pictureWidth x $pictureHeight on $width x $height: $cut"
                // The part of the shown side that's cut, as on the screen.
                val sides = (right.left - left.left) / (left.right - left.left)
                val topAndBottom = (bottom.top - top.top) / (top.bottom - top.top)
                when (cut) {
                    PictureCut.SIDES -> assertTrue(what, sides >= 0.01f && topAndBottom == 0f)
                    PictureCut.TOP_AND_BOTTOM -> assertTrue(what, topAndBottom >= 0.01f && sides == 0f)
                    PictureCut.NONE -> assertTrue(what, sides < 0.01f && topAndBottom < 0.01f)
                }
            }
        }
    }
}
