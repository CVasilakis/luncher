package com.luncher.domain.appearance

/** How round a tile's corners are: a radius, or ends as round as the tile is high (a pill). */
sealed interface Corners {

    /** A radius of [dp]; 0 is square. */
    data class Radius(val dp: Int) : Corners

    /** Straight top and bottom, the ends half circles, whatever the tile's size. */
    data object Pill : Corners

    /**
     * The radius in pixels for a tile [tileHeight] pixels high, at [pixelsPerDp]: never more than
     * half its height, where the ends are round.
     */
    fun pixels(tileHeight: Int, pixelsPerDp: Float): Float {
        val half = tileHeight / 2f
        return when (this) {
            is Radius -> minOf(dp * pixelsPerDp, half)
            Pill -> half
        }
    }
}
