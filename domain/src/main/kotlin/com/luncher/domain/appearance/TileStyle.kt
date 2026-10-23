package com.luncher.domain.appearance

/**
 * How each tile is drawn: a different one only redraws the tiles, with the images and places they
 * have.
 */
data class TileStyle(
    /** The tiles' corners, and their focus frame's and shadow's. */
    val corners: Corners = Corners.Radius(16),
    /**
     * Whether the focused tile has a frame, in the accent color, inside its edge; without one, it
     * casts a soft shadow, as if lifted off the screen. It zooms as much either way.
     */
    val focusFrame: Boolean = true,
)
