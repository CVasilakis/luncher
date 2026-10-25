package com.luncher.launcher.pictures

import android.graphics.Bitmap
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId

/**
 * A picture of the library drawn into an app's tile, in place of its banner
 * ([Banner.Picture][com.luncher.domain.apps.Banner.Picture]): what the banner adapters ask for one,
 * and fall back to the app's own image from when it can't be drawn now.
 */
interface PictureTiles {

    /**
     * [picture] drawn [width] x [height] px, as [framing] puts it: quick enough for the main thread,
     * where the tiles are drawn. Null when it can't be now (not prepared for this screen, damaged).
     */
    fun draw(picture: PictureId, framing: Framing, width: Int, height: Int): Bitmap?

    /** Whether [picture] is still in the library, as far as its files say: for a cache's job, safe on any thread. */
    fun exists(picture: PictureId): Boolean
}
