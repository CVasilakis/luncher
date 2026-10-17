package com.luncher.launcher.pictures

import android.content.Context
import android.graphics.Bitmap
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.pictures.PictureStyle
import com.luncher.launcher.ui.color
import java.io.File
import com.luncher.launcher.ui.R as UiR

/**
 * [PictureImages] drawn from the library's files in [folder] ([FilesPictures]): decoded at no more
 * than the size asked, framed by the domain's rule into an ARGB_8888 bitmap of exactly that size
 * ([PictureFiles.draw]), over the home screen's background, then darkened as the style says.
 * Keeps nothing: [CachedPictureImages] goes in front of it.
 *
 * A blurred picture is drawn at half the size, which a blur's softness hides: it decodes smaller,
 * and blurring and dimming it costs a quarter. Then it's drawn up to the size asked, filtered and
 * dithered, the dim's fill already in it, so the dither covers that too.
 */
class FilePictureImages(context: Context, private val folder: File = File(context.filesDir, "pictures")) : PictureImages<Bitmap> {

    private val background = context.color(UiR.color.background)

    override fun cached(picture: PictureId, width: Int, height: Int, style: PictureStyle): Bitmap? = null

    override fun render(picture: PictureId, width: Int, height: Int, style: PictureStyle): Bitmap? {
        val file = File(folder, picture.value)
        if (style.blur == Blur.OFF) {
            val image = PictureFiles.draw(file, width, height, style.framing, background) ?: return null
            PictureFiles.dim(image, style.dim)
            return image
        }
        val half = PictureFiles.draw(file, (width + 1) / 2, (height + 1) / 2, style.framing, background) ?: return null
        PictureFiles.blur(half, style.blur)
        PictureFiles.dim(half, style.dim)
        return PictureFiles.enlarge(half, width, height)
    }

    internal companion object {
        /**
         * How pictures are drawn, in its version: up by one with each change to the drawing, so
         * what was kept drawn the old way (the caches, the wallpaper) is drawn again.
         */
        const val DRAWING = 1
    }
}
