package com.luncher.domain.appearance

/**
 * How the home screen's top bar is arranged: changing it moves the bar's items, not the tiles.
 * [com.luncher.domain.layout.topBarLayout] decides from it where each item goes.
 */
data class TopBarAppearance(
    /** The side of the bar the clock is on; the settings entry is on the other. */
    val clockSide: Side = Side.LEFT,
    /** The side of the date the time is on. */
    val timeSide: Side = Side.LEFT,
)
