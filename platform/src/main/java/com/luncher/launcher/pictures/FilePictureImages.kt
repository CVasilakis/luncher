package com.luncher.launcher.pictures

import android.content.Context
import android.graphics.Bitmap
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.pictures.PictureStyle
import com.luncher.launcher.ui.color
import java.io.File
import com.luncher.launcher.ui.R as UiR

/**
 * [PictureImages] drawn from the library's original files in [folder] ([FilesPictures]): decoded
 * at no more than the size asked, framed by the domain's rule into an ARGB_8888 bitmap of exactly
 * that size ([PictureFiles.draw]), over the home screen's background, then blurred and darkened as
 * the style says ([PictureFiles.styled]). What's stored for good is drawn this way: the wallpaper.
 * The screens draw from working copies instead ([WorkingCopyImages]). Keeps nothing.
 */
class FilePictureImages(context: Context, private val folder: File = File(context.filesDir, "pictures")) : PictureImages<Bitmap> {

    private val background = context.color(UiR.color.background)

    override fun cached(picture: PictureId, width: Int, height: Int, style: PictureStyle): Bitmap? = null

    override fun render(picture: PictureId, width: Int, height: Int, style: PictureStyle, stopped: () -> Boolean): Bitmap? {
        val file = File(folder, picture.value)
        return PictureFiles.styled(width, height, style) { w, h -> PictureFiles.draw(file, w, h, style.framing, background, stopped) }
    }

    internal companion object {
        /**
         * How pictures are drawn, in its version: up by one with each change to the drawing, so
         * what was kept drawn the old way (the caches, the wallpaper) is drawn again.
         */
        const val DRAWING = 1
    }
}
