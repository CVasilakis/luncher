package com.luncher.domain.wallpaper

/**
 * Port: the one wallpaper kept, drawn at the screen's size, with its frosted copy, so the home
 * screen shows both from its first frame without drawing them. [Image] is the UI's own image type
 * (a Bitmap on Android). Implemented in :platform as one file in the app's private storage,
 * replaced in one step.
 */
interface StoredWallpaper<Image> {

    /**
     * What the kept wallpaper was drawn from; null if there's none, if it can't be read, or if
     * another version of Luncher drew it. Quick, for the main thread: only its header is read.
     */
    fun drawing(): WallpaperDrawing?

    /** The kept wallpaper and its frosted copy, if drawn from [drawing] and both can be read; else null. For the main thread, as a start needs it before its first frame. */
    fun read(drawing: WallpaperDrawing): DrawnWallpaper<Image>?

    /** Keeps [wallpaper], drawn from [drawing], in place of the one kept, in one step: never half of it, never two, never one without its frosted copy. Slow: a job. */
    fun store(drawing: WallpaperDrawing, wallpaper: DrawnWallpaper<Image>)

    /** Deletes the kept wallpaper, if there's one. A job. */
    fun delete()
}
