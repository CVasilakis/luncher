package com.luncher.domain.pictures

/**
 * Port: a picture of the library drawn at the size a screen shows it, with a [Framing] (a
 * thumbnail, later the wallpaper). [Image] is the UI's own image type (a Bitmap on Android), which
 * this module can't name.
 */
interface PictureImages<Image> {

    /**
     * [picture] as drawn earlier at [width] x [height] px with [framing], if that's kept: quick,
     * for the main thread, so a screen shows what it has at once. Null if it isn't kept.
     */
    fun cached(picture: Picture, width: Int, height: Int, framing: Framing): Image?

    /**
     * [picture] drawn at [width] x [height] px with [framing], and kept for [cached]: slow, a job.
     * Null if its file can't be read (deleted meanwhile, say).
     */
    fun render(picture: Picture, width: Int, height: Int, framing: Framing): Image?
}
