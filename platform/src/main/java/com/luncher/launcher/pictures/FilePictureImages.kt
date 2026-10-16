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
 * [PictureImages] drawn from the library's files in [folder] ([FilesPictures]): decoded at no more
 * than the size asked, framed by the domain's rule into an ARGB_8888 bitmap of exactly that size
 * ([PictureFiles.draw]), over the home screen's background, then blurred and darkened as the
 * style says. Keeps nothing: [CachedPictureImages] goes in front of it.
 */
class FilePictureImages(context: Context, private val folder: File = File(context.filesDir, "pictures")) : PictureImages<Bitmap> {

    private val background = context.color(UiR.color.background)

    override fun cached(picture: PictureId, width: Int, height: Int, style: PictureStyle): Bitmap? = null

    override fun render(picture: PictureId, width: Int, height: Int, style: PictureStyle): Bitmap? {
        val image = PictureFiles.draw(File(folder, picture.value), width, height, style.framing, background) ?: return null
        PictureFiles.blur(image, style.blur)
        PictureFiles.dim(image, style.dim)
        return image
    }
}
