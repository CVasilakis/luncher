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

    // Columns for a width: tiles of about 154 px, 24 px apart, as on the home screen at 160 dpi,
    // where 1 dp is 1 px. Widths are the screen's, less 2 x 48 of padding.
    private fun columnsFor(width: Int) = TileGrid.columnsFor(width, tileWidth = 154, gap = 24)

    @Test
    fun `as many columns as fit tiles of about the given width`() {
        assertEquals(5, columnsFor(864))    // a 16:9 TV, 960 dp
        assertEquals(4, columnsFor(624))    // 4:3, 720 dp
        assertEquals(7, columnsFor(1164))   // 21:9, 1260 dp
        assertEquals(10, columnsFor(1824))  // 1080p at 160 dpi, 1920 dp
    }

    @Test
    fun `the nearest whole number of columns`() {
        assertEquals(5, columnsFor(795))    // (795 + 24) / (154 + 24) = 4.6
        assertEquals(4, columnsFor(759))    // 4.4
    }

    @Test
    fun `at least one column, however narrow`() {
        assertEquals(1, columnsFor(0))
        assertEquals(1, columnsFor(50))
    }

    // Moves: 5 columns, so with 12 tiles rows are 0-4, 5-9 and 10-11.
    private val twelve = grid(tiles = 12)

    @Test
    fun `left and right move one place along the order, wrapping rows`() {
        assertEquals(6, twelve.indexToward(5, Direction.RIGHT))
        assertEquals(5, twelve.indexToward(4, Direction.RIGHT))    // end of a row: start of the next
        assertEquals(4, twelve.indexToward(5, Direction.LEFT))     // start of a row: end of the one above
    }

    @Test
    fun `left and right stop at the ends`() {
        assertEquals(null, twelve.indexToward(0, Direction.LEFT))
        assertEquals(null, twelve.indexToward(11, Direction.RIGHT))
    }

    @Test
    fun `up and down move one row`() {
        assertEquals(8, twelve.indexToward(3, Direction.DOWN))
        assertEquals(3, twelve.indexToward(8, Direction.UP))
        assertEquals(10, twelve.indexToward(5, Direction.DOWN))
    }

    @Test
    fun `down where the next row is too short goes to the last place`() {
        assertEquals(11, twelve.indexToward(8, Direction.DOWN))
    }

    @Test
    fun `up from the first row and down from the last stop`() {
        assertEquals(null, twelve.indexToward(3, Direction.UP))
        assertEquals(null, twelve.indexToward(11, Direction.DOWN))
        assertEquals(null, twelve.indexToward(10, Direction.DOWN))
    }

    @Test
    fun `a single tile can't move`() {
        val one = grid(tiles = 1)

        assertEquals(listOf(null, null, null, null), Direction.entries.map { one.indexToward(0, it) })
    }

    @Test
    fun `columns count from the left of each row`() {
        assertEquals(listOf(0, 4, 0, 3, 1), listOf(0, 4, 5, 8, 11).map(twelve::column))
    }

    @Test
    fun `a tile coming in from above takes the first row, in its column`() {
        assertEquals(3, twelve.entryIndex(Direction.DOWN, column = 3))
        assertEquals(2, grid(tiles = 2).entryIndex(Direction.DOWN, column = 4))   // as near as there are tiles
        assertEquals(0, grid(tiles = 0).entryIndex(Direction.DOWN, column = 4))
    }

    @Test
    fun `a tile coming in from below takes the last row, in its column`() {
        assertEquals(11, twelve.entryIndex(Direction.UP, column = 1))
        assertEquals(12, twelve.entryIndex(Direction.UP, column = 4))   // after the last tile
        assertEquals(8, grid(tiles = 10).entryIndex(Direction.UP, column = 3))   // a full last row
        assertEquals(0, grid(tiles = 0).entryIndex(Direction.UP, column = 2))
    }
}
