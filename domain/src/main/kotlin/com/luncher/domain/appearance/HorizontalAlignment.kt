package com.luncher.domain.appearance

/**
 * Where a row of tiles that isn't full goes in the width: at the left, centered, or at the right,
 * the screen's in every language. Full rows are as wide as the space, so only that row moves; a
 * centered one keeps the gaps of the others, so it's half a tile off their columns when the
 * tiles it lacks are an odd number.
 */
enum class HorizontalAlignment { LEFT, CENTER, RIGHT }
