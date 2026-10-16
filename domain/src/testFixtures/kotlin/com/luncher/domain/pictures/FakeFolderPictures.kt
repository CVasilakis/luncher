package com.luncher.domain.pictures

/** [FolderPictures] in memory: [pictures] is what the folders hold, listed at once; previews drawn with [draw]. */
class FakeFolderPictures<Source, Image>(
    vararg pictures: FolderPicture<Source>,
    private val draw: (FolderPicture<Source>, Int, Int) -> Image?,
) : FolderPictures<Source, Image> {

    val pictures: MutableList<FolderPicture<Source>> = pictures.toMutableList()

    /** Every picture [preview] drew, in order. */
    val previewed = mutableListOf<FolderPicture<Source>>()

    override fun list(done: (List<FolderPicture<Source>>) -> Unit) = done(pictures.toList())

    override fun preview(picture: FolderPicture<Source>, width: Int, height: Int, framing: Framing): Image? {
        previewed += picture
        return draw(picture, width, height)
    }
}
