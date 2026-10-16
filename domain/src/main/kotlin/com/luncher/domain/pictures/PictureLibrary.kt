package com.luncher.domain.pictures

/**
 * The library as the Pictures screen shows it: the newest picture first, so one just added is
 * first; how much room they take; and the pictures nothing uses, which "Delete unused" deletes.
 * [inUse]: the pictures something uses, which "Delete unused" keeps.
 */
class PictureLibrary(pictures: List<Picture>, inUse: Set<PictureId> = emptySet()) {

    /** Newest first; the same time by id, so the order never changes between two reads. */
    val pictures: List<Picture> = pictures.sortedWith(compareByDescending<Picture> { it.added }.thenBy { it.id.value })

    val bytes: Long = pictures.sumOf { it.bytes }

    /** The pictures nothing uses, in [pictures]' order. */
    val unused: List<Picture> = this.pictures.filter { it.id !in inUse }

    val unusedBytes: Long = unused.sumOf { it.bytes }

    /** Where the picture [id] is in [pictures]; -1 if it isn't. */
    fun indexOf(id: PictureId): Int = pictures.indexOfFirst { it.id == id }
}
