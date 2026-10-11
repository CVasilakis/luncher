package com.luncher.domain.appearance

/**
 * The corner of the space below the top bar that the tiles gather in. The apps keep their order,
 * left to right and top to bottom, and rows are as wide as the space; so a row that isn't full
 * lines up on the corner's side, and the rows sit at its top or bottom. Tiles that don't fit the
 * height start at the top whatever the corner, and scroll.
 */
enum class TilePlacement(val atBottom: Boolean, val atRight: Boolean) {
    TOP_LEFT(atBottom = false, atRight = false),
    TOP_RIGHT(atBottom = false, atRight = true),
    BOTTOM_LEFT(atBottom = true, atRight = false),
    BOTTOM_RIGHT(atBottom = true, atRight = true),
}
