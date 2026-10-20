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

    /**
     * Names the picture [id] [name], one [renaming] gives, and tells the listeners; nothing if the
     * library hasn't it, or it has that name. Its files and the images drawn of it stay as they
     * are: they go by its id, which a name doesn't change.
     */
    fun rename(id: PictureId, name: String)

    /** Calls [listener] on the main thread whenever a picture was added, renamed or deleted, whichever screen did it. */
    fun addListener(listener: () -> Unit)

    fun removeListener(listener: () -> Unit)
}
