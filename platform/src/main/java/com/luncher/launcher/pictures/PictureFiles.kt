package com.luncher.launcher.pictures

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.annotation.SuppressLint
import android.os.Build
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PICTURE_HEADER_BYTES
import com.luncher.domain.pictures.PictureFormat
import com.luncher.domain.pictures.frame
import com.luncher.domain.pictures.pictureFormatOf
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Reading and drawing a picture file, for the library, its images and the drop folders: what
 * BitmapFactory decodes, judged by decoding, never by the file's name. Safe on any thread: it
 * keeps nothing between calls.
 */
internal object PictureFiles {

    /** A picture file's size as shown (upright, its orientation tag applied), and that tag. */
    class Shape(val width: Int, val height: Int, val orientation: Int)

    /** [file]'s shape, or null if BitmapFactory can't read its size: not a picture, or one in a format this TV can't decode. */
    fun shape(file: File): Shape? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val orientation = orientation(file)
        return if (turned(orientation)) Shape(bounds.outHeight, bounds.outWidth, orientation) else Shape(bounds.outWidth, bounds.outHeight, orientation)
    }

    /**
     * Whether [file] decodes whole, not only its size: AVIF on API 31 reads its size, then fails.
     * Decoded small ([inSampleSize][BitmapFactory.Options.inSampleSize] for at most [CHECK_SIZE]
     * px a side), so a large picture costs little memory.
     */
    fun decodes(file: File, shape: Shape): Boolean {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(maxOf(shape.width, shape.height).toFloat() / CHECK_SIZE)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return false
        bitmap.recycle()
        return true
    }

    /** The format [file] is in, when it's one Luncher names because a TV may not show it ([pictureFormatOf]). */
    fun format(file: File): PictureFormat? = pictureFormatOf(read(file, PICTURE_HEADER_BYTES))

    /**
     * [file] drawn into a new ARGB_8888 bitmap of [width] x [height] px, as [framing] puts it, over
     * [background] (which shows through a picture's transparent parts); null if it can't be read.
     * Decoded at no more than the size needed ([inSampleSize][BitmapFactory.Options.inSampleSize]),
     * into sRGB from API 26 on (a phone's photo may be in a wider color space), upright as its
     * orientation tag says, and freed once drawn. A decoder may give another config (16-bit PNGs
     * decode to RGBA_F16 from API 26 on, GIFs to none up to 26): drawing it here makes it ARGB_8888.
     */
    fun draw(file: File, width: Int, height: Int, framing: Framing, background: Int): Bitmap? {
        val shape = shape(file) ?: return null
        val area = frame(shape.width, shape.height, width, height, framing)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(minOf((area.right - area.left) / width, (area.bottom - area.top) / height))
            inPreferredConfig = Bitmap.Config.ARGB_8888
            if (Build.VERSION.SDK_INT >= 26) inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        val decoded = BitmapFactory.decodeFile(file.path, options) ?: return null
        // The decoded pixels upright, then the framed area of them onto the bitmap.
        val matrix = upright(shape.orientation, decoded.width, decoded.height)
        val scale = (if (turned(shape.orientation)) decoded.height else decoded.width).toFloat() / shape.width
        matrix.postConcat(
            Matrix().apply {
                setRectToRect(
                    RectF(area.left * scale, area.top * scale, area.right * scale, area.bottom * scale),
                    RectF(0f, 0f, width.toFloat(), height.toFloat()),
                    Matrix.ScaleToFit.FILL,
                )
            },
        )
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        canvas.drawBitmap(decoded, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
        decoded.recycle()
        return bitmap
    }

    /**
     * [image] blurred in place, as [blur] says: drawn at an eighth of its size, blurred there (a
     * box blur, twice each way, which looks like a Gaussian one), and drawn back up with
     * filtering, which smooths it further. Blurring the small copy costs little, whatever the
     * screen's size.
     */
    fun blur(image: Bitmap, blur: Blur) {
        val radius = when (blur) {
            Blur.OFF -> return
            Blur.LIGHT -> 1
            Blur.STRONG -> 4
        }
        val width = (image.width / BLUR_SCALE).coerceAtLeast(1)
        val height = (image.height / BLUR_SCALE).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(image, width, height, true)
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        val scratch = IntArray(pixels.size)
        repeat(2) {
            boxBlur(pixels, scratch, width, height, radius, horizontal = true)
            boxBlur(scratch, pixels, width, height, radius, horizontal = false)
        }
        small.setPixels(pixels, 0, width, 0, 0, width, height)
        Canvas(image).drawBitmap(small, null, Rect(0, 0, image.width, image.height), Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
        small.recycle()
    }

    /** [image] darkened in place by [percent] percent, as if seen through that much black. */
    fun dim(image: Bitmap, percent: Int) {
        if (percent <= 0) return
        Canvas(image).drawColor(Color.argb(percent.coerceAtMost(100) * 255 / 100, 0, 0, 0))
    }

    /** Each pixel of [source] the average of those [radius] around it along one direction, edges repeated, into [target]. */
    private fun boxBlur(source: IntArray, target: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val window = 2 * radius + 1
        for (line in 0 until lines) {
            fun at(i: Int) = source[if (horizontal) line * width + i.coerceIn(0, length - 1) else i.coerceIn(0, length - 1) * width + line]
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val p = at(i)
                a += p ushr 24; r += p shr 16 and 0xFF; g += p shr 8 and 0xFF; b += p and 0xFF
            }
            for (i in 0 until length) {
                target[if (horizontal) line * width + i else i * width + line] =
                    (a / window shl 24) or (r / window shl 16) or (g / window shl 8) or (b / window)
                val out = at(i - radius)
                val into = at(i + radius + 1)
                a += (into ushr 24) - (out ushr 24)
                r += (into shr 16 and 0xFF) - (out shr 16 and 0xFF)
                g += (into shr 8 and 0xFF) - (out shr 8 and 0xFF)
                b += (into and 0xFF) - (out and 0xFF)
            }
        }
    }

    /**
     * The orientation tag of [file] (EXIF), which BitmapFactory doesn't apply: a phone's photo is
     * often stored on its side, with a tag saying how to turn it. Normal when there's none. A
     * JPEG's is read by [jpegOrientation]; a HEIF photo's (an iPhone's) by the platform's
     * ExifInterface, which reads HEIF from API 28 on, the first that decodes it, and which is
     * written in Java from API 24 on.
     */
    private fun orientation(file: File): Int {
        val start = read(file, EXIF_BYTES)
        if (start.size >= 2 && start[0] == 0xFF.toByte() && start[1] == 0xD8.toByte()) return jpegOrientation(start)
        if (Build.VERSION.SDK_INT < 28 || pictureFormatOf(start.copyOf(minOf(start.size, PICTURE_HEADER_BYTES))) != PictureFormat.HEIF) return NORMAL
        return heifOrientation(file)
    }

    @SuppressLint("ExifInterface")   // HEIF only, which decodes from API 28 on: the platform's Java one; see orientation
    private fun heifOrientation(file: File): Int =
        try {
            android.media.ExifInterface(file.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, NORMAL)
        } catch (e: IOException) {
            NORMAL
        } catch (e: RuntimeException) {
            NORMAL   // a damaged tag
        }

    /** The first [count] bytes of [file], fewer if it's shorter; none if it can't be read. */
    private fun read(file: File, count: Int): ByteArray {
        val bytes = ByteArray(count)
        val read = try {
            FileInputStream(file).use { input ->
                var total = 0
                while (total < count) {
                    val n = input.read(bytes, total, count - total)
                    if (n < 0) break
                    total += n
                }
                total
            }
        } catch (e: IOException) {
            0
        }
        return bytes.copyOf(read)
    }

    /** Whether the [orientation] tag turns the picture on its side, so its width is the stored height. */
    private fun turned(orientation: Int) = orientation in TRANSPOSE..ROTATE_270

    /** Maps a [width] x [height] bitmap as stored to how the [orientation] tag shows it, from the top left. */
    private fun upright(orientation: Int, width: Int, height: Int): Matrix {
        val w = width.toFloat()
        val h = height.toFloat()
        return Matrix().apply {
            // The EXIF tag's values, 1 being as stored.
            when (orientation) {
                2 -> { postScale(-1f, 1f); postTranslate(w, 0f) }                                               // flipped across
                3 -> { postRotate(180f); postTranslate(w, h) }
                4 -> { postScale(1f, -1f); postTranslate(0f, h) }                                               // flipped upside down
                TRANSPOSE -> { postRotate(90f); postTranslate(h, 0f); postScale(-1f, 1f); postTranslate(h, 0f) }
                6 -> { postRotate(90f); postTranslate(h, 0f) }                                                  // turned right
                7 -> { postRotate(270f); postTranslate(0f, w); postScale(-1f, 1f); postTranslate(h, 0f) }       // transverse
                ROTATE_270 -> { postRotate(270f); postTranslate(0f, w) }
            }
        }
    }

    /** The largest power of 2 at most [scale]: decoded that much smaller, the picture is still at least as large as needed. */
    private fun sampleSize(scale: Float): Int {
        var sample = 1
        while (sample * 2 <= scale) sample *= 2
        return sample
    }

    /** How much smaller [blur] blurs a picture. */
    private const val BLUR_SCALE = 8

    /** The longest side [decodes] decodes at, at most. */
    private const val CHECK_SIZE = 512

    /** How much of a file's start the orientation tag is looked for in: a JPEG's EXIF segment is at most 64 KB. */
    private const val EXIF_BYTES = 70 * 1024

    // EXIF orientation tag values: as stored, and the first and last that turn a picture on its side.
    private const val NORMAL = 1
    private const val TRANSPOSE = 5
    private const val ROTATE_270 = 8
}
