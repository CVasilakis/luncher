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

    // Placement: rows aligned right, the short row first, at the bottom of the height.

    private fun placed(tiles: Int, alignRight: Boolean = false, shortRowFirst: Boolean = false, height: Int = 0, atBottom: Boolean = false) =
        TileGrid(tiles, columns = 5, width = 1000, gap = 25, alignRight, shortRowFirst, height, atBottom)

    @Test
    fun `a row that isn't full lines up at the right, when aligned right`() {
        val grid = placed(tiles = 7, alignRight = true)

        assertEquals(listOf(0, 205, 410, 615, 820), (0..4).map(grid::left))   // full rows as before
        assertEquals(listOf(615, 820), (5..6).map(grid::left))
        assertEquals(listOf(3, 4), (5..6).map(grid::column))
        assertEquals(820, placed(tiles = 1, alignRight = true).left(0))
    }

    @Test
    fun `the short row can come first, so the last row is full`() {
        val grid = placed(tiles = 7, shortRowFirst = true)

        assertEquals(listOf(0, 205), (0..1).map(grid::left))
        assertEquals(listOf(0, 0), (0..1).map(grid::top))
        assertEquals(listOf(0, 205, 410, 615, 820), (2..6).map(grid::left))
        assertEquals(126, grid.top(2))
        assertEquals(listOf(615, 820), (0..1).map(placed(tiles = 7, alignRight = true, shortRowFirst = true)::left))
    }

    @Test
    fun `with no short row, the short row first changes nothing`() {
        val grid = placed(tiles = 10, shortRowFirst = true)

        assertEquals((0..9).map(grid(tiles = 10)::left), (0..9).map(grid::left))
        assertEquals((0..9).map(grid(tiles = 10)::top), (0..9).map(grid::top))
    }

    @Test
    fun `at the bottom, the rows end at the bottom of the height`() {
        val grid = placed(tiles = 7, height = 600, atBottom = true)   // two rows, 227 px

        assertEquals(600 - 227, grid.top(0))
        assertEquals(600 - 101, grid.top(5))
        assertEquals(600, grid.contentHeight)   // nothing to scroll
    }

    @Test
    fun `at the bottom, rows taller than the height start at the top`() {
        val grid = placed(tiles = 15, height = 300, atBottom = true)   // three rows, 353 px

        assertEquals(0, grid.top(0))
        assertEquals(353, grid.contentHeight)
    }

    @Test
    fun `at the top, the height changes nothing`() {
        assertEquals(0, placed(tiles = 7, height = 600).top(0))
        assertEquals(227, placed(tiles = 7, height = 600).contentHeight)
    }

    // Twelve tiles aligned right: rows 0-4, 5-9, and 10-11 in columns 3 and 4.
    private val twelveRight = placed(tiles = 12, alignRight = true)

    @Test
    fun `aligned right, up and down keep the column`() {
        assertEquals(10, twelveRight.indexToward(8, Direction.DOWN))
        assertEquals(11, twelveRight.indexToward(9, Direction.DOWN))
        assertEquals(8, twelveRight.indexToward(10, Direction.UP))
    }

    @Test
    fun `aligned right, down where the next row has no tile in the column goes to its nearest place`() {
        assertEquals(10, twelveRight.indexToward(6, Direction.DOWN))
    }

    // Twelve tiles with the short row first: 0-1, then 2-6 and 7-11.
    private val twelveShortFirst = placed(tiles = 12, shortRowFirst = true)

    @Test
    fun `with the short row first, up and down keep the column`() {
        assertEquals(0, twelveShortFirst.indexToward(2, Direction.UP))
        assertEquals(3, twelveShortFirst.indexToward(1, Direction.DOWN))
        assertEquals(11, twelveShortFirst.indexToward(6, Direction.DOWN))
        assertEquals(null, twelveShortFirst.indexToward(0, Direction.UP))
        assertEquals(null, twelveShortFirst.indexToward(7, Direction.DOWN))
    }

    @Test
    fun `with the short row first, up where it has no tile in the column goes to its nearest place`() {
        assertEquals(1, twelveShortFirst.indexToward(6, Direction.UP))
        assertEquals(0, placed(tiles = 12, alignRight = true, shortRowFirst = true).indexToward(2, Direction.UP))
    }

    @Test
    fun `aligned right, a tile coming in lands in its column`() {
        assertEquals(12, twelveRight.entryIndex(Direction.UP, column = 4))   // 10 and 11 move left
        assertEquals(11, twelveRight.entryIndex(Direction.UP, column = 3))
        assertEquals(10, twelveRight.entryIndex(Direction.UP, column = 0))   // as near as the row goes
        assertEquals(3, twelveRight.entryIndex(Direction.DOWN, column = 3))  // a full first row
    }

    @Test
    fun `with the short row first, a tile coming in from below lands in the last row, in its column`() {
        // 7 to 11 are the last row; one more tile makes the first row three long.
        assertEquals(10, twelveShortFirst.entryIndex(Direction.UP, column = 2))
        assertEquals(12, twelveShortFirst.entryIndex(Direction.UP, column = 4))
    }

    @Test
    fun `with the short row first, a tile coming in from above lands in the first row, in its column`() {
        assertEquals(1, twelveShortFirst.entryIndex(Direction.DOWN, column = 1))
        assertEquals(2, twelveShortFirst.entryIndex(Direction.DOWN, column = 4))   // as near as the row goes
    }
}
