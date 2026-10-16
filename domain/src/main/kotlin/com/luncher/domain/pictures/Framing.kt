package com.luncher.domain.pictures

/** How a picture fills a space of another shape. There's no fit with bars: a picture always covers the space. */
enum class Fit {
    /** Scaled to cover the space, keeping its shape: what doesn't fit is cut, as [Framing.position] says. */
    FILL,

    /** Scaled to the space's shape, whole. */
    STRETCH,
}

/** Which part of a filling picture shows: its middle, or the side named, in the screen's directions in every language. */
enum class PicturePosition { CENTER, TOP, BOTTOM, LEFT, RIGHT }

/** How a picture is put into a space: every image drawn of a picture (a thumbnail, the wallpaper) has one. */
data class Framing(
    val fit: Fit = Fit.FILL,
    /** Only with [Fit.FILL]. */
    val position: PicturePosition = PicturePosition.CENTER,
)

/** The part of a picture that fills a space, in the picture's px: from [left], [top] to [right], [bottom]. */
data class PictureArea(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * The part of a [pictureWidth] x [pictureHeight] picture that fills a [width] x [height] space
 * with [framing]: with [Fit.FILL] as much of the picture as has the space's shape, at its middle
 * or at the side its position names (left or right cut from a wide picture, top or bottom from a
 * tall one; a side across the cut keeps the middle); with [Fit.STRETCH] all of it. The home
 * screen, every preview and the thumbnails take it from here, so they can't disagree.
 */
fun frame(pictureWidth: Int, pictureHeight: Int, width: Int, height: Int, framing: Framing): PictureArea {
    val whole = PictureArea(0f, 0f, pictureWidth.toFloat(), pictureHeight.toFloat())
    if (framing.fit == Fit.STRETCH || pictureWidth <= 0 || pictureHeight <= 0 || width <= 0 || height <= 0) return whole
    // The space's shape at the picture's scale: as wide as the picture, or as tall.
    val wider = width.toLong() * pictureHeight > height.toLong() * pictureWidth
    val shownWidth = if (wider) pictureWidth.toFloat() else pictureHeight.toFloat() * width / height
    val shownHeight = if (wider) pictureWidth.toFloat() * height / width else pictureHeight.toFloat()
    val spareX = pictureWidth - shownWidth
    val spareY = pictureHeight - shownHeight
    val left = when (framing.position) {
        PicturePosition.LEFT -> 0f
        PicturePosition.RIGHT -> spareX
        else -> spareX / 2
    }
    val top = when (framing.position) {
        PicturePosition.TOP -> 0f
        PicturePosition.BOTTOM -> spareY
        else -> spareY / 2
    }
    return PictureArea(left, top, left + shownWidth, top + shownHeight)
}
