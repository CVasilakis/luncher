package com.luncher.domain.pictures

/**
 * The library as the Pictures screen shows it: the newest picture first, so one just added is
 * first; how much room they take; what each one is used for ([uses], from [pictureUses]); and
 * the pictures nothing uses, which "Delete unused" deletes.
 */
class PictureLibrary(pictures: List<Picture>, private val uses: Map<PictureId, List<PictureUse>> = emptyMap()) {

    /** Newest first; the same time by id, so the order never changes between two reads. */
    val pictures: List<Picture> = pictures.sortedWith(compareByDescending<Picture> { it.added }.thenBy { it.id.value })

    val bytes: Long = pictures.sumOf { it.bytes }

    /** The pictures nothing uses, in [pictures]' order. */
    val unused: List<Picture> = this.pictures.filter { usesOf(it.id).isEmpty() }

    val unusedBytes: Long = unused.sumOf { it.bytes }

    /** What the picture [id] is used for; nothing if it's unused. */
    fun usesOf(id: PictureId): List<PictureUse> = uses[id].orEmpty()

    /** Where the picture [id] is in [pictures]; -1 if it isn't. */
    fun indexOf(id: PictureId): Int = pictures.indexOfFirst { it.id == id }
}
