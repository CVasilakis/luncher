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

    /**
     * Why [picture] isn't prepared for the screen as it is now, or null if it is: quick, for the
     * main thread.
     */
    fun whyPrepare(picture: PictureId): PreparingCause?

    /**
     * Prepares [picture], unless it is: slow, a job, one picture each. False if it can't be (its
     * file gone, or no room for what it keeps), and once [stopped] says so: it stops then.
     */
    fun prepare(picture: PictureId, stopped: () -> Boolean): Boolean
}

/**
 * Whether [picture] is prepared for the screen as it is now: quick, for the main thread. Not a
 * default method of the port: the app's code is desugared for API 22, which moves a default's body
 * out of the interface, and a fake in the instrumented tests' APK, built apart, would call it there
 * (AbstractMethodError, on every API level).
 */
fun PreparedPictures.isPrepared(picture: PictureId): Boolean = whyPrepare(picture) == null

/** Why a picture isn't prepared. Each screen that says it does so in its language, in an exhaustive `when`. */
enum class PreparingCause {
    /** Prepared the same way for a screen of another size (the TV switched to 4K, say). */
    SCREEN_CHANGED,

    /** Not prepared, or by another version of Luncher, or what was prepared is damaged. */
    OUTDATED,
}
