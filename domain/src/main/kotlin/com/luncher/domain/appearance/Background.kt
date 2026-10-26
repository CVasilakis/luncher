package com.luncher.domain.appearance

import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureChoice
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.pictures.offeredIn

/**
 * The home screen's background: a picture of the library behind everything, or none (the home
 * screen's plain color, the default), and how it's drawn. A new value draws the wallpaper again,
 * and nothing else. Chosen in the background editor by [PictureChoice]'s rules, where another
 * picture also starts neither dimmed nor blurred.
 */
data class Background(
    override val picture: PictureId? = null,
    override val framing: Framing = Framing(),
    /** How much darker the picture is drawn, in percent, so the tiles stand out. */
    val dim: Int = 0,
    val blur: Blur = Blur.OFF,
) : PictureChoice<Background> {

    /** How the picture is drawn into the screen. */
    val style: PictureStyle get() = PictureStyle(framing, dim, blur)

    override fun framed(framing: Framing) = copy(framing = framing)

    override fun starting(picture: PictureId?, framing: Framing) = Background(picture, framing)

    /** [stored] as it is: the dim and blur go with its picture, and the background has no values of its own. */
    override fun restoring(stored: Background) = stored

    /**
     * This background with only values that change anything in [context], where filling the screen
     * cuts [AppearanceContext.backgroundCut] from its picture ([offeredIn]).
     */
    fun offeredIn(context: AppearanceContext): Background = offeredIn(context.backgroundCut)
}
