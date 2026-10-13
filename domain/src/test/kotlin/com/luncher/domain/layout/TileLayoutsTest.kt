package com.luncher.domain.layout

import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.HorizontalAlignment
import com.luncher.domain.appearance.VerticalAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
        assertEquals(8, tiles.slot(4))
        assertEquals(0, tiles.slot(5))   // the sixth starts the second row
    }

    @Test
    fun `wider tiles make fewer columns`() {
        val tiles = TileLayouts(TileGeometry(tileWidthDp = 300), pixelsPerDp = 1f).tiles(tileCount = 7, width, height)

        assertEquals(0, tiles.slot(3))   // (864 + 24) / (300 + 24) = 2.7: three columns
    }

    @Test
    fun `pixels follow the density`() {
        val layouts = TileLayouts(TileGeometry(), pixelsPerDp = 2f)

        val tiles = layouts.tiles(tileCount = 7, width * 2, height * 2)

        assertEquals(48, layouts.gap)
        assertEquals(0, tiles.slot(5))   // still five columns
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

    private fun at(
        vertical: VerticalAlignment = VerticalAlignment.TOP,
        horizontal: HorizontalAlignment = HorizontalAlignment.LEFT,
        fullBottomRow: Boolean = false,
    ) = TileLayouts(TileGeometry(verticalAlignment = vertical, horizontalAlignment = horizontal, fullBottomRow = fullBottomRow), pixelsPerDp = 1f)

    private val top = VerticalAlignment.TOP
    private val middle = VerticalAlignment.CENTER
    private val bottom = VerticalAlignment.BOTTOM
    private val left = HorizontalAlignment.LEFT
    private val center = HorizontalAlignment.CENTER
    private val right = HorizontalAlignment.RIGHT

    @Test
    fun `at the top left, rows start at the top and a short row at the left`() {
        val tiles = at(top, left).tiles(tileCount = 7, width, height)

        assertEquals(0, tiles.top(0))
        assertEquals(tiles.left(0), tiles.left(5))
    }

    @Test
    fun `on the right, a short row lines up at the right`() {
        val tiles = at(top, right).tiles(tileCount = 7, width, height)

        assertEquals(tiles.left(4), tiles.left(6))
        assertEquals(0, tiles.top(0))
    }

    @Test
    fun `at the bottom, the rows end at the bottom of the space`() {
        val tiles = at(bottom, left).tiles(tileCount = 7, width, height)

        assertEquals(height, tiles.top(6) + tiles.tileHeight)
        assertEquals(tiles.left(0), tiles.left(5))   // the short row still last, at the left
    }

    @Test
    fun `centered, the rows are in the middle of the space and a short row in the middle of the width`() {
        val tiles = at(middle, center).tiles(tileCount = 7, width, height)

        val above = tiles.top(0)
        val below = height - (tiles.top(5) + tiles.tileHeight)
        assertTrue("$above, $below", above - below in 0..1)
        val fromLeft = tiles.left(5) - tiles.left(0)
        val fromRight = tiles.left(4) - tiles.left(6)
        assertTrue("$fromLeft, $fromRight", abs(fromLeft - fromRight) <= 1)   // half a step rounds down
    }

    @Test
    fun `a full bottom row puts the short row first, only centered or at the bottom`() {
        val atBottom = at(bottom, right, fullBottomRow = true).tiles(tileCount = 7, width, height)
        val centered = at(middle, right, fullBottomRow = true).tiles(tileCount = 7, width, height)
        val atTop = at(top, right, fullBottomRow = true).tiles(tileCount = 7, width, height)

        assertEquals(atBottom.top(0), atBottom.top(1))
        assertTrue(atBottom.top(2) > atBottom.top(1))    // the first row has two tiles...
        assertEquals(atBottom.left(5), atBottom.left(0))   // ...at the right, above the last two
        assertTrue(centered.top(2) > centered.top(1))
        assertEquals(atTop.top(0), atTop.top(4))         // at the top, the first row is full
    }

    // A move and a tile's place must agree, or the held tile would jump.
    @Test
    fun `moves follow the alignment, whatever the height`() {
        val layouts = at(middle, center, fullBottomRow = true)

        val tiles = layouts.tiles(tileCount = 7, width, height)
        val moves = layouts.moves(tileCount = 7, width)

        for (index in 0 until 7) assertEquals(tiles.slot(index), moves.slot(index))
        assertEquals(tiles.indexToward(1, Direction.DOWN), moves.indexToward(1, Direction.DOWN))
    }

    @Test
    fun `at the bottom, the shown tiles, label and shelf end at the bottom of the space together`() {
        val shelf = at(bottom, left).shelf(shownCount = 5, hiddenCount = 2, width, height, labelHeight = 20)

        assertEquals(height, shelf.contentHeight)
        assertEquals(height - shelf.tileHeight, shelf.top(6))
        assertEquals(shelf.labelTop - layouts().gap - shelf.tileHeight, shelf.top(0))
    }

    @Test
    fun `centered, the shown tiles, label and shelf are in the middle of the space together`() {
        val shelf = at(middle).shelf(shownCount = 5, hiddenCount = 2, width, height, labelHeight = 20)
        val atTop = at(top).shelf(shownCount = 5, hiddenCount = 2, width, height, labelHeight = 20)

        val down = shelf.top(0)
        assertEquals((height - atTop.contentHeight) / 2, down)
        assertEquals(atTop.top(6) + down, shelf.top(6))
    }

    @Test
    fun `the focus follows the alignment, and crosses to the shelf`() {
        val layouts = at(middle, center)

        val focus = layouts.focus(tileCount = 7, width)
        assertEquals(layouts.moves(7, width).indexToward(2, Direction.DOWN), focus.toward(2, Direction.DOWN))
        val shelf = layouts.shelfFocus(shownCount = 5, hiddenCount = 2, width)
        assertEquals(5, shelf.toward(2, Direction.DOWN))   // a full row above two centered hidden tiles
        assertEquals(2, layouts.shelfFocus(shownCount = 5, hiddenCount = 0, width).toward(2, Direction.DOWN))   // stays
    }

    @Test
    fun `centered, the shelf's label is centered over it`() {
        val shelf = at(top, center).shelf(shownCount = 5, hiddenCount = 1, width, height, labelHeight = 20)

        assertEquals((width - 50) / 2, shelf.labelLeft(labelWidth = 50))
    }

    @Test
    fun `on the right, the shelf's label and empty slot line up at the right`() {
        val layouts = at(top, right)
        val shelf = layouts.shelf(shownCount = 5, hiddenCount = 0, width, height, labelHeight = 20)

        val rightEdge = layouts.tiles(5, width, height).let { it.left(4) + it.tileWidth }
        assertEquals(rightEdge, shelf.left(5) + shelf.tileWidth)   // the empty slot
        assertEquals(rightEdge - 50, shelf.labelLeft(labelWidth = 50))
        assertEquals(layouts.tiles(5, width, height).left(0), at(top, left).shelf(5, 0, width, height, 20).labelLeft(50))
    }

    private fun layouts() = TileLayouts(TileGeometry(), pixelsPerDp = 1f)
}
