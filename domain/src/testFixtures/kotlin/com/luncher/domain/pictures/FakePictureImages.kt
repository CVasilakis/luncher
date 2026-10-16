package com.luncher.domain.pictures

/**
 * [PictureImages] that draw with [draw], counting what they draw; [cache] holds what [cached]
 * finds, by picture and style, as the real cache would after a [render]. [missing] are pictures
 * whose file is gone: nothing is drawn of them.
 */
class FakePictureImages<Image>(private val draw: (PictureId, Int, Int, PictureStyle) -> Image) : PictureImages<Image> {

    /** What [cached] finds: an image per picture and style, drawn earlier. */
    val cache = mutableMapOf<Pair<PictureId, PictureStyle>, Image>()

    /** Every picture [render] drew, in order, and in which style. */
    val rendered = mutableListOf<Pair<PictureId, PictureStyle>>()

    val missing = mutableSetOf<PictureId>()

    override fun cached(picture: PictureId, width: Int, height: Int, style: PictureStyle): Image? = cache[picture to style]

    override fun render(picture: PictureId, width: Int, height: Int, style: PictureStyle): Image? {
        if (picture in missing) return null
        rendered += picture to style
        return draw(picture, width, height, style).also { cache[picture to style] = it }
    }
}
