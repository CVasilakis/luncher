package com.luncher.domain.layout

import com.luncher.domain.appearance.HorizontalAlignment
import org.junit.Assert.assertEquals
import org.junit.Test

class TileFocusTest {

    // Rows of five, a short row centered: with seven tiles, 0-4, then 5 and 6 at slots 3 and 5,
    // half a column off the first row's.
    private fun grid(tiles: Int) = TileGrid(tiles, columns = 5, width = 1000, gap = 25, HorizontalAlignment.CENTER)

    private fun seven() = TileFocus(grid(7), shownCount = 7)

    @Test
    fun `left and right go along the row`() {
        assertEquals(1, seven().toward(0, Direction.RIGHT))
        assertEquals(3, seven().toward(4, Direction.LEFT))
        assertEquals(6, seven().toward(5, Direction.RIGHT))
        assertEquals(5, seven().toward(6, Direction.LEFT))
    }

    @Test
    fun `up and down go to the next row's nearest tile, the left one of two as near`() {
        assertEquals(5, seven().toward(2, Direction.DOWN))   // 5 and 6 as near
        assertEquals(6, seven().toward(4, Direction.DOWN))
        assertEquals(1, seven().toward(5, Direction.UP))     // 1 and 2 as near
    }

    @Test
    fun `the opposite key goes back to the tile the focus came from`() {
        val focus = seven()

        assertEquals(6, focus.toward(4, Direction.DOWN))
        assertEquals(4, focus.toward(6, Direction.UP))       // not 2, the nearest
        assertEquals(6, focus.toward(4, Direction.DOWN))
        assertEquals(4, focus.toward(6, Direction.UP))
    }

    @Test
    fun `the way back is only from the tile the move landed on`() {
        val focus = seven()

        focus.toward(4, Direction.DOWN)                    // to 6, and from there elsewhere
        assertEquals(1, focus.toward(5, Direction.UP))
    }

    @Test
    fun `a move left or right forgets the way back`() {
        val focus = seven()

        focus.toward(4, Direction.DOWN)                    // to 6
        assertEquals(5, focus.toward(6, Direction.LEFT))
        assertEquals(6, focus.toward(5, Direction.RIGHT))
        assertEquals(2, focus.toward(6, Direction.UP))     // the nearest, not back to 4
    }

    @Test
    fun `a key that moves nothing keeps the way back`() {
        val focus = seven()

        focus.toward(4, Direction.DOWN)                    // to 6, in the last row
        assertEquals(6, focus.toward(6, Direction.DOWN))
        assertEquals(6, focus.toward(6, Direction.RIGHT))
        assertEquals(4, focus.toward(6, Direction.UP))
    }

    @Test
    fun `after going back, the same key goes on by the nearest tile`() {
        val focus = TileFocus(grid(12), shownCount = 12)   // 0-4, 5-9, then 10 and 11 centered

        assertEquals(11, focus.toward(9, Direction.DOWN))
        assertEquals(9, focus.toward(11, Direction.UP))
        assertEquals(4, focus.toward(9, Direction.UP))
    }

    @Test
    fun `forgets the way back when the tiles move`() {
        val focus = seven()

        focus.toward(4, Direction.DOWN)
        focus.forget()

        assertEquals(2, focus.toward(6, Direction.UP))
    }

    @Test
    fun `leaves the tiles up from the first row, and stays at every other edge`() {
        assertEquals(null, seven().toward(1, Direction.UP))
        assertEquals(6, seven().toward(6, Direction.DOWN))
        assertEquals(4, seven().toward(4, Direction.RIGHT))   // not on to the next row
        assertEquals(5, seven().toward(5, Direction.LEFT))    // nor back to the one above
        assertEquals(6, seven().toward(6, Direction.RIGHT))
        assertEquals(0, seven().toward(0, Direction.LEFT))
    }

    // While arranging: five shown tiles, a full row, above two hidden ones, centered.
    private fun shelf() = TileFocus(grid(5), shownCount = 5, shelf = grid(2))

    @Test
    fun `crosses between the shown tiles' last row and the shelf's first`() {
        assertEquals(6, shelf().toward(4, Direction.DOWN))   // the shelf's second tile, nearest
        assertEquals(5, shelf().toward(0, Direction.DOWN))
        assertEquals(1, shelf().toward(5, Direction.UP))     // 1 and 2 as near
    }

    @Test
    fun `the opposite key goes back across the shelf too`() {
        val focus = shelf()

        assertEquals(6, focus.toward(4, Direction.DOWN))
        assertEquals(4, focus.toward(6, Direction.UP))
    }

    @Test
    fun `left and right never cross between the shown tiles and the shelf`() {
        assertEquals(4, shelf().toward(4, Direction.RIGHT))
        assertEquals(5, shelf().toward(5, Direction.LEFT))
        assertEquals(6, shelf().toward(5, Direction.RIGHT))
    }

    @Test
    fun `stays down with nothing on the shelf, and leaves up with nothing shown above it`() {
        assertEquals(2, TileFocus(grid(5), shownCount = 5, shelf = grid(0)).toward(2, Direction.DOWN))
        assertEquals(null, TileFocus(grid(0), shownCount = 0, shelf = grid(2)).toward(0, Direction.UP))
    }
}
