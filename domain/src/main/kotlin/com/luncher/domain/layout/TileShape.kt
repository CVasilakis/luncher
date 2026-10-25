package com.luncher.domain.layout

import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.TileGeometry

/**
 * A tile's shape, an Android TV banner's (320 x 180 dp): [WIDTH] by [HEIGHT]. Every tile has it,
 * whatever its size ([TileGrid]), and so does whatever shows a tile elsewhere (the preview of a
 * tile's picture), which takes it from here.
 */
object TileShape {
    const val WIDTH = 16
    const val HEIGHT = 9

    /**
     * The corners' radius, in px, of a tile drawn [height] px high in place of a home screen tile of
     * [geometry] with [corners]: as round for its size as that tile is, so it has the tile's shape
     * at any scale. The home screen's tile is taken as [TileGeometry.tileWidthDp] wide, about what
     * it is.
     */
    fun cornerRadius(corners: Corners, geometry: TileGeometry, height: Int): Float {
        val tileHeightDp = geometry.tileWidthDp * HEIGHT / WIDTH.toFloat()
        // As many px per dp as make that tile [height] px high.
        return corners.pixels(height, height / tileHeightDp)
    }
}
