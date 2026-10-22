package com.luncher.domain.layout

import com.luncher.domain.appearance.HorizontalAlignment
import com.luncher.domain.appearance.VerticalAlignment
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

    // Two rows, 25 px apart, each with 30 px below its tiles for the focused tile's name.
    @Test
    fun `leaves room for a name below each row, the last one too`() {
        val grid = TileGrid(7, columns = 5, width = 1000, gap = 25, nameHeight = 30)

        assertEquals(101, grid.tileHeight)                  // the tiles keep their size
        assertEquals(listOf(0, 205), (5..6).map(grid::left))
        assertEquals(101 + 30 + 25, grid.top(5))
        assertEquals(2 * (101 + 30) + 25, grid.contentHeight)
    }

    @Test
    fun `at the bottom, a name below the last row still fits the height`() {
        val grid = TileGrid(7, columns = 5, width = 1000, gap = 25, height = 600, verticalAlignment = VerticalAlignment.BOTTOM, nameHeight = 30)

        assertEquals(600 - 30, grid.top(5) + grid.tileHeight)
        assertEquals(600, grid.contentHeight)
    }

    // Moves depend on the order and the columns only: the room for names changes none.
    @Test
    fun `the room for names changes no move`() {
        val grid = TileGrid(7, columns = 5, width = 1000, gap = 25, nameHeight = 30)

        for (index in 0 until 7) {
            for (direction in Direction.entries) assertEquals(grid(tiles = 7).indexToward(index, direction), grid.indexToward(index, direction))
            assertEquals(grid(tiles = 7).slot(index), grid.slot(index))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `needs a room for names that isn't negative`() {
        TileGrid(7, columns = 5, width = 1000, gap = 25, nameHeight = -1)
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
    fun `rows count from the top`() {
        assertEquals(listOf(0, 0, 1, 1, 2), listOf(0, 4, 5, 9, 10).map(twelve::row))
        assertEquals(listOf(0, 0, 1), listOf(0, 1, 2).map(placed(tiles = 7, shortRowFirst = true)::row))
    }

    @Test
    fun `slots count half columns from the left of each row`() {
        assertEquals(listOf(0, 8, 0, 6, 2), listOf(0, 4, 5, 8, 11).map(twelve::slot))
    }

    // Alignment: a short row at the right or centered, the short row first, the rows at the bottom
    // or centered in the height.

    private fun placed(
        tiles: Int,
        row: HorizontalAlignment = HorizontalAlignment.LEFT,
        shortRowFirst: Boolean = false,
        height: Int = 0,
        vertical: VerticalAlignment = VerticalAlignment.TOP,
    ) = TileGrid(tiles, columns = 5, width = 1000, gap = 25, row, shortRowFirst, height, vertical)

    @Test
    fun `a row that isn't full lines up at the right, when aligned right`() {
        val grid = placed(tiles = 7, row = HorizontalAlignment.RIGHT)

        assertEquals(listOf(0, 205, 410, 615, 820), (0..4).map(grid::left))   // full rows as before
        assertEquals(listOf(615, 820), (5..6).map(grid::left))
        assertEquals(listOf(6, 8), (5..6).map(grid::slot))
        assertEquals(820, placed(tiles = 1, row = HorizontalAlignment.RIGHT).left(0))
    }

    @Test
    fun `the short row can come first, so the last row is full`() {
        val grid = placed(tiles = 7, shortRowFirst = true)

        assertEquals(listOf(0, 205), (0..1).map(grid::left))
        assertEquals(listOf(0, 0), (0..1).map(grid::top))
        assertEquals(listOf(0, 205, 410, 615, 820), (2..6).map(grid::left))
        assertEquals(126, grid.top(2))
        assertEquals(listOf(615, 820), (0..1).map(placed(tiles = 7, row = HorizontalAlignment.RIGHT, shortRowFirst = true)::left))
    }

    @Test
    fun `with no short row, the short row first changes nothing`() {
        val grid = placed(tiles = 10, shortRowFirst = true)

        assertEquals((0..9).map(grid(tiles = 10)::left), (0..9).map(grid::left))
        assertEquals((0..9).map(grid(tiles = 10)::top), (0..9).map(grid::top))
    }

    @Test
    fun `at the bottom, the rows end at the bottom of the height`() {
        val grid = placed(tiles = 7, height = 600, vertical = VerticalAlignment.BOTTOM)   // two rows, 227 px

        assertEquals(600 - 227, grid.top(0))
        assertEquals(600 - 101, grid.top(5))
        assertEquals(600, grid.contentHeight)   // nothing to scroll
    }

    @Test
    fun `at the bottom, rows taller than the height start at the top`() {
        val grid = placed(tiles = 15, height = 300, vertical = VerticalAlignment.BOTTOM)   // three rows, 353 px

        assertEquals(0, grid.top(0))
        assertEquals(353, grid.contentHeight)
    }

    @Test
    fun `at the top, the height changes nothing`() {
        assertEquals(0, placed(tiles = 7, height = 600).top(0))
        assertEquals(227, placed(tiles = 7, height = 600).contentHeight)
    }

    @Test
    fun `centered in the height, the rows have as much room above as below`() {
        val two = placed(tiles = 7, height = 600, vertical = VerticalAlignment.CENTER)      // 227 px
        val three = placed(tiles = 15, height = 600, vertical = VerticalAlignment.CENTER)   // 353 px

        assertEquals(186, two.top(0))                // (600 - 227) / 2, rounded down
        assertEquals(300, two.top(5) - 25 / 2)       // the gap between the two in the middle
        assertEquals(123, three.top(0))              // (600 - 353) / 2
        assertEquals(299, three.top(5) + 101 / 2)    // the second row in the middle
        assertEquals(186 + 227, two.contentHeight)
    }

    @Test
    fun `centered in the height, rows taller than the height start at the top`() {
        assertEquals(0, placed(tiles = 15, height = 300, vertical = VerticalAlignment.CENTER).top(0))
    }

    @Test
    fun `a centered row that isn't full has as much room on either side, half a column off when it must`() {
        val grid = placed(tiles = 7, row = HorizontalAlignment.CENTER)   // two in the second row

        assertEquals(listOf(0, 205, 410, 615, 820), (0..4).map(grid::left))   // full rows as before
        assertEquals(listOf(307, 512), (5..6).map(grid::left))             // 1.5 and 2.5 columns in
        assertEquals(listOf(3, 5), (5..6).map(grid::slot))
        assertEquals(listOf(205, 410, 615), (5..7).map(placed(tiles = 8, row = HorizontalAlignment.CENTER)::left))
        assertEquals(410, placed(tiles = 1, row = HorizontalAlignment.CENTER).left(0))
    }

    // Seven tiles centered: 0-4, then 5 and 6 at slots 3 and 5, between columns.
    private val sevenCentered = placed(tiles = 7, row = HorizontalAlignment.CENTER)

    @Test
    fun `centered, down goes to the nearest tile, the left one of two as near`() {
        assertEquals(5, sevenCentered.indexToward(0, Direction.DOWN))   // slot 0: 5 is nearest
        assertEquals(5, sevenCentered.indexToward(1, Direction.DOWN))   // slot 2: 5 just right of it
        assertEquals(5, sevenCentered.indexToward(2, Direction.DOWN))   // slot 4: 5 and 6 as near
        assertEquals(6, sevenCentered.indexToward(3, Direction.DOWN))
        assertEquals(6, sevenCentered.indexToward(4, Direction.DOWN))   // the last column too
    }

    @Test
    fun `centered, up goes to the nearest tile, the left one of two as near`() {
        assertEquals(1, sevenCentered.indexToward(5, Direction.UP))   // slot 3: 1 and 2 as near
        assertEquals(2, sevenCentered.indexToward(6, Direction.UP))   // slot 5: 2 and 3 as near
    }

    @Test
    fun `centered, with the short row first, up and down cross it alike`() {
        val grid = placed(tiles = 7, row = HorizontalAlignment.CENTER, shortRowFirst = true)   // 0-1, then 2-6

        assertEquals(listOf(3, 5), (0..1).map(grid::slot))
        assertEquals(0, grid.indexToward(2, Direction.UP))
        assertEquals(1, grid.indexToward(6, Direction.UP))
        assertEquals(3, grid.indexToward(0, Direction.DOWN))   // slot 3: 3 and 4 as near
    }

    // Twelve tiles aligned right: rows 0-4, 5-9, and 10-11 in columns 3 and 4.
    private val twelveRight = placed(tiles = 12, row = HorizontalAlignment.RIGHT)

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
        assertEquals(0, placed(tiles = 12, row = HorizontalAlignment.RIGHT, shortRowFirst = true).indexToward(2, Direction.UP))
    }
}
