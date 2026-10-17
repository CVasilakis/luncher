package com.luncher.domain.wallpaper

/**
 * Port: the one wallpaper kept, drawn at the screen's size, so the home screen shows it from its
 * first frame without drawing it. [Image] is the UI's own image type (a Bitmap on Android).
 * Implemented in :platform as one file in the app's private storage, replaced in one step.
 */
interface StoredWallpaper<Image> {

    /**
     * What the kept wallpaper was drawn from; null if there's none, if it can't be read, or if
     * another version of Luncher drew it. Quick, for the main thread: only its header is read.
     */
    fun drawing(): WallpaperDrawing?

    /** The kept wallpaper, if it was drawn from [drawing] and can be read; else null. For the main thread, as a start needs it before its first frame. */
    fun read(drawing: WallpaperDrawing): Image?

    /** Keeps [image], drawn from [drawing], in place of the one kept, in one step: never half of it, never two. Slow: a job. */
    fun store(drawing: WallpaperDrawing, image: Image)

    /** Deletes the kept wallpaper, if there's one. A job. */
    fun delete()
}
