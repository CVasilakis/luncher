package com.luncher.domain.appearance

/**
 * Where the home screen's tiles go and how big they are, in dp: a different one lays the tiles out
 * again, and draws their images anew when their size changes. [com.luncher.domain.layout.TileLayouts]
 * turns it into pixels and layouts.
 */
data class TileGeometry(
    /**
     * About how wide a tile is: as many fit in a row as make the nearest whole number, so a 16:9
     * TV's 960 dp, less the home screen's padding, has five. Fewer on a narrower screen, more on a
     * wider one or at a lower density.
     */
    val tileWidthDp: Int = 154,
    /** Between tiles; more than a tile grows when it zooms, so neighbors never overlap. */
    val gapDp: Int = 24,
)
