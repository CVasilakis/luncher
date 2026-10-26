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
 * The file format of the image caches (`CachedBannerImages`, `CachedPictureImages`), the kept
 * wallpaper (`FileStoredWallpaper`) and the prepared tiles (`FilePreparedTiles`): after a header,
 * [magic] and [format] first, then what the store wrote of what the images were drawn from, one
 * image a file, or a few ([readAll], [writeAll]: a tile's image and its masks); each image's pixels as they are in
 * memory (ARGB_8888, premultiplied, or ALPHA_8), compressed with [Deflater], so a pixel reads back
 * exactly as it was drawn, its transparency too.
 *
 * [read] and [readAll] are for the main thread, where screens read what's kept, and reuse this
 * instance's buffers, so each store has its own. [header] keeps nothing, for either (a cache's
 * sweep reads headers in a job). [write] and [writeAll] are a job's, and allocate their own.
 */
internal class PixelFiles(private val magic: Int, private val format: Int) {

    private val inflater = Inflater()
    private var packed = ByteArray(0)
    private var pixels = ByteArray(0)

    /** The size and config of an image of a file, as its header says. */
    class Shape(val width: Int, val height: Int, val config: Bitmap.Config = Bitmap.Config.ARGB_8888)

    /**
     * [file]'s image, [width] x [height] px, if [matches] reads its header (what follows [magic]
     * and [format]) as the one wanted; else null: no file, another format or drawing, or a file
     * that can't be read.
     */
    fun read(file: File, width: Int, height: Int, matches: (DataInputStream) -> Boolean): Bitmap? =
        readAll(file) { input -> if (matches(input)) listOf(Shape(width, height)) else null }?.single()

    /**
     * [file]'s images, of the shapes [shapes] reads from its header (what follows [magic] and
     * [format]), or null for a header it doesn't want; null too without a file of this format, or
     * for one that can't be read, its images freed.
     */
    fun readAll(file: File, shapes: (DataInputStream) -> List<Shape>?): List<Bitmap>? {
        if (!file.isFile) return null
        val images = ArrayList<Bitmap>(2)
        try {
            DataInputStream(BufferedInputStream(FileInputStream(file), BUFFER)).use { input ->
                if (input.readInt() != magic || input.readInt() != format) return null
                for (shape in shapes(input) ?: return null) {
                    val image = readImage(input, file.length(), shape)
                    if (image == null) {
                        recycle(images)
                        return null
                    }
                    images += image
                }
                return images
            }
        } catch (e: IOException) {
            recycle(images)
            return null
        } catch (e: DataFormatException) {
            recycle(images)
            return null
        }
    }

    /** The next image of [input], of [shape], or null if it isn't whole. */
    private fun readImage(input: DataInputStream, fileLength: Long, shape: Shape): Bitmap? {
        val length = input.readInt()
        if (length < 0 || length > fileLength) return null
        if (packed.size < length) packed = ByteArray(length)
        input.readFully(packed, 0, length)
        val image = Bitmap.createBitmap(shape.width, shape.height, shape.config)
        val size = image.byteCount
        if (pixels.size < size) pixels = ByteArray(size)
        inflater.reset()
        inflater.setInput(packed, 0, length)
        if (inflater.inflate(pixels, 0, size) != size || !inflater.finished()) {
            image.recycle()
            return null
        }
        image.copyPixelsFromBuffer(ByteBuffer.wrap(pixels, 0, size))
        return image
    }

    private fun recycle(images: List<Bitmap>) {
        for (image in images) image.recycle()
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
        writeAll(file, listOf(image), header)
    }

    /** Writes [images] to [file], in that order, as [write] writes one; whether it could. */
    fun writeAll(file: File, images: List<Bitmap>, header: (DataOutputStream) -> Unit): Boolean {
        val packed = images.map(::pack)
        val temporary = File(file.parentFile, file.name + TEMPORARY)
        try {
            file.parentFile?.mkdirs()
            DataOutputStream(BufferedOutputStream(FileOutputStream(temporary), BUFFER)).use { output ->
                output.writeInt(magic)
                output.writeInt(format)
                header(output)
                for (image in packed) {
                    output.writeInt(image.size())
                    image.writeTo(output)
                }
            }
            if (temporary.renameTo(file)) return true
            temporary.delete()
        } catch (e: IOException) {
            temporary.delete()
        }
        return false
    }

    /** [image]'s pixels, compressed. */
    private fun pack(image: Bitmap): ByteArrayOutputStream {
        val pixels = ByteBuffer.allocate(image.byteCount)
        image.copyPixelsToBuffer(pixels)
        val packed = ByteArrayOutputStream(pixels.capacity() / 4)
        val deflater = Deflater(Deflater.BEST_SPEED)
        deflater.setInput(pixels.array())
        deflater.finish()
        val chunk = ByteArray(BUFFER)
        while (!deflater.finished()) packed.write(chunk, 0, deflater.deflate(chunk))
        deflater.end()
        return packed
    }

    companion object {
        /** The ending of a file [write] hasn't finished: a cache deletes those it finds. */
        const val TEMPORARY = ".new"

        private const val BUFFER = 64 * 1024

        /** Enough for any header, so [header] reads little. */
        private const val HEADER_BUFFER = 1024
    }
}
