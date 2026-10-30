package com.luncher.domain.appearance

/**
 * The colors of the menus and the settings, by what each is for, in a theme ([colorRoles]); and
 * the home screen's accent. Each `on…` color is for text and icons on the color it names, and
 * reads on it ([ColorRoles] are checked for contrast in every theme). ARGB, as Android takes them.
 */
data class ColorRoles(
    /** Behind a screen of its own (the appearance and background editors, Add from folder). */
    val screen: Int,
    /** A panel or a dialog. */
    val surface: Int,
    /** On a panel: a field, a list's container, a tonal button. */
    val surfaceHigh: Int,
    /** A chip, a thumbnail not drawn yet, a track. */
    val surfaceHighest: Int,
    val onSurface: Int,
    /** Secondary text and icons: a supporting line, a hint, a label. */
    val onSurfaceVariant: Int,
    /** An outlined field's edge. */
    val outline: Int,
    val outlineVariant: Int,
    /** The accent, in the menus and settings: a filled button, what's chosen, a check. */
    val primary: Int,
    val onPrimary: Int,
    /** A badge, a tonal mark of the accent. */
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    /** Deleting, and what went wrong. */
    val error: Int,
    val onError: Int,
    val errorContainer: Int,
    val onErrorContainer: Int,
    /** The focused entry or button: light on a dark theme, dark on a light one, so it stands out from afar. */
    val inverseSurface: Int,
    val inverseOnSurface: Int,
    /**
     * The focused entry while OK is held on it: a step away from [inverseSurface] that shows on it
     * whatever the mode (a little darker where that is light, a little lighter where it's dark),
     * and still reads under [inverseOnSurface].
     */
    val inverseSurfacePressed: Int,
    /** The accent on the home screen, which is always dark: a focused tile's frame. */
    val homeAccent: Int,
)

/** The colors [theme] makes: the accent's tones in the menus and settings, of the theme's mode. */
fun colorRoles(theme: ThemeAppearance): ColorRoles {
    val accent = theme.accent
    return when (theme.mode) {
        ThemeMode.DARK -> ColorRoles(
            screen = 0xFF101418.toInt(),
            surface = 0xFF1C232B.toInt(),
            surfaceHigh = 0xFF262D36.toInt(),
            surfaceHighest = 0xFF313944.toInt(),
            onSurface = 0xFFEEF1F5.toInt(),
            onSurfaceVariant = 0xFFA9B3BF.toInt(),
            outline = 0xFF6B7682.toInt(),
            outlineVariant = 0xFF343C46.toInt(),
            primary = accent.tone80,
            onPrimary = accent.tone20,
            primaryContainer = accent.tone30,
            onPrimaryContainer = accent.tone90,
            error = 0xFFFFB4AB.toInt(),
            onError = 0xFF690005.toInt(),
            errorContainer = 0xFF93000A.toInt(),
            onErrorContainer = 0xFFFFDAD6.toInt(),
            inverseSurface = 0xFFEEF1F5.toInt(),
            inverseOnSurface = 0xFF1C232B.toInt(),
            inverseSurfacePressed = 0xFFC4CAD2.toInt(),
            homeAccent = accent.vivid,
        )
        ThemeMode.LIGHT -> ColorRoles(
            screen = 0xFFF3F5F8.toInt(),
            surface = 0xFFFFFFFF.toInt(),
            surfaceHigh = 0xFFE8ECF1.toInt(),
            surfaceHighest = 0xFFDCE2E9.toInt(),
            onSurface = 0xFF161B22.toInt(),
            onSurfaceVariant = 0xFF4F5A66.toInt(),
            outline = 0xFF7D8894.toInt(),
            outlineVariant = 0xFFCDD4DC.toInt(),
            primary = accent.tone40,
            onPrimary = 0xFFFFFFFF.toInt(),
            primaryContainer = accent.tone90,
            onPrimaryContainer = accent.tone10,
            error = 0xFFBA1A1A.toInt(),
            onError = 0xFFFFFFFF.toInt(),
            errorContainer = 0xFFFFDAD6.toInt(),
            onErrorContainer = 0xFF410002.toInt(),
            inverseSurface = 0xFF1F252D.toInt(),
            inverseOnSurface = 0xFFEEF1F5.toInt(),
            inverseSurfacePressed = 0xFF4A535E.toInt(),
            homeAccent = accent.vivid,
        )
    }
}
