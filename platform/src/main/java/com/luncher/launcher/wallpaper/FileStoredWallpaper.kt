package com.luncher.launcher.wallpaper

import android.content.Context
import android.graphics.Bitmap
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.wallpaper.StoredWallpaper
import com.luncher.domain.wallpaper.WallpaperDrawing
import com.luncher.launcher.images.PixelFiles
import com.luncher.launcher.pictures.FilePictureImages
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

/**
 * [StoredWallpaper] as one file, [file], in the app's private files, which Android doesn't delete
 * on its own as it may a cache. The format: [PixelFiles], its header what the wallpaper was drawn
 * from and how ([FilePictureImages.DRAWING]): one drawn by another version reads as none, and is
 * drawn again. [store] writes a new file that then replaces the old one at once, so there's never
 * half a wallpaper, nor two; a write cut short (the process killed) leaves its unfinished file,
 * which the next [store] writes over and [delete] deletes.
 *
 * [drawing] and [read] are for the main thread, where the home screen starts; [store] and
 * [delete] for a job.
 */
class FileStoredWallpaper(context: Context, private val file: File = File(context.filesDir, "wallpaper")) : StoredWallpaper<Bitmap> {

    // Its buffers are the main thread's, where read runs.
    private val pixelFiles = PixelFiles(MAGIC, FORMAT)

    override fun drawing(): WallpaperDrawing? = pixelFiles.header(file, ::readDrawing)

    override fun read(drawing: WallpaperDrawing): Bitmap? =
        pixelFiles.read(file, drawing.width, drawing.height) { input -> readDrawing(input) == drawing }

    override fun store(drawing: WallpaperDrawing, image: Bitmap) {
        // Only for writing: the reading buffers stay the main thread's.
        PixelFiles(MAGIC, FORMAT).write(file, image) { output -> writeDrawing(output, drawing) }
    }

    override fun delete() {
        file.delete()
        File(file.parentFile, file.name + PixelFiles.TEMPORARY).delete()
    }

    private fun writeDrawing(output: DataOutputStream, drawing: WallpaperDrawing) {
        output.writeInt(FilePictureImages.DRAWING)
        output.writeUTF(drawing.picture.value)
        output.writeUTF(drawing.style.framing.fit.name)
        output.writeUTF(drawing.style.framing.position.name)
        output.writeInt(drawing.style.dim)
        output.writeUTF(drawing.style.blur.name)
        output.writeInt(drawing.width)
        output.writeInt(drawing.height)
    }

    /** The drawing [writeDrawing] wrote; null if it's of another drawing version, or names a value this version doesn't know. */
    private fun readDrawing(input: DataInputStream): WallpaperDrawing? {
        if (input.readInt() != FilePictureImages.DRAWING) return null
        return try {
            val picture = PictureId(input.readUTF())
            val framing = Framing(Fit.valueOf(input.readUTF()), PicturePosition.valueOf(input.readUTF()))
            val style = PictureStyle(framing, input.readInt(), Blur.valueOf(input.readUTF()))
            WallpaperDrawing(picture, style, input.readInt(), input.readInt())
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    private companion object {
        /** "LWAL": the kept wallpaper. */
        const val MAGIC = 0x4C57414C

        /** The file format's version; the drawing's is in the header. */
        const val FORMAT = 1
    }
}
