package com.luncher.domain.pictures

/**
 * How blurred a picture is drawn: not, a little (the picture still recognizable), more, or a lot
 * (only its colors left), in equal steps. [spread] is how far each point spreads, a Gaussian blur's
 * standard deviation, as a share of the width the picture is drawn at, so it looks the same on any
 * screen and in the appearance editor's preview.
 */
enum class Blur(val spread: Double) {
    OFF(0.0),
    LIGHT(0.005),
    MEDIUM(0.010),
    STRONG(0.015),
}

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
