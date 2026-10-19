package com.luncher.domain.pictures

/**
 * [FolderPictures] in memory: [pictures] is what the folders hold, listed at once; previews drawn
 * with [draw], and kept by picture and size until [forgetPreviews], as the real ones are.
 */
class FakeFolderPictures<Source, Image>(
    vararg pictures: FolderPicture<Source>,
    private val draw: (FolderPicture<Source>, Int, Int) -> Image?,
) : FolderPictures<Source, Image> {

    val pictures: MutableList<FolderPicture<Source>> = pictures.toMutableList()

    /** Every picture [preview] drew, in order. */
    val previewed = mutableListOf<FolderPicture<Source>>()

    /** What each [preview] was told about being stopped, as it began: the test reads them later. */
    val previewStops = mutableListOf<() -> Boolean>()

    override fun list(done: (List<FolderPicture<Source>>) -> Unit) = done(pictures.toList())

    override fun preview(picture: FolderPicture<Source>, width: Int, height: Int, framing: Framing, stopped: () -> Boolean): Image? {
        previewed += picture
        previewStops += stopped
        if (stopped()) return null
        return draw(picture, width, height)?.also { kept[picture to (width to height)] = it }
    }

    /** The previews kept, by picture and size. */
    val kept = mutableMapOf<Pair<FolderPicture<Source>, Pair<Int, Int>>, Image>()

    /** How many times the previews were forgotten. */
    var forgotten = 0
        private set

    override fun cachedPreview(picture: FolderPicture<Source>, width: Int, height: Int, framing: Framing): Image? = kept[picture to (width to height)]

    override fun forgetPreviews() {
        kept.clear()
        forgotten++
    }
}
