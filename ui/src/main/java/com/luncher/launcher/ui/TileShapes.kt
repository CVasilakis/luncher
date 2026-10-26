package com.luncher.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import com.luncher.domain.apps.ShapeMasks
import com.luncher.domain.apps.TileMask

/**
 * A see-through picture's shape on its tile ([ShapeMasks]), made from the tile's image as it's
 * prepared, in a job: a band around its outline, which a focus frame that follows the shape is
 * drawn with, and its soft shadow, which the tile casts as it's lifted. Each at half the tile's
 * scale ([SCALE]): their blur hides it, and they take a quarter of the memory. Both are kept, so
 * turning the appearance's focus frame on or off prepares nothing. The tile's painter draws them.
 */
class TileShapes(context: Context) {

    private val band = context.resources.getDimension(R.dimen.tile_shape_band) / SCALE
    private val shadow = context.resources.getDimension(R.dimen.tile_shape_shadow) / SCALE

    /** [image]'s shape, a tile's image: allocates, for a job. */
    fun make(image: Bitmap): ShapeMasks<Bitmap> {
        val half = Bitmap.createScaledBitmap(image, (image.width + 1) / SCALE, (image.height + 1) / SCALE, true)
        try {
            return ShapeMasks(mask(half, band, BlurMaskFilter.Blur.OUTER), mask(half, shadow, BlurMaskFilter.Blur.NORMAL))
        } finally {
            if (half !== image) half.recycle()
        }
    }

    /** [image]'s alpha, blurred [radius] px as [blur] says, and where it goes on the image. */
    private fun mask(image: Bitmap, radius: Float, blur: BlurMaskFilter.Blur): TileMask<Bitmap> {
        val offset = IntArray(2)
        val mask = image.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter(radius, blur) }, offset)
        return TileMask(mask, offset[0], offset[1])
    }

    companion object {
        /** How many times smaller than the tile the masks are. */
        const val SCALE = 2
    }
}
