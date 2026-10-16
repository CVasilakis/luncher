package com.luncher.domain.pictures

/**
 * [PictureImages] that draw with [draw], counting what they draw; [cache] holds what [cached]
 * finds, by picture, as the real cache would after a [render].
 */
class FakePictureImages<Image>(private val draw: (Picture, Int, Int, Framing) -> Image) : PictureImages<Image> {

    /** What [cached] finds: an image per picture id, drawn earlier. */
    val cache = mutableMapOf<PictureId, Image>()

    /** Every picture [render] drew, in order. */
    val rendered = mutableListOf<Picture>()

    override fun cached(picture: Picture, width: Int, height: Int, framing: Framing): Image? = cache[picture.id]

    override fun render(picture: Picture, width: Int, height: Int, framing: Framing): Image {
        rendered += picture
        return draw(picture, width, height, framing).also { cache[picture.id] = it }
    }
}
