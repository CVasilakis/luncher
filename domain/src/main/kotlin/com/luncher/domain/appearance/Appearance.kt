package com.luncher.domain.appearance

/**
 * How the home screen looks, as far as the user can change it: one part per part of the home
 * screen that draws it, each holding values that cost the same to apply, so a consumer takes only
 * its part and compares it with `==` (docs/ARCHITECTURE.md#appearance). The defaults are the home
 * screen without settings.
 */
data class Appearance(
    val tileGeometry: TileGeometry = TileGeometry(),
)
