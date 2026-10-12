package com.luncher.domain.appearance

import org.junit.Assert.assertEquals
import org.junit.Test

class CornersTest {

    @Test
    fun `a radius follows the density`() {
        assertEquals(16f, Corners.Radius(8).pixels(tileHeight = 172, pixelsPerDp = 2f))
        assertEquals(0f, Corners.Radius(0).pixels(tileHeight = 172, pixelsPerDp = 2f))
    }

    // Past half the height the ends are round already; more would draw nothing different.
    @Test
    fun `a radius is at most half the tile's height`() {
        assertEquals(43f, Corners.Radius(32).pixels(tileHeight = 86, pixelsPerDp = 2f))
    }

    @Test
    fun `a pill's ends are half circles, whatever the tile's height`() {
        assertEquals(43f, Corners.Pill.pixels(tileHeight = 86, pixelsPerDp = 2f))
        assertEquals(60f, Corners.Pill.pixels(tileHeight = 120, pixelsPerDp = 1f))
    }
}
