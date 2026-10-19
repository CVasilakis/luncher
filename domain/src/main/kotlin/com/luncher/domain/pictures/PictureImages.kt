package com.luncher.domain.pictures

/**
 * Port: a picture of the library drawn at the size a screen shows it, in a [PictureStyle] (a
 * thumbnail, the wallpaper, the editor's preview of it). [Image] is the UI's own image type (a
 * Bitmap on Android), which this module can't name.
 */
interface PictureImages<Image> {

    /**
     * [picture] as drawn earlier at [width] x [height] px in [style], if that's kept: quick, for
     * the main thread, so a screen shows what it has at once. Null if it isn't kept.
     */
    fun cached(picture: PictureId, width: Int, height: Int, style: PictureStyle): Image?

    /**
     * [picture] drawn at [width] x [height] px in [style], and kept for [cached]: slow, a job.
     * Null if it isn't in the library, or its file can't be read (deleted meanwhile, say), or
     * once [stopped] says so (a screen's request dropped): it stops reading then, and keeps nothing.
     */
    fun render(picture: PictureId, width: Int, height: Int, style: PictureStyle, stopped: () -> Boolean = { false }): Image?
}
