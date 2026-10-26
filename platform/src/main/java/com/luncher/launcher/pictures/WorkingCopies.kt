package com.luncher.launcher.pictures

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PreparingCause
import com.luncher.domain.pictures.frame
import com.luncher.launcher.ui.ScreenSize
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.ceil

/**
 * The library's working copies, in [folder]: each picture decoded once, whole and upright,
 * scaled to cover half the screen (what the screens show of it at most: the editors' previews),
 * and stored as a JPEG, from which its thumbnail and its previews are drawn in milliseconds
 * rather than by decoding the original again. A JPEG, as a working copy only ever stands for the
 * picture on the screens: a few hundred KB each, read in milliseconds, its losses invisible at
 * that size. The wallpaper is drawn from the original ([FilePictureImages]).
 *
 * A see-through picture's copy is a PNG instead, its transparent parts kept: lossless, alpha
 * included, on every version, and about a JPEG's size for a logo. See-through is decided here,
 * once: its decoder said the picture has alpha (a JPEG never does, so one cut short, whose missing
 * rows decode transparent, isn't), and the copy, drawn over nothing, has a pixel that isn't
 * opaque. Every other copy is drawn over [background], as the screens show it.
 *
 * One file per picture, named by its id, after a header saying what it was made for: [FORMAT],
 * [FilePictureImages.DRAWING] and the half of the [screen] it covers, in px; then whether it's
 * see-through, which says the image's format too. One made for anything else isn't [made], and
 * making it again replaces it, in one step (a new file, then renamed), so a TV that changed its
 * resolution leaves no copy of the old size behind. [FilesPictures] makes one as it adds a
 * picture, deletes it with the picture, and once per process deletes those of no picture. Safe on
 * any thread: it keeps nothing.
 */
