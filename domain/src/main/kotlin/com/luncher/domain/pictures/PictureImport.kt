package com.luncher.domain.pictures

import com.luncher.domain.jobs.Cancellable

/**
 * Port: adds a picture to the library from a [Source] (a Uri on Android, which this module can't
 * name): a picture another app shared, or a file of the drop folders ([FolderPictures]). Slow, so
 * it answers later, on the main thread, where it's used.
 */
interface PictureImport<Source> {

    /**
     * Copies the picture [source] has into the library, then calls [done] with how that went. The
     * handle cancels it, at any point before [done] runs: then [done] never runs and the picture
     * isn't added, as if [add] hadn't been called. Afterwards it does nothing.
     */
    fun add(source: Source, done: (ImportResult) -> Unit): Cancellable
}

/** How adding a picture went. Every screen that adds one handles each in an exhaustive `when`. */
sealed interface ImportResult {

    /** In the library now. */
    data class Added(val picture: Picture) : ImportResult

    /** The library had it already (the same content), as [picture]: nothing was added. */
    data class AlreadyThere(val picture: Picture) : ImportResult

    /** Not a picture, or a damaged one. */
    data object NotAPicture : ImportResult

    /** A picture in a [format] this TV can't show. */
    data class UnsupportedFormat(val format: PictureFormat) : ImportResult

    /** Larger than [PictureLimits] allow. */
    data object TooLarge : ImportResult

    /**
     * Luncher couldn't read it: the app that shared it gave no access, the file went away, it
     * was shared as a path (`file://`), which only an app with storage permission can read, or
     * the app stopped sending it.
     */
    data object Unreadable : ImportResult

    /** No room left on the TV for its copy. */
    data object NoSpace : ImportResult
}
