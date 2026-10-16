package com.luncher.domain.pictures

/**
 * Port: Luncher's picture library, which a screen shows while it changes
 * (docs/ARCHITECTURE.md#background-work-and-changes-while-shown). Pictures come in through
 * [PictureImport].
 */
interface Pictures {

    /** Every picture of the library, in no particular order: [PictureLibrary] orders them. */
    fun read(): List<Picture>

    /** Deletes the pictures [ids] names, their files and every image drawn of them, and tells the listeners. */
    fun delete(ids: Set<PictureId>)

    /** Calls [listener] on the main thread whenever a picture was added or deleted, whichever screen did it. */
    fun addListener(listener: () -> Unit)

    fun removeListener(listener: () -> Unit)
}
