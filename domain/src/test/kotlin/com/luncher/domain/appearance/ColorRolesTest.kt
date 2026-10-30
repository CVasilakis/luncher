package com.luncher.domain.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ColorRolesTest {

    private val themes = AccentColor.entries.flatMap { accent -> ThemeMode.entries.map { ThemeAppearance(accent, it) } }

    // WCAG's contrast: 4.5 for text, 3 for what isn't (a frame, an icon beside a text).
    @Test
    fun `every text reads on the color it's drawn on, in every theme`() {
        for (theme in themes) {
            val roles = colorRoles(theme)
            fun text(on: Int, color: Int, name: String) = assertContrast(theme, name, color, on, 4.5)
            text(roles.surface, roles.onSurface, "onSurface on surface")
            text(roles.surfaceHigh, roles.onSurface, "onSurface on surfaceHigh")
            text(roles.surfaceHighest, roles.onSurface, "onSurface on surfaceHighest")
            text(roles.screen, roles.onSurface, "onSurface on screen")
            text(roles.surface, roles.onSurfaceVariant, "onSurfaceVariant on surface")
            text(roles.screen, roles.onSurfaceVariant, "onSurfaceVariant on screen")
            text(roles.primary, roles.onPrimary, "onPrimary on primary")
            text(roles.primaryContainer, roles.onPrimaryContainer, "onPrimaryContainer on primaryContainer")
            text(roles.error, roles.onError, "onError on error")
            text(roles.errorContainer, roles.onErrorContainer, "onErrorContainer on errorContainer")
            text(roles.inverseSurface, roles.inverseOnSurface, "inverseOnSurface on inverseSurface")
            text(roles.inverseSurfacePressed, roles.inverseOnSurface, "inverseOnSurface on inverseSurfacePressed")
            text(roles.surface, roles.error, "error on surface")
            text(roles.screen, roles.error, "error on screen")
            // The chosen picture's name, and the check beside it.
            text(roles.surface, roles.primary, "primary on surface")
            text(roles.screen, roles.primary, "primary on screen")
        }
    }

    // The focused entry is the inverse of the panel, so it stands out from afar whatever the accent.
    @Test
    fun `the focused entry stands out from its panel`() {
        for (theme in themes) {
            val roles = colorRoles(theme)
            assertContrast(theme, "inverseSurface on surface", roles.inverseSurface, roles.surface, 7.0)
        }
    }

    // OK held on an entry: its fill changes by a step that shows from afar, in a light theme as in a dark one.
    @Test
    fun `the pressed entry's fill differs from the focused one's by a step that shows`() {
        for (theme in themes) {
            val roles = colorRoles(theme)
            assertContrast(theme, "inverseSurfacePressed against inverseSurface", roles.inverseSurfacePressed, roles.inverseSurface, 1.4)
            // Still the focus's own kind of fill: it stands out from its panel.
            assertContrast(theme, "inverseSurfacePressed on surface", roles.inverseSurfacePressed, roles.surface, 3.0)
        }
    }

    // The home screen is always dark: its focus frame is the accent's vivid tone, on the plain background.
    @Test
    fun `the home screen's accent shows on its background`() {
        for (accent in AccentColor.entries) {
            val roles = colorRoles(ThemeAppearance(accent))
            assertContrast(ThemeAppearance(accent), "homeAccent on the home screen", roles.homeAccent, HOME_BACKGROUND, 3.0)
        }
    }

    @Test
    fun `the default theme is today's, the orange accent and dark`() {
        val roles = colorRoles(ThemeAppearance())
        assertEquals(0xFFFF7043.toInt(), roles.homeAccent)
        assertEquals(0xFF1C232B.toInt(), roles.surface)
        assertEquals(0xFF101418.toInt(), roles.screen)
    }

    @Test
    fun `each accent and each mode gives colors of its own`() {
        assertEquals(themes.size, themes.map(::colorRoles).distinct().size)
        for (accent in AccentColor.entries) {
            assertNotEquals(colorRoles(ThemeAppearance(accent, ThemeMode.DARK)).surface, colorRoles(ThemeAppearance(accent, ThemeMode.LIGHT)).surface)
        }
    }

    private fun assertContrast(theme: ThemeAppearance, what: String, a: Int, b: Int, least: Double) {
        val ratio = contrast(a, b)
        assertTrue("$what in $theme: ${"%.2f".format(ratio)} < $least", ratio >= least)
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val c = (color shr shift and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private companion object {
        /** `:ui`'s background color: the home screen's, without a wallpaper. */
        const val HOME_BACKGROUND = 0xFF101418.toInt()
    }
}
