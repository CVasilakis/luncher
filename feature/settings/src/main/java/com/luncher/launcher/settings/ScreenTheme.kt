package com.luncher.launcher.settings

import com.luncher.domain.appearance.AppearanceSettings
import com.luncher.domain.appearance.ColorRoles
import com.luncher.domain.appearance.ThemeAppearance
import com.luncher.domain.appearance.colorRoles

/**
 * A screen's theme: the colors of the theme the appearance has now ([roles]), which the screen is
 * styled with as it's made, and again, in place, whenever the theme changes while the screen is
 * started ([apply]): the appearance editor's Theme group changes it while the editor shows, and
 * the screens under it find it changed when they come back. Only the theme part is compared, so
 * another option changed doesn't restyle anything.
 */
internal class ScreenTheme(private val settings: AppearanceSettings, private val apply: (ColorRoles) -> Unit) {

    private var theme: ThemeAppearance = settings.read().theme

    /** The colors of the theme as it was last read. */
    var roles: ColorRoles = colorRoles(theme)
        private set

    private val changed: () -> Unit = { read() }

    /** Follows the theme until [stop], restyling the screen if it changed meanwhile. */
    fun start() {
        settings.addListener(changed)
        read()
    }

    fun stop() {
        settings.removeListener(changed)
    }

    private fun read() {
        val now = settings.read().theme
        if (now == theme) return
        theme = now
        roles = colorRoles(now)
        apply(roles)
    }
}
