package com.luncher.domain.pictures

/**
 * The library as the background editor lists it: the newest picture first, so one just added is
 * first; and what each one is used for ([uses], from [pictureUses]), which deleting it says.
 */
class PictureLibrary(pictures: List<Picture>, private val uses: Map<PictureId, List<PictureUse>> = emptyMap()) {

    /** Newest first; the same time by id, so the order never changes between two reads. */
    val pictures: List<Picture> = pictures.sortedWith(compareByDescending<Picture> { it.added }.thenBy { it.id.value })

    /** What the picture [id] is used for; nothing if it's unused. */
    fun usesOf(id: PictureId): List<PictureUse> = uses[id].orEmpty()

    /** Where the picture [id] is in [pictures]; -1 if it isn't. */
    fun indexOf(id: PictureId): Int = pictures.indexOfFirst { it.id == id }
}
