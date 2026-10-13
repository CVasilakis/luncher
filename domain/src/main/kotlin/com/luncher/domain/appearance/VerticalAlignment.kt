package com.luncher.domain.appearance

/**
 * Where the rows of tiles go in the height of the space below the top bar: at its top, centered
 * in it (with three rows, the second in the middle; with two, the gap between them), or at its
 * bottom. Rows that don't fit the height start at the top whatever the alignment, and scroll.
 */
enum class VerticalAlignment { TOP, CENTER, BOTTOM }