internal class WorkingCopies(
    val folder: File,
    private val background: Int,
    private val screen: () -> ScreenSize,
) {

    /** How making a working copy went. */
    sealed interface Made {
        /** Made and written; of a picture that's [seeThrough] or not. */
        class Written(val seeThrough: Boolean) : Made

        /**
         * Decoded, but not written (a full disk): the picture is fine. As it's added, it's refused
         * ([FilesPictures]); a copy made again later (for another screen) is made once it can be,
         * the picture drawn from its original meanwhile ([WorkingCopyImages]).
         */
        data object NotWritten : Made

        /** Not decoded: not a picture this TV can show, or stopped. */
        data object NotDecoded : Made
    }

    /** How drawing a tile from a working copy went ([drawTile]). */
    sealed interface Tile {
        /** Drawn, at the size asked; of a picture that's [seeThrough], drawn over nothing, or not, over the background. */
        class Drawn(val image: Bitmap, val seeThrough: Boolean) : Tile

        /** No copy, or one made for another screen or drawing: it's made again, the file kept till then. */
        data object NotMade : Tile

        /** Made for this screen, but it doesn't decode. */
        data object Damaged : Tile
    }

    fun fileOf(picture: String) = File(folder, picture)

    /** The size a working copy covers: half the screen, in px. */
    private fun target(): Pair<Int, Int> = screen().let { (it.width + 1) / 2 to (it.height + 1) / 2 }

    /** Whether [picture]'s working copy is there, and made for the screen as it is now. */
    fun made(picture: String): Boolean = whyMake(picture) == null

    /**
     * Why [picture]'s working copy must be made again, or null if it's there and made for the
     * screen as it is now: one of this format and drawing made for another size is for another
     * screen; none, or one of another format or drawing, is outdated.
     */
    fun whyMake(picture: String): PreparingCause? {
        val made = header(fileOf(picture)) ?: return PreparingCause.OUTDATED
        return if (made.covers == target()) null else PreparingCause.SCREEN_CHANGED
    }

    /**
     * Whether [picture] is see-through, as its working copy says, made for any screen; null without
     * one of this format and drawing. Quick: its header only.
     */
    fun seeThrough(picture: String): Boolean? = header(fileOf(picture))?.seeThrough

    /**
     * [original], of [shape], decoded into a working copy written to [into], through a new file
     * renamed into place, a PNG if it's see-through, else a JPEG; stops once [stopped] says so.
     */
    fun make(original: File, shape: PictureFiles.Shape, into: File, stopped: () -> Boolean): Made {
        val (width, height) = target()
        // Covering the target, as a picture fills the screen; never larger than the picture itself.
        val scale = minOf(1.0, maxOf(width.toDouble() / shape.width, height.toDouble() / shape.height))
        val drawn = PictureFiles.drawKeepingAlpha(
            original,
            ceil(shape.width * scale).toInt().coerceAtLeast(1),
            ceil(shape.height * scale).toInt().coerceAtLeast(1),
            Framing(Fit.STRETCH),
            background,
            stopped,
        ) ?: return Made.NotDecoded
        val copy = drawn.image
        val seeThrough = drawn.alpha && !opaque(copy)
        val temporary = File(into.parentFile, into.name + TEMPORARY)
        try {
            into.parentFile?.mkdirs()
            DataOutputStream(BufferedOutputStream(FileOutputStream(temporary), BUFFER)).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(FORMAT)
                output.writeInt(FilePictureImages.DRAWING)
                output.writeInt(width)
                output.writeInt(height)
                output.writeInt(if (seeThrough) SEE_THROUGH else OPAQUE)
                val format = if (seeThrough) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                if (!copy.compress(format, QUALITY, output)) throw IOException("not written")
            }
            if (!temporary.renameTo(into)) throw IOException("not renamed")
            return Made.Written(seeThrough)
        } catch (e: IOException) {
            temporary.delete()
            return Made.NotWritten
        } finally {
            copy.recycle()
        }
    }

    /** [picture]'s working copy, if it was [made] for the screen as it is now; else null. */
    fun read(picture: String): Bitmap? {
        val file = fileOf(picture)
        if (!file.isFile) return null
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file), BUFFER)).use { input ->
                if (readHeader(input)?.covers != target()) return null
                BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            }
        } catch (e: IOException) {
            null
        }
    }

    /**
     * [picture]'s working copy, if it was [made] for the screen as it is now, drawn into a new
     * ARGB_8888 bitmap of [width] x [height] px as [framing] puts it: a tile's picture, a see-through
     * one over nothing, its transparent parts kept, any other over [background]. Decoded at no more
     * than the size needed (`inSampleSize`, as [PictureFiles.draw] picks it, after a decode of its
     * size), then framed and freed. Never from the original: where the copy isn't made for this
     * screen, [Tile.NotMade]; where its header is, but it doesn't decode, [Tile.Damaged]. A JPEG cut
     * short decodes without an error from API 24 on, so only some damage shows here.
     */
    fun drawTile(picture: String, width: Int, height: Int, framing: Framing): Tile {
        val file = fileOf(picture)
        if (!file.isFile) return Tile.NotMade
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val header = DataInputStream(BufferedInputStream(FileInputStream(file), BUFFER)).use { input ->
                val header = readHeader(input)?.takeIf { it.covers == target() } ?: return Tile.NotMade
                BitmapFactory.decodeStream(input, null, bounds)
                header
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return Tile.Damaged
            val area = frame(bounds.outWidth, bounds.outHeight, width, height, framing)
            val options = BitmapFactory.Options().apply {
                inSampleSize = PictureFiles.sampleSize(minOf((area.right - area.left) / width, (area.bottom - area.top) / height))
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = DataInputStream(BufferedInputStream(FileInputStream(file), BUFFER)).use { input ->
                input.skipBytes(HEADER_BYTES)
                BitmapFactory.decodeStream(input, null, options)
            } ?: return Tile.Damaged
            // A copy cut short leaves its missing rows transparent in a bitmap said to be opaque (PictureFiles.draw).
            decoded.setHasAlpha(true)
            try {
                val seeThrough = header.seeThrough
                return Tile.Drawn(PictureFiles.draw(decoded, width, height, framing, if (seeThrough) Color.TRANSPARENT else background), seeThrough)
            } finally {
                decoded.recycle()
            }
        } catch (e: IOException) {
            return Tile.Damaged
        }
    }

    /** Whether every pixel of [image] is opaque: row by row, up to the first that isn't. */
    private fun opaque(image: Bitmap): Boolean {
        val row = IntArray(image.width)
        for (y in 0 until image.height) {
            image.getPixels(row, 0, image.width, 0, y, image.width, 1)
            for (pixel in row) if (pixel ushr 24 != 0xFF) return false
        }
        return true
    }

    /** What a copy's header says: the size it covers, in px, and whether it's see-through. */
    private class Header(val covers: Pair<Int, Int>, val seeThrough: Boolean)

    private fun header(file: File): Header? {
        if (!file.isFile) return null
        return try {
            DataInputStream(FileInputStream(file)).use(::readHeader)
        } catch (e: IOException) {
            null
        }
    }

    /** What a file's copy was made for, if it's of this format and drawing; else null. */
    private fun readHeader(input: DataInputStream): Header? {
        if (input.readInt() != MAGIC || input.readInt() != FORMAT || input.readInt() != FilePictureImages.DRAWING) return null
        val covers = input.readInt() to input.readInt()
        return Header(covers, input.readInt() == SEE_THROUGH)
    }

    companion object {
        /** Their folder, in filesDir: Android doesn't delete it, as it may a cache, so they're made once. */
        const val FOLDER = "picture-copies"

        /** The ending of a copy being written: [FilesPictures]'s cleanup deletes those it finds. */
        const val TEMPORARY = ".new"

        /** "LWRK": a file of this class. */
        private const val MAGIC = 0x4C57524B

        /**
         * The file format's version; another one is a mismatch, made again. 2: see-through copies
         * kept as PNGs. What's drawn from a copy says it was drawn from this one ([FilePreparedTiles][com.luncher.launcher.apps.FilePreparedTiles]).
         */
        const val FORMAT = 2

        // Whether a copy is see-through, in its header: a PNG, else a JPEG.
        private const val OPAQUE = 0
        private const val SEE_THROUGH = 1

        /** Of the JPEG: its losses are invisible at a preview's size; 85 would save a third, with more artifacts. A PNG ignores it. */
        private const val QUALITY = 92

        private const val BUFFER = 64 * 1024

        /** The header's length: [MAGIC], [FORMAT], the drawing's version, the size covered and whether it's see-through, an Int each. */
        private const val HEADER_BYTES = 6 * 4
    }
}
