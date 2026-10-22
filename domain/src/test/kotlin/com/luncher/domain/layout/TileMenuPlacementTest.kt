package com.luncher.domain.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class TileMenuPlacementTest {

    // A 1920x1080 screen less a 96x64 margin; a menu 200x150, 10 from the tile.
    private val room = PixelBounds(96, 64, 1824, 1016)

    private fun place(tile: PixelBounds, height: Int = 150) =
        tileMenuPlacement(tile, menuWidth = 200, menuHeight = height, room, gap = 10)

    @Test
    fun `goes above the tile, centered on it`() {
        assertEquals(TileMenuPlacement(left = 400, top = 340, above = true), place(PixelBounds(300, 500, 700, 725)))
    }

    @Test
    fun `goes below where there's no room above`() {
        assertEquals(TileMenuPlacement(left = 400, top = 335, above = false), place(PixelBounds(300, 100, 700, 325)))
    }

    @Test
    fun `just fits above`() {
        assertEquals(64, place(PixelBounds(300, 224, 700, 449)).top)
        assertEquals(false, place(PixelBounds(300, 223, 700, 448)).above)
    }

    // The first and last tiles of a row, zoomed past the room's edge.
    @Test
    fun `where centered would leave the room, lines up with the tile's edge on that side`() {
        assertEquals(81, place(PixelBounds(81, 500, 231, 584)).left)               // centered: 56
        assertEquals(1839 - 200, place(PixelBounds(1689, 500, 1839, 584)).left)   // centered: 1664 to 1864
    }

    @Test
    fun `centered just inside the room, stays centered`() {
        assertEquals(96, place(PixelBounds(46, 500, 346, 584)).left)
        assertEquals(1624, place(PixelBounds(1574, 500, 1874, 584)).left)
    }

    @Test
    fun `fitting neither, goes over the tile's edge on the side with more room`() {
        val tall = 700
        assertEquals(TileMenuPlacement(left = 400, top = 64, above = true), place(PixelBounds(300, 600, 700, 825), tall))
        assertEquals(TileMenuPlacement(left = 400, top = 1016 - tall, above = false), place(PixelBounds(300, 200, 700, 425), tall))
    }

    @Test
    fun `taller than the room, starts at its top`() {
        assertEquals(64, place(PixelBounds(300, 200, 700, 425), height = 2000).top)
    }
}
