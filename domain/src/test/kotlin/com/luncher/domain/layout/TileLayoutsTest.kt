package com.luncher.domain.layout

import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.TilePlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileLayoutsTest {

    // The home screen's width on a 16:9 TV, 960 dp less 2 x 48 of padding, at 1 px per dp.
    private val width = 864

    // The space below the top bar, 540 dp less the bar and the paddings.
    private val height = 380

    @Test
    fun `the default geometry fits five tiles in a 16-9 TV's row`() {
        val layouts = TileLayouts(TileGeometry(), pixelsPerDp = 1f)

        val tiles = layouts.tiles(tileCount = 7, width, height)

        assertEquals(24, layouts.gap)
        assertEquals(4, tiles.column(4))
        assertEquals(0, tiles.column(5))   // the sixth starts the second row
    }

    @Test
    fun `wider tiles make fewer columns`() {
        val tiles = TileLayouts(TileGeometry(tileWidthDp = 300), pixelsPerDp = 1f).tiles(tileCount = 7, width, height)

        assertEquals(0, tiles.column(3))   // (864 + 24) / (300 + 24) = 2.7: three columns
    }

    @Test
    fun `pixels follow the density`() {
        val layouts = TileLayouts(TileGeometry(), pixelsPerDp = 2f)

        val tiles = layouts.tiles(tileCount = 7, width * 2, height * 2)

        assertEquals(48, layouts.gap)
        assertEquals(0, tiles.column(5))   // still five columns
        assertEquals(307, tiles.tileWidth)   // (1728 - 4 x 48) / 5, where 1 px per dp makes 153
    }

    // As Android's dimension resources round: to the nearest pixel.
    @Test
    fun `rounds to the nearest pixel`() {
        assertEquals(32, TileLayouts(TileGeometry(gapDp = 24), pixelsPerDp = 1.33125f).gap)   // 31.95, tvdpi
        assertEquals(31, TileLayouts(TileGeometry(gapDp = 23), pixelsPerDp = 1.33125f).gap)   // 30.62
    }

    @Test
    fun `the shelf's tiles are laid out like the shown ones, below them`() {
        val layouts = TileLayouts(TileGeometry(), pixelsPerDp = 1f)

        val shelf = layouts.shelf(shownCount = 5, hiddenCount = 2, width, height, labelHeight = 20)

        val shown = layouts.tiles(5, width, height)
        assertEquals(shown.contentHeight + layouts.gap, shelf.labelTop)
        assertEquals(layouts.tiles(2, width, height).left(1), shelf.left(6))
        assertEquals(shown.tileWidth, shelf.tileWidth)
    }

    private fun at(placement: TilePlacement, fullBottomRow: Boolean = false) =
        TileLayouts(TileGeometry(placement = placement, fullBottomRow = fullBottomRow), pixelsPerDp = 1f)

    @Test
    fun `at the top left, rows start at the top and a short row at the left`() {
        val tiles = at(TilePlacement.TOP_LEFT).tiles(tileCount = 7, width, height)

        assertEquals(0, tiles.top(0))
        assertEquals(tiles.left(0), tiles.left(5))
    }

    @Test
    fun `on the right, a short row lines up at the right`() {
        val tiles = at(TilePlacement.TOP_RIGHT).tiles(tileCount = 7, width, height)

        assertEquals(tiles.left(4), tiles.left(6))
        assertEquals(0, tiles.top(0))
    }

    @Test
    fun `at the bottom, the rows end at the bottom of the space`() {
        val tiles = at(TilePlacement.BOTTOM_LEFT).tiles(tileCount = 7, width, height)

        assertEquals(height, tiles.top(6) + tiles.tileHeight)
        assertEquals(tiles.left(0), tiles.left(5))   // the short row still last, at the left
    }

    @Test
    fun `a full bottom row puts the short row first, only at the bottom`() {
        val bottom = at(TilePlacement.BOTTOM_RIGHT, fullBottomRow = true).tiles(tileCount = 7, width, height)
        val top = at(TilePlacement.TOP_RIGHT, fullBottomRow = true).tiles(tileCount = 7, width, height)

        assertEquals(bottom.top(0), bottom.top(1))
        assertTrue(bottom.top(2) > bottom.top(1))    // the first row has two tiles...
        assertEquals(bottom.left(5), bottom.left(0))   // ...at the right, above the last two
        assertEquals(top.top(0), top.top(4))         // at the top, the first row is full
    }

    // A move and a tile's place must agree, or the held tile would jump.
    @Test
    fun `moves follow the placement, whatever the height`() {
        val layouts = at(TilePlacement.BOTTOM_RIGHT, fullBottomRow = true)

        val tiles = layouts.tiles(tileCount = 7, width, height)
        val moves = layouts.moves(tileCount = 7, width)

        for (index in 0 until 7) assertEquals(tiles.column(index), moves.column(index))
        assertEquals(tiles.indexToward(1, Direction.DOWN), moves.indexToward(1, Direction.DOWN))
    }

    @Test
    fun `at the bottom, the shown tiles, label and shelf end at the bottom of the space together`() {
        val shelf = at(TilePlacement.BOTTOM_LEFT).shelf(shownCount = 5, hiddenCount = 2, width, height, labelHeight = 20)

        assertEquals(height, shelf.contentHeight)
        assertEquals(height - shelf.tileHeight, shelf.top(6))
        assertEquals(shelf.labelTop - layouts().gap - shelf.tileHeight, shelf.top(0))
    }

    @Test
    fun `on the right, the shelf's label and empty slot line up at the right`() {
        val layouts = at(TilePlacement.TOP_RIGHT)
        val shelf = layouts.shelf(shownCount = 5, hiddenCount = 0, width, height, labelHeight = 20)

        val rightEdge = layouts.tiles(5, width, height).let { it.left(4) + it.tileWidth }
        assertEquals(rightEdge, shelf.left(5) + shelf.tileWidth)   // the empty slot
        assertEquals(rightEdge - 50, shelf.labelLeft(labelWidth = 50))
        assertEquals(layouts.tiles(5, width, height).left(0), at(TilePlacement.TOP_LEFT).shelf(5, 0, width, height, 20).labelLeft(50))
    }

    private fun layouts() = TileLayouts(TileGeometry(), pixelsPerDp = 1f)
}
