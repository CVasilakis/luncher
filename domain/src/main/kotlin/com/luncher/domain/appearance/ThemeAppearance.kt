package com.luncher.domain.appearance

/**
 * How the menus and the settings look, and the accent color of everything: Luncher's theme. The
 * accent shows on the home screen too (its focus frames); the mode, dark or light, only in the
 * menus and settings, as the home screen is drawn on its wallpaper. What colors they make:
 * [colorRoles].
 */
data class ThemeAppearance(
    val accent: AccentColor = AccentColor.ORANGE,
    val mode: ThemeMode = ThemeMode.DARK,
)

/** Whether the menus and the settings are dark or light. */
enum class ThemeMode { DARK, LIGHT }
