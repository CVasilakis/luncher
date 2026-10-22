package com.luncher.domain.appearance

/**
 * How the home screen looks, as far as the user can change it, and Luncher's theme: one part per
 * part of the screens that draws it, each holding values that cost the same to apply, so a consumer
 * takes only its part and compares it with `==` (docs/ARCHITECTURE.md#appearance). The defaults
 * are the screens without settings. What the user can change of it, and to which values:
 * [appearanceOptions].
 */
data class Appearance(
    val tileGeometry: TileGeometry = TileGeometry(),
    val tileStyle: TileStyle = TileStyle(),
    val topBar: TopBarAppearance = TopBarAppearance(),
    val background: Background = Background(),
    val theme: ThemeAppearance = ThemeAppearance(),
)
