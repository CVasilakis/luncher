package com.luncher.domain.appearance

/**
 * How each tile is drawn, in dp: a different one only redraws the tiles, with the images and
 * places they have.
 */
data class TileStyle(
    /** The radius of a tile's corners, and of its focus frame's; 0 is square. */
    val cornerRadiusDp: Int = 0,
)
