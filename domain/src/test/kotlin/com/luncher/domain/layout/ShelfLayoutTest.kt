package com.luncher.domain.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShelfLayoutTest {

    // Grids of 5 columns in 1000 px with 25 px gaps: tiles 180 x 101, rows 126 px apart.
    private fun grid(tiles: Int) = TileGrid(tiles, columns = 5, width = 1000, gap = 25)

    private fun shelf(shown: Int, hidden: Int) =
        ShelfLayout(grid(shown), grid(hidden), shown, hidden, labelHeight = 40, gap = 30)

    @Test
    fun `puts the shown tiles first, then the label, then the hidden tiles`() {
        val layout = shelf(shown = 7, hidden = 2)   // two rows shown: 227 px

        assertEquals(227 + 30, layout.labelTop)
        assertEquals(227 + 30 + 40 + 30, layout.shelfTop)
        assertEquals(126, layout.top(5))            // second shown row
        assertEquals(layout.shelfTop, layout.top(7))
        assertEquals(0, layout.left(7))             // the shelf starts at the left
        assertEquals(205, layout.left(8))
    }

    @Test
    fun `covers the shown tiles, the label and the shelf`() {
        assertEquals(227 + 30 + 40 + 30 + 101, shelf(shown = 7, hidden = 2).contentHeight)
        assertEquals(1000, shelf(shown = 7, hidden = 2).contentWidth)
    }

    @Test
    fun `keeps an empty slot for hiding when nothing is hidden`() {
        val layout = shelf(shown = 3, hidden = 0)

        assertEquals(3, layout.emptySlot)
        assertEquals(layout.shelfTop, layout.top(3))
        assertEquals(layout.shelfTop + 101, layout.contentHeight)
        assertNull(shelf(shown = 3, hidden = 1).emptySlot)
    }

    @Test
    fun `starts with the label when every app is hidden`() {
        val layout = shelf(shown = 0, hidden = 2)

        assertEquals(0, layout.labelTop)
        assertEquals(40 + 30, layout.top(0))
    }
}
