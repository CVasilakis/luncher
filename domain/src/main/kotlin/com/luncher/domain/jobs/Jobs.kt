package com.luncher.domain.jobs

/**
 * Port: Luncher's background thread, for work too slow for the main thread
 * (docs/ARCHITECTURE.md#background-work-and-changes-while-shown). Jobs run one at a time, the
 * highest [priority][JobPriority] first and in the order they came among equals; a running job
 * isn't interrupted, so a job is small (one image, not all of them).
 */
interface Jobs {

    /** Queues [job] at [priority]. The handle cancels it if it hasn't started; a started one runs to its end. */
    fun run(priority: Int, job: () -> Unit): Cancellable
}

/**
 * Cancels what it was returned for: a queued job ([Jobs.run]), a picture being added
 * ([PictureImport.add][com.luncher.domain.pictures.PictureImport.add]). Each function that returns
 * one says what cancelling stops, and what still runs to its end.
 */
fun interface Cancellable {
    fun cancel()
}

/** The priority of each kind of job, higher first: all of them here, so their order shows at a glance. */
object JobPriority {

    /** Writing the tiles' images for the next start: after anything a screen waits for. */
    const val BANNER_CACHE = 0

    /** Add from folder's previews of the pictures not selected, drawn ahead in case they're selected next. */
    const val PICTURE_PREFETCH = 1

    /** What the pictures' screens load: thumbnails, previews, the drop folders' list, preparing pictures. */
    const val PICTURE_SCREENS = 2

    /**
     * Adding and deleting pictures, and writing the library's index: one priority, so they run in
     * the order they came, and a picture's file is in place before anything draws it.
     */
    const val PICTURE_LIBRARY = 3

    /** The wallpaper drawn and kept, or deleted: saved, or drawn again, while a screen waits for it. First. */
    const val WALLPAPER = 4
}
