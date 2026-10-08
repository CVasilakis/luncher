package com.luncher.domain.layout

import com.luncher.domain.appearance.TileGeometry
import kotlin.math.roundToInt

/**
 * The layouts of the home screen's tiles for a [TileGeometry], at [pixelsPerDp]: the one place that
 * turns the geometry's dp into pixels and picks the arrangement, so where a tile is shown and
 * where the user moves it agree, and anything else that shows tiles, at its own density, agrees too.
 */
class TileLayouts(geometry: TileGeometry, pixelsPerDp: Float) {

    /** Between tiles, in pixels. */
    val gap: Int = (geometry.gapDp * pixelsPerDp).roundToInt()

    private val tileWidth: Int = (geometry.tileWidthDp * pixelsPerDp).roundToInt()

    /** [tileCount] tiles in [width] pixels; also where a moved tile goes among them. */
    fun tiles(tileCount: Int, width: Int): TileGrid =
        TileGrid(tileCount, TileGrid.columnsFor(width, tileWidth, gap), width, gap)

    /** While the user arranges apps: [shownCount] tiles above a shelf of [hiddenCount], under a label [labelHeight] tall. */
    fun shelf(shownCount: Int, hiddenCount: Int, width: Int, labelHeight: Int): ShelfLayout =
        ShelfLayout(tiles(shownCount, width), tiles(hiddenCount, width), shownCount, hiddenCount, labelHeight, gap)
}
