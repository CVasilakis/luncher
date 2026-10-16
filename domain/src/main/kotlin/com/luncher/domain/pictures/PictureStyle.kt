package com.luncher.domain.pictures

/** How blurred a picture is drawn: not, a little (the picture still recognizable), or a lot (only its colors left). */
enum class Blur { OFF, LIGHT, STRONG }

/**
 * How a picture is drawn into a space: its [framing], darkened by [dim] percent (0 is as it is,
 * 100 black), and blurred. A thumbnail is the picture as it is; the wallpaper, behind the tiles,
 * may be darker and blurred so they stand out.
 */
data class PictureStyle(
    val framing: Framing = Framing(),
    val dim: Int = 0,
    val blur: Blur = Blur.OFF,
)
