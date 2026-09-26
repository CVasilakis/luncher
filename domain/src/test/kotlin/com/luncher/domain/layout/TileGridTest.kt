package com.luncher.domain.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class TileGridTest {

    // 5 columns in 1000 px with 25 px gaps: 4 gaps take 100 px, each tile gets 180 x 101.
    private fun grid(tiles: Int, width: Int = 1000) = TileGrid(tiles, columns = 5, width = width, gap = 25)

    @Test
    fun `tiles share the width and have the banner's shape`() {
        val grid = grid(tiles = 5)

        assertEquals(180, grid.tileWidth)
        assertEquals(101, grid.tileHeight)   // 180 * 9 / 16, rounded down
    }

    @Test
    fun `fills rows from the left, then the next row`() {
        val grid = grid(tiles = 7)

        assertEquals(listOf(0, 205, 410, 615, 820), (0..4).map(grid::left))
        assertEquals(listOf(0, 0, 0, 0, 0), (0..4).map(grid::top))
        assertEquals(listOf(0, 205), (5..6).map(grid::left))    // second row, starting at the left
        assertEquals(listOf(126, 126), (5..6).map(grid::top))   // 101 + 25
    }

    @Test
    fun `splits what rounding leaves over into equal margins`() {
        val grid = grid(tiles = 5, width = 1004)   // (1004 - 100) / 5 = 180, 4 px left over

        assertEquals(2, grid.left(0))                               // 2 px on the left...
        assertEquals(1004 - 2, grid.left(4) + grid.tileWidth)       // ...and 2 px on the right
    }

    @Test
    fun `a single app starts at the left, not in the middle`() {
        assertEquals(0, grid(tiles = 1).left(0))
    }

    @Test
    fun `content covers every row`() {
        assertEquals(1000, grid(tiles = 11).contentWidth)
        assertEquals(3 * 101 + 2 * 25, grid(tiles = 11).contentHeight)
        assertEquals(101, grid(tiles = 5).contentHeight)
        assertEquals(0, grid(tiles = 0).contentHeight)
    }

    @Test
    fun `a space too narrow for the tiles gives empty tiles instead of failing`() {
        val grid = grid(tiles = 3, width = 50)

        assertEquals(0, grid.tileWidth)
        assertEquals(0, grid.tileHeight)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `needs at least one column`() {
        TileGrid(tileCount = 3, columns = 0, width = 1000, gap = 25)
    }
}
