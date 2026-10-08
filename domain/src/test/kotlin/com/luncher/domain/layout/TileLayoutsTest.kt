package com.luncher.domain.layout

import com.luncher.domain.appearance.TileGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class TileLayoutsTest {

    // The home screen's width on a 16:9 TV, 960 dp less 2 x 48 of padding, at 1 px per dp.
    private val width = 864

    @Test
    fun `the default geometry fits five tiles in a 16-9 TV's row`() {
        val layouts = TileLayouts(TileGeometry(), pixelsPerDp = 1f)

        val tiles = layouts.tiles(tileCount = 7, width)

        assertEquals(24, layouts.gap)
        assertEquals(4, tiles.column(4))
        assertEquals(0, tiles.column(5))   // the sixth starts the second row
    }

    @Test
    fun `wider tiles make fewer columns`() {
        val tiles = TileLayouts(TileGeometry(tileWidthDp = 300), pixelsPerDp = 1f).tiles(tileCount = 7, width)

        assertEquals(0, tiles.column(3))   // (864 + 24) / (300 + 24) = 2.7: three columns
    }

    @Test
    fun `pixels follow the density`() {
        val layouts = TileLayouts(TileGeometry(), pixelsPerDp = 2f)

        val tiles = layouts.tiles(tileCount = 7, width * 2)

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

        val shelf = layouts.shelf(shownCount = 5, hiddenCount = 2, width, labelHeight = 20)

        val shown = layouts.tiles(5, width)
        assertEquals(shown.contentHeight + layouts.gap, shelf.labelTop)
        assertEquals(layouts.tiles(2, width).left(1), shelf.left(6))
        assertEquals(shown.tileWidth, shelf.tileWidth)
    }
}
