package com.luncher.domain.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class TileMenuPlacementTest {

    // A 1920x1080 screen less a 96x64 margin; a menu 200x150, 10 from the tile.
    private val room = PixelBounds(96, 64, 1824, 1016)

    private fun place(tile: PixelBounds, height: Int = 150, width: Int = 200, room: PixelBounds = this.room) =
        tileMenuPlacement(tile, menuWidth = width, menuHeight = height, room, gap = 10)

    @Test
    fun `goes above the tile, centered on it`() {
        assertEquals(TileMenuPlacement(left = 400, top = 340, MenuSide.ABOVE), place(PixelBounds(300, 500, 700, 725)))
    }

    @Test
    fun `goes below where there's no room above`() {
        assertEquals(TileMenuPlacement(left = 400, top = 335, MenuSide.BELOW), place(PixelBounds(300, 100, 700, 325)))
    }

    @Test
    fun `just fits above`() {
        assertEquals(64, place(PixelBounds(300, 224, 700, 449)).top)
        assertEquals(MenuSide.BELOW, place(PixelBounds(300, 223, 700, 448)).side)
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

    // 400 px tall: 296 above the tile at 370, 386 below it at 620.
    @Test
    fun `fitting neither above nor below, goes beside the tile, centered on it down`() {
        assertEquals(TileMenuPlacement(left = 710, top = 295, MenuSide.RIGHT), place(PixelBounds(300, 370, 700, 620), height = 400))
    }

    @Test
    fun `beside, on the side with more room`() {
        assertEquals(MenuSide.RIGHT, place(PixelBounds(700, 370, 1000, 620), height = 400).side)    // 824 at the right, 604 at the left
        assertEquals(MenuSide.LEFT, place(PixelBounds(1000, 370, 1300, 620), height = 400).side)    // 524 at the right, 904 at the left
        assertEquals(TileMenuPlacement(left = 1000 - 10 - 200, top = 295, MenuSide.LEFT), place(PixelBounds(1000, 370, 1300, 620), height = 400))
    }

    // A row's last tile, its zoom past the room: no room at its right.
    @Test
    fun `beside, at the left of a row's last tile`() {
        assertEquals(TileMenuPlacement(left = 1689 - 10 - 200, top = 295, MenuSide.LEFT), place(PixelBounds(1689, 370, 1839, 620), height = 400))
    }

    // Centered down, it would leave the room at the bottom, or at the top.
    @Test
    fun `beside, moved up or down into the room`() {
        assertEquals(1016 - 900, place(PixelBounds(300, 180, 700, 1000), height = 900).top)
        assertEquals(64, place(PixelBounds(300, 64, 700, 300), height = 900).top)
    }

    // A menu as wide as the room fits beside no tile; across, in line with the tile's left edge.
    @Test
    fun `fitting nowhere, goes over the tile's edge on the side with more room`() {
        val tall = 700
        val wide = 1728
        assertEquals(TileMenuPlacement(left = 300, top = 64, MenuSide.ABOVE), place(PixelBounds(300, 600, 700, 825), tall, wide))
        assertEquals(TileMenuPlacement(left = 300, top = 1016 - tall, MenuSide.BELOW), place(PixelBounds(300, 200, 700, 425), tall, wide))
    }

    @Test
    fun `taller than the room, starts at its top`() {
        assertEquals(64, place(PixelBounds(300, 200, 700, 425), height = 2000).top)
    }

    private fun height(tile: PixelBounds, height: Int, width: Int = 200, room: PixelBounds = this.room) =
        tileMenuHeight(tile, menuWidth = width, menuHeight = height, room, gap = 10, least = 100)

    @Test
    fun `fitting whole somewhere, takes its whole height`() {
        assertEquals(150, height(PixelBounds(300, 500, 700, 725), 150))     // above
        assertEquals(400, height(PixelBounds(300, 370, 700, 620), 400))     // beside
    }

    // A tile in the middle of a narrow room: 296 above it, 386 below, no room beside.
    @Test
    fun `fitting nowhere whole, takes the tallest space, above or below`() {
        val narrow = PixelBounds(96, 64, 800, 1016)
        assertEquals(386, height(PixelBounds(300, 370, 600, 620), 500, room = narrow))
        assertEquals(296, height(PixelBounds(300, 370, 600, 900), 500, room = narrow))
    }

    // Beside, the whole room's height, more than above or below.
    @Test
    fun `taller than the room, takes the room's height beside the tile`() {
        assertEquals(952, height(PixelBounds(300, 370, 700, 620), 2000))
    }

    @Test
    fun `whatever it takes, the placement puts it clear of the tile`() {
        val narrow = PixelBounds(96, 64, 800, 1016)
        for ((tile, room) in listOf(PixelBounds(300, 370, 600, 620) to narrow, PixelBounds(300, 370, 600, 900) to narrow, PixelBounds(300, 370, 700, 620) to room)) {
            val h = height(tile, 2000, room = room)
            val placed = place(tile, height = h, room = room)
            val bottom = placed.top + h
            val right = placed.left + 200
            val apart = bottom <= tile.top || placed.top >= tile.bottom || right <= tile.left || placed.left >= tile.right
            assertEquals("${placed} over $tile", true, apart)
        }
    }

    // Less than a few rows above, below or beside: the least, over the tile's edge by as little as
    // it can, on the side with more room: 76 above, 60 below.
    @Test
    fun `with less than the least everywhere, the least, over the tile's edge as little as it can`() {
        val low = PixelBounds(96, 64, 800, 400)
        val tile = PixelBounds(300, 150, 600, 330)
        assertEquals(100, height(tile, 500, room = low))
        assertEquals(TileMenuPlacement(left = 350, top = 64, MenuSide.ABOVE), place(tile, height = 100, room = low))
    }

    @Test
    fun `with less than the least everywhere, a short menu keeps its height`() {
        val low = PixelBounds(96, 64, 800, 400)
        assertEquals(90, height(PixelBounds(300, 150, 600, 330), 90, room = low))
    }
}
