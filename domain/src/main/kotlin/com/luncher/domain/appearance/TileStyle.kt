package com.luncher.domain.appearance

/**
 * How each tile is drawn: a different one only redraws the tiles, with the images and places they
 * have.
 */
data class TileStyle(
    /** The tiles' corners, and their focus frame's. */
    val corners: Corners = Corners.Radius(12),
)
