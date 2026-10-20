package com.luncher.domain.appearance

import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.PictureStyle

/**
 * The home screen's background: a picture of the library behind everything, or none (the home
 * screen's plain color, the default), and how it's drawn. A new value draws the wallpaper again,
 * and nothing else.
 */
data class Background(
    /** The picture; null for none, as when it was deleted. */
    val picture: PictureId? = null,
    val fit: Fit = Fit.FILL,
    /** Only with [Fit.FILL]. */
    val position: PicturePosition = PicturePosition.CENTER,
    /** How much darker the picture is drawn, in percent, so the tiles stand out. */
    val dim: Int = 0,
    val blur: Blur = Blur.OFF,
) {
    /** How the picture is drawn into the screen. */
    val style: PictureStyle get() = PictureStyle(Framing(fit, position), dim, blur)

    /**
     * This background with only values that change anything in [context]: a position that doesn't
     * move the picture, as the top of a picture cut at the sides, is the middle, which draws the
     * same, so the next change starts there. A fit is kept: its other value draws otherwise as soon
     * as a picture of another shape is chosen.
     */
    fun offeredIn(context: OptionContext): Background {
        val positions = AppearanceOption.BackgroundPosition.values(context)
        return if (position in positions) this else copy(position = AppearanceOption.BackgroundPosition.default)
    }
}
