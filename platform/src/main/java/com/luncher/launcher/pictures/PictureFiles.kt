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
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.pictures.frame
import com.luncher.domain.pictures.pictureFormatOf
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.IntBuffer
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
     * [file] decoded with [options], or null, also once [stopped] says so: it's read through a
     * stream whose reads fail from then on, which ends the decoder at its next read, within
     * milliseconds on every API level, where a decode can't be interrupted otherwise
     * (`requestCancelDecode` does nothing from API 24 on). What it decoded so far is dropped.
     */
    private fun decode(file: File, options: BitmapFactory.Options, stopped: () -> Boolean): Bitmap? {
        val bitmap = try {
            StoppableInput(FileInputStream(file), stopped).use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: IOException) {
            null
        }
        if (bitmap != null && stopped()) {
            bitmap.recycle()
            return null
        }
        return bitmap
    }

    /** [input], whose every read fails once [stopped] says so. */
    private class StoppableInput(input: InputStream, private val stopped: () -> Boolean) : FilterInputStream(input) {

        override fun read(): Int {
            check()
            return super.read()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            check()
            return super.read(b, off, len)
        }

        override fun skip(n: Long): Long {
            check()
            return super.skip(n)
        }

        private fun check() {
            if (stopped()) throw IOException("stopped")
        }
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
     * Null too once [stopped] says so: the decode stops then ([decode]).
     */
    fun draw(file: File, width: Int, height: Int, framing: Framing, background: Int, stopped: () -> Boolean = { false }): Bitmap? {
        val shape = shape(file) ?: return null
        val area = frame(shape.width, shape.height, width, height, framing)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(minOf((area.right - area.left) / width, (area.bottom - area.top) / height))
            inPreferredConfig = Bitmap.Config.ARGB_8888
            if (Build.VERSION.SDK_INT >= 26) inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        val decoded = decode(file, options, stopped) ?: return null
        // A cut-off JPEG decoded at a sample size leaves its missing rows transparent in a bitmap
        // said to be opaque, which drawing would copy through the background as they are.
        decoded.setHasAlpha(true)
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
     * The part of [source] (already upright) that [framing] puts into [width] x [height] px, drawn
     * into a new ARGB_8888 bitmap of that size over [background], filtered: a working copy's
     * framing, as [draw] frames a file.
     */
    fun draw(source: Bitmap, width: Int, height: Int, framing: Framing, background: Int): Bitmap {
        val area = frame(source.width, source.height, width, height, framing)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        val matrix = Matrix().apply {
            setRectToRect(RectF(area.left, area.top, area.right, area.bottom), RectF(0f, 0f, width.toFloat(), height.toFloat()), Matrix.ScaleToFit.FILL)
        }
        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
        return bitmap
    }

    /**
     * A picture drawn at [width] x [height] px in [style], from what [framed] draws of it, framed,
     * at a size it's given; null if that's null. Not blurred, it's drawn at the size and dimmed. A
     * blurred one is drawn at half the size, which the blur's softness hides: it decodes smaller,
     * and blurring and dimming it costs a quarter; then it's drawn up to the size, filtered and
     * dithered, the dim's fill already in it, so the dither covers that too.
     */
    fun styled(width: Int, height: Int, style: PictureStyle, framed: (width: Int, height: Int) -> Bitmap?): Bitmap? {
        if (style.blur == Blur.OFF) return framed(width, height)?.also { dim(it, style.dim) }
        val half = framed((width + 1) / 2, (height + 1) / 2) ?: return null
        blur(half, style.blur)
        dim(half, style.dim)
        return enlarge(half, width, height)
    }

    /**
     * [image] blurred in place, as [blur] says: [Blur.spread] of its width. Three box blurs each
     * way, which add up to nearly a Gaussian blur, each a running sum, so the cost doesn't depend on
     * how strong it is. In place, in one array of the pixels: the rows through a line buffer, the
     * columns by a running sum per column, top to bottom, keeping only the rows the sums still
     * need as they were. [image] must be opaque, as [draw] makes it: its alpha isn't blurred.
     */
    fun blur(image: Bitmap, blur: Blur) {
        if (blur == Blur.OFF) return
        val width = image.width
        val height = image.height
        val radii = boxRadii(blur.spread * width)
        val pixels = IntArray(width * height)
        // As stored, without the conversion getPixels makes: red and blue may be swapped, which a blur doesn't mind.
        image.copyPixelsToBuffer(IntBuffer.wrap(pixels))
        val most = radii.max()
        val line = IntArray(width + 2 * most + 1)
        for (y in 0 until height) for (radius in radii) blurRow(pixels, y * width, width, line, radius)
        val columns = Columns(width, most)
        for (radius in radii) columns.blur(pixels, height, radius)
        image.copyPixelsFromBuffer(IntBuffer.wrap(pixels))
    }

    /** [image] darkened in place by [percent] percent, as if seen through that much black. */
    fun dim(image: Bitmap, percent: Int) {
        if (percent <= 0) return
        Canvas(image).drawColor(Color.argb(percent.coerceAtMost(100) * 255 / 100, 0, 0, 0))
    }

    /** [image] drawn into a new bitmap of [width] x [height] px, filtered and dithered, and freed. */
    fun enlarge(image: Bitmap, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawBitmap(image, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
        image.recycle()
        return bitmap
    }

    /**
     * The radii of three box blurs that together blur about as much as a Gaussian blur of standard
     * deviation [sigma] px: boxes of two widths next to each other, the mix whose variances add
     * up closest to sigma² (Kutskir's "boxesForGauss"); at most [MOST_RADIUS], which only a
     * picture drawn wider than 8000 px would reach.
     */
    private fun boxRadii(sigma: Double): IntArray {
        val ideal = sqrt(12 * sigma * sigma / BOXES + 1)
        var lower = floor(ideal).toInt()
        if (lower % 2 == 0) lower--
        val narrow = ((12 * sigma * sigma - BOXES * lower * lower - 4 * BOXES * lower - 3 * BOXES) / (-4.0 * lower - 4)).roundToInt()
        return IntArray(BOXES) { i -> (((if (i < narrow) lower else lower + 2) - 1) / 2).coerceAtMost(MOST_RADIUS) }
    }

    /**
     * The [width] pixels of [pixels] from [start] each the average of those [radius] around it, the
     * edge pixels repeated, through [line]. Red and blue are summed in one Int, each in 16 bits,
     * which hold the sum of up to 257 pixels; a multiplication stands for the division.
     */
    private fun blurRow(pixels: IntArray, start: Int, width: Int, line: IntArray, radius: Int) {
        if (radius == 0) return
        line.fill(pixels[start], 0, radius)
        pixels.copyInto(line, radius, start, start + width)
        line.fill(pixels[start + width - 1], radius + width, radius + width + radius + 1)
        val times = DIVIDE / (2 * radius + 1) + 1
        var redBlue = 0
        var green = 0
        for (i in 0..2 * radius) {
            redBlue += line[i] and RED_BLUE
            green += line[i] shr 8 and 0xFF
        }
        for (x in 0 until width) {
            pixels[start + x] = average(redBlue, green, times)
            val into = line[x + 2 * radius + 1]
            val out = line[x]
            redBlue += (into and RED_BLUE) - (out and RED_BLUE)
            green += (into shr 8 and 0xFF) - (out shr 8 and 0xFF)
        }
    }

    /** The opaque pixel whose channels are the sums, each times [times] / 65536: their average. */
    private fun average(redBlue: Int, green: Int, times: Int) =
        OPAQUE or ((redBlue ushr 16) * times ushr 16 shl 16) or (green * times ushr 16 shl 8) or ((redBlue and 0xFFFF) * times ushr 16)

    /** Box blurs down the columns of a picture [width] px wide, of radius [most] at most, as [blurRow] across. */
    private class Columns(private val width: Int, most: Int) {
        private val redBlue = IntArray(width)
        private val green = IntArray(width)

        /** The last rows as they were before they were blurred, as many as the sums still need. */
        private val kept = Array(most + 1) { IntArray(width) }

        fun blur(pixels: IntArray, height: Int, radius: Int) {
            if (radius == 0) return
            val times = DIVIDE / (2 * radius + 1) + 1
            redBlue.fill(0)
            green.fill(0)
            for (i in -radius..radius) {
                val row = i.coerceIn(0, height - 1) * width
                for (x in 0 until width) {
                    redBlue[x] += pixels[row + x] and RED_BLUE
                    green[x] += pixels[row + x] shr 8 and 0xFF
                }
            }
            for (y in 0 until height) {
                val row = y * width
                pixels.copyInto(kept[y % (radius + 1)], 0, row, row + width)
                for (x in 0 until width) pixels[row + x] = average(redBlue[x], green[x], times)
                if (y == height - 1) break
                // The row coming into the sums isn't blurred yet; the one leaving is, so it's read as kept.
                val into = (y + radius + 1).coerceAtMost(height - 1) * width
                val out = kept[(y - radius).coerceAtLeast(0) % (radius + 1)]
                for (x in 0 until width) {
                    redBlue[x] += (pixels[into + x] and RED_BLUE) - (out[x] and RED_BLUE)
                    green[x] += (pixels[into + x] shr 8 and 0xFF) - (out[x] shr 8 and 0xFF)
                }
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

    /** How many box blurs [blur] adds up, each way. */
    private const val BOXES = 3

    /** The widest box [blur] can sum, 2 x 128 + 1 = 257 pixels: what 16 bits a channel hold. */
    private const val MOST_RADIUS = 128

    /** 1 in 16 bits: [blur]'s averages multiply by this over the box's width, then shift it away. */
    private const val DIVIDE = 65536

    /** The two channels [blur] sums in one Int, 16 bits apart. */
    private const val RED_BLUE = 0xFF00FF

    private const val OPAQUE = 0xFF shl 24

    /** How much of a file's start the orientation tag is looked for in: a JPEG's EXIF segment is at most 64 KB. */
    private const val EXIF_BYTES = 70 * 1024

    // EXIF orientation tag values: as stored, and the first and last that turn a picture on its side.
    private const val NORMAL = 1
    private const val TRANSPOSE = 5
    private const val ROTATE_270 = 8
}
