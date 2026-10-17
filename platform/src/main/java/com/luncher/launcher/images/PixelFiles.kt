package com.luncher.launcher.images

import android.graphics.Bitmap
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * The file format of the image caches (`CachedBannerImages`, `CachedPictureImages`) and the kept
 * wallpaper (`FileStoredWallpaper`): one image a file, after a header, [magic] and [format] first, then what the cache wrote of what the image
 * was drawn from; then the image's pixels as they are in memory (ARGB_8888, premultiplied),
 * compressed with [Deflater], so a pixel reads back exactly as it was drawn, its transparency too.
 *
 * [read] and [header] are for the main thread, where screens read what's kept: [read] reuses this
 * instance's buffers, so each cache has its own. [write] is a job's, and allocates its own.
 */
internal class PixelFiles(private val magic: Int, private val format: Int) {

    private val inflater = Inflater()
    private var packed = ByteArray(0)
    private var pixels = ByteArray(0)

    /**
     * [file]'s image, [width] x [height] px, if [matches] reads its header (what follows [magic]
     * and [format]) as the one wanted; else null: no file, another format or drawing, or a file
     * that can't be read.
     */
    fun read(file: File, width: Int, height: Int, matches: (DataInputStream) -> Boolean): Bitmap? {
        if (!file.isFile) return null
        try {
            DataInputStream(BufferedInputStream(FileInputStream(file), BUFFER)).use { input ->
                if (input.readInt() != magic || input.readInt() != format || !matches(input)) return null
                val length = input.readInt()
                if (length < 0 || length > file.length()) return null
                if (packed.size < length) packed = ByteArray(length)
                input.readFully(packed, 0, length)
                val size = width * height * 4
                if (pixels.size < size) pixels = ByteArray(size)
                inflater.reset()
                inflater.setInput(packed, 0, length)
                if (inflater.inflate(pixels, 0, size) != size || !inflater.finished()) return null
                val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                image.copyPixelsFromBuffer(ByteBuffer.wrap(pixels, 0, size))
                return image
            }
        } catch (e: IOException) {
            return null
        } catch (e: DataFormatException) {
            return null
        }
    }

    /**
     * What [header] reads of [file]'s header (what follows [magic] and [format]), without its
     * pixels; null without a file of this format, or one that can't be read.
     */
    fun <T> header(file: File, header: (DataInputStream) -> T?): T? {
        if (!file.isFile) return null
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file), HEADER_BUFFER)).use { input ->
                if (input.readInt() != magic || input.readInt() != format) null else header(input)
            }
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Writes [image] to [file], its header written by [header] after [magic] and [format]: to a
     * new file that then replaces the old one at once, so a reader never sees half of it. Nothing
     * is written when that fails (a full disk, say): the next start draws the image again.
     */
    fun write(file: File, image: Bitmap, header: (DataOutputStream) -> Unit) {
        val pixels = ByteBuffer.allocate(image.byteCount)
        image.copyPixelsToBuffer(pixels)
        val packed = ByteArrayOutputStream(pixels.capacity() / 4)
        val deflater = Deflater(Deflater.BEST_SPEED)
        deflater.setInput(pixels.array())
        deflater.finish()
        val chunk = ByteArray(BUFFER)
        while (!deflater.finished()) packed.write(chunk, 0, deflater.deflate(chunk))
        deflater.end()

        val temporary = File(file.parentFile, file.name + TEMPORARY)
        try {
            file.parentFile?.mkdirs()
            DataOutputStream(BufferedOutputStream(FileOutputStream(temporary), BUFFER)).use { output ->
                output.writeInt(magic)
                output.writeInt(format)
                header(output)
                output.writeInt(packed.size())
                packed.writeTo(output)
            }
            if (!temporary.renameTo(file)) temporary.delete()
        } catch (e: IOException) {
            temporary.delete()
        }
    }

    companion object {
        /** The ending of a file [write] hasn't finished: a cache deletes those it finds. */
        const val TEMPORARY = ".new"

        private const val BUFFER = 64 * 1024

        /** Enough for any header, so [header] reads little. */
        private const val HEADER_BUFFER = 1024
    }
}
