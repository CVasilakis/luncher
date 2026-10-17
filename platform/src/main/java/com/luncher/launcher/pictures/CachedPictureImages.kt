package com.luncher.launcher.pictures

import android.content.Context
import android.graphics.Bitmap
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.pictures.PictureStyle
import com.luncher.launcher.images.PixelFiles
import java.io.File

/**
 * [PictureImages] that keep what [images] draw in files, so a screen shows them at once the next
 * time ([cached]), as `CachedBannerImages` does for the tiles: the thumbnails at the grid's tile
 * size in px, the wallpaper at the screen's, the editor's preview at its own. A file per picture
 * and size, in [folder]/<picture id>/, which [FilesPictures] deletes with the picture; another
 * style at the same size replaces it. The format:
 * [PixelFiles], with what the image was drawn from as the header; a file that doesn't match is
 * drawn and written again, so it needs no migration, and Android may delete it.
 *
 * [cached] reads on the main thread. [render] is a job: it draws, then writes the file, in the
 * same job.
 */
class CachedPictureImages(
    context: Context,
    private val images: PictureImages<Bitmap>,
    private val folder: File = File(context.cacheDir, "pictures"),
) : PictureImages<Bitmap> {

    /** What an image was drawn from: a file holds an image only for these. */
    private data class Drawing(
        val picture: String,
        val width: Int,
        val height: Int,
        val fit: String,
        val position: String,
        val dim: Int,
        val blur: String,
    ) {
        constructor(picture: PictureId, width: Int, height: Int, style: PictureStyle) :
            this(picture.value, width, height, style.framing.fit.name, style.framing.position.name, style.dim, style.blur.name)
    }

    // Its buffers are the main thread's, where cached reads.
    private val pixelFiles = PixelFiles(MAGIC, FORMAT)

    override fun cached(picture: PictureId, width: Int, height: Int, style: PictureStyle): Bitmap? {
        val drawing = Drawing(picture, width, height, style)
        return pixelFiles.read(fileOf(picture, width, height), width, height) { input ->
            Drawing(input.readUTF(), input.readInt(), input.readInt(), input.readUTF(), input.readUTF(), input.readInt(), input.readUTF()) == drawing
        }
    }

    override fun render(picture: PictureId, width: Int, height: Int, style: PictureStyle): Bitmap? {
        val image = images.render(picture, width, height, style) ?: return null
        val drawing = Drawing(picture, width, height, style)
        // Only for writing: the reading buffers stay the main thread's.
        PixelFiles(MAGIC, FORMAT).write(fileOf(picture, width, height), image) { output ->
            output.writeUTF(drawing.picture)
            output.writeInt(drawing.width)
            output.writeInt(drawing.height)
            output.writeUTF(drawing.fit)
            output.writeUTF(drawing.position)
            output.writeInt(drawing.dim)
            output.writeUTF(drawing.blur)
        }
        return image
    }

    private fun fileOf(picture: PictureId, width: Int, height: Int) = File(File(folder, picture.value), "${width}x$height")

    private companion object {
        /** "LPIC": a file of this class. */
        const val MAGIC = 0x4C504943

        /** The file format's version, and the drawing's: another one is a mismatch, drawn and written again. */
        const val FORMAT = 3
    }
}
