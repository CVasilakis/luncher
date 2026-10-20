package com.luncher.domain.pictures

/**
 * Port: each picture of the library prepared once for the screens that show it, so that its
 * thumbnail, and its preview at every value of an option the user tries, are drawn in
 * milliseconds rather than by decoding the picture again
 * (docs/ARCHITECTURE.md#background-work-and-changes-while-shown, point 7). A picture is prepared
 * as it's added; one that isn't, or not for the screen as it is now (another size, an update that
 * draws pictures otherwise), is prepared again by the screen that shows it, at its start.
 */
interface PreparedPictures {

    /** Whether [picture] is prepared for the screen as it is now: quick, for the main thread. */
    fun isPrepared(picture: PictureId): Boolean

    /**
     * Prepares [picture], unless it is: slow, a job, one picture each. False if it can't be (its
     * file gone, or no room for what it keeps), and once [stopped] says so: it stops then.
     */
    fun prepare(picture: PictureId, stopped: () -> Boolean): Boolean
}
