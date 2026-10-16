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

/** Cancels a queued job that hasn't started. */
fun interface Cancellable {
    fun cancel()
}

/** The priority of each kind of job, higher first: all of them here, so their order shows at a glance. */
object JobPriority {

    /** Writing the tiles' images for the next start: after anything a screen waits for. */
    const val BANNER_CACHE = 0

    /** What the pictures' screens load: thumbnails, previews, the drop folders' list. */
    const val PICTURE_SCREENS = 1

    /**
     * Adding and deleting pictures, and writing the library's index: one priority, so they run in
     * the order they came, and a picture's file is in place before anything draws it.
     */
    const val PICTURE_LIBRARY = 2

    /** The home screen's wallpaper, which it shows without until it's drawn: first. */
    const val WALLPAPER = 3
}
