package com.luncher.domain.layout

import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.VerticalAlignment
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
    private val vertical = geometry.verticalAlignment
    private val horizontal = geometry.horizontalAlignment
    private val shortRowFirst = vertical != VerticalAlignment.TOP && geometry.fullBottomRow

    /** [tileCount] tiles in [width] by [height] pixels; also where a moved tile goes among them. */
    fun tiles(tileCount: Int, width: Int, height: Int): TileGrid = grid(tileCount, width, height, vertical)

    /** Where a moved tile goes among [tileCount] tiles [width] pixels wide: as [tiles], whatever the height. */
    fun moves(tileCount: Int, width: Int): TileMoves = grid(tileCount, width, height = 0, VerticalAlignment.TOP)

    /** Where the D-pad takes the focus among [tileCount] tiles [width] pixels wide, laid out as [tiles]. */
    fun focus(tileCount: Int, width: Int): TileFocus = TileFocus(moves(tileCount, width), tileCount)

    /** Where the D-pad takes the focus among [shownCount] tiles and a shelf of [hiddenCount], laid out as [shelf]. */
    fun shelfFocus(shownCount: Int, hiddenCount: Int, width: Int): TileFocus =
        TileFocus(moves(shownCount, width), shownCount, moves(hiddenCount, width))

    /**
     * While the user arranges apps: [shownCount] tiles above a shelf of [hiddenCount], under a
     * label [labelHeight] tall, in [width] by [height] pixels. Placed as a whole, as [tiles] are.
     */
    fun shelf(shownCount: Int, hiddenCount: Int, width: Int, height: Int, labelHeight: Int): ShelfLayout =
        ShelfLayout(
            shown = grid(shownCount, width, height = 0, VerticalAlignment.TOP),
            hidden = grid(maxOf(hiddenCount, 1), width, height = 0, VerticalAlignment.TOP),
            shownCount, hiddenCount, labelHeight, gap, height, vertical, labelAlignment = horizontal,
        )

    private fun grid(tileCount: Int, width: Int, height: Int, vertical: VerticalAlignment) =
        TileGrid(
            tileCount, TileGrid.columnsFor(width, tileWidth, gap), width, gap,
            horizontal, shortRowFirst, height, vertical,
        )
}
