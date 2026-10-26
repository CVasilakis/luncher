package com.luncher.domain.layout

import com.luncher.domain.appearance.AppName
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.VerticalAlignment
import kotlin.math.roundToInt

/**
 * The layouts of the home screen's tiles for a [TileGeometry], at [pixelsPerDp]: the one place that
 * turns the geometry's dp into pixels and picks the arrangement, so where a tile is shown and
 * where the user moves it agree, and anything else that shows tiles, at its own density, agrees too.
 *
 * [nameHeight] is how much room, in pixels, the focused tile's name takes below the tile, zoomed
 * as the tile is: the view that draws the name measures its text, as the domain can't. The rows
 * leave it that room only while the geometry shows names ([AppName.WHEN_FOCUSED],
 * [AppName.ALWAYS]): the same room either way, as a tile's name at rest takes less.
 */
class TileLayouts(geometry: TileGeometry, pixelsPerDp: Float, nameHeight: Int = 0) {

    /** Between tiles, in pixels. */
    val gap: Int = (geometry.gapDp * pixelsPerDp).roundToInt()

    /**
     * Below each row, the last one too, the room for the tiles' names, the focused one's zoomed,
     * in pixels: none while names don't show. A view scrolling a tile into sight shows this much below it too.
     */
    val nameRoom: Int = when (geometry.appName) {
        AppName.OFF -> 0
        AppName.WHEN_FOCUSED, AppName.ALWAYS -> nameHeight
    }

    private val tileWidth: Int = (geometry.tileWidthDp * pixelsPerDp).roundToInt()
    private val vertical = geometry.verticalAlignment
    private val horizontal = geometry.horizontalAlignment
    private val shortRowFirst = vertical != VerticalAlignment.TOP && geometry.fullBottomRow

    /** [tileCount] tiles in [width] by [height] pixels; also where a moved tile goes among them. */
    fun tiles(tileCount: Int, width: Int, height: Int): TileGrid = grid(tileCount, width, height, vertical)

    /**
     * The size of every tile in a space [width] pixels wide, however many tiles there are and
     * however tall the space: what a tile's picture is prepared at, by the home screen and by the
     * tile banner picker alike, so the two agree to the pixel.
     */
    fun tileSize(width: Int): TileSize = tiles(tileCount = 0, width, height = 0).let { TileSize(it.tileWidth, it.tileHeight) }

    /** Where a moved tile goes among [tileCount] tiles [width] pixels wide: as [tiles], whatever the height. */
    fun moves(tileCount: Int, width: Int): TileMoves = grid(tileCount, width, height = 0, VerticalAlignment.TOP)

    /** Where the D-pad takes the focus among [tileCount] tiles [width] pixels wide, laid out as [tiles]. */
    fun focus(tileCount: Int, width: Int): TileFocus = TileFocus(moves(tileCount, width), tileCount)

    private fun grid(tileCount: Int, width: Int, height: Int, vertical: VerticalAlignment) =
        TileGrid(
            tileCount, TileGrid.columnsFor(width, tileWidth, gap), width, gap,
            horizontal, shortRowFirst, height, vertical, nameRoom,
        )
}
