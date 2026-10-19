package com.luncher.domain.pictures

import java.text.Collator
import java.util.Locale

/**
 * Port: the pictures in Luncher's drop folders, on the TV and on each USB drive, where a file
 * manager or a computer puts them for "Add from folder". Reading them is slow (a drive), so each
 * answers later, on the main thread. [Source] is what [PictureImport] adds a picture from, [Image]
 * the UI's image type.
 */
interface FolderPictures<Source, Image> {

    /** Calls [done] with the pictures in the folders, the ones this TV can't show included. */
    fun list(done: (List<FolderPicture<Source>>) -> Unit)

    /**
     * [picture] drawn at [width] x [height] px with [framing], to preview it: slow, a job. Null if
     * it can't be read, or once [stopped] says so (the preview isn't wanted any more): it stops
     * reading then.
     */
    fun preview(picture: FolderPicture<Source>, width: Int, height: Int, framing: Framing, stopped: () -> Boolean): Image?
}

/** A picture in a drop folder. */
data class FolderPicture<Source>(
    /** The file's name. */
    val name: String,
    /** On a USB drive (or an SD card), not the TV's own storage. */
    val removable: Boolean,
    val bytes: Long,
    /** Its size as shown, in px; 0 when this TV can't read it ([unsupported]). */
    val width: Int,
    val height: Int,
    /** A format this TV can't show, which adding it would say; null when it can. */
    val unsupported: PictureFormat?,
    val source: Source,
)

/** [pictures] as the Add from folder list shows them: the TV's own first, then the drives', each by name as [locale]'s language sorts words. */
fun <Source> inListOrder(pictures: List<FolderPicture<Source>>, locale: Locale): List<FolderPicture<Source>> {
    val collator = Collator.getInstance(locale)
    return pictures.sortedWith(compareBy<FolderPicture<Source>> { it.removable }.thenComparator { a, b -> collator.compare(a.name, b.name) })
}
