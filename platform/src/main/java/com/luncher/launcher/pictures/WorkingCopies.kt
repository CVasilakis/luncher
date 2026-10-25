package com.luncher.launcher.pictures

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
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
 * One file per picture, named by its id, after a header saying what it was made for: [FORMAT],
 * [FilePictureImages.DRAWING] and the half of the [screen] it covers, in px. One made for anything
 * else isn't [made], and making it again replaces it, in one step (a new file, then renamed), so
 * a TV that changed its resolution leaves no copy of the old size behind. [FilesPictures] makes one
 * as it adds a picture, deletes it with the picture, and once per process deletes those of no
 * picture. Safe on any thread: it keeps nothing.
 */
internal class WorkingCopies(
    val folder: File,
    private val background: Int,
    private val screen: () -> ScreenSize,
) {

    /** How making a working copy went. */
    enum class Made {
        /** Made and written. */
        MADE,

        /**
         * Decoded, but not written (a full disk): the picture is fine. As it's added, it's refused
         * ([FilesPictures]); a copy made again later (for another screen) is made once it can be,
         * the picture drawn from its original meanwhile ([WorkingCopyImages]).
         */
        NOT_WRITTEN,

        /** Not decoded: not a picture this TV can show, or stopped. */
        NOT_DECODED,
    }

    /** How drawing a tile from a working copy went ([drawTile]). */
    sealed interface Tile {
        /** Drawn, at the size asked. */
        class Drawn(val image: Bitmap) : Tile

        /** No copy, or one made for another screen or drawing: it's made again, the file kept till then. */
        data object NotMade : Tile

        /** Made for this screen, but it doesn't decode: the file of [length] bytes, last changed at [modified]. */
        class Damaged(val length: Long, val modified: Long) : Tile
    }

    fun fileOf(picture: String) = File(folder, picture)

    /** The size a working copy covers: half the screen, in px. */
    private fun target(): Pair<Int, Int> = screen().let { (it.width + 1) / 2 to (it.height + 1) / 2 }

    /** Whether [picture]'s working copy is there, and made for the screen as it is now. */
    fun made(picture: String): Boolean = header(fileOf(picture)) == target()

    /**
     * [original], of [shape], decoded into a working copy written to [into], through a new file
     * renamed into place; stops once [stopped] says so.
     */
    fun make(original: File, shape: PictureFiles.Shape, into: File, stopped: () -> Boolean): Made {
        val (width, height) = target()
        // Covering the target, as a picture fills the screen; never larger than the picture itself.
        val scale = minOf(1.0, maxOf(width.toDouble() / shape.width, height.toDouble() / shape.height))
        val copy = PictureFiles.draw(
            original,
            ceil(shape.width * scale).toInt().coerceAtLeast(1),
            ceil(shape.height * scale).toInt().coerceAtLeast(1),
            Framing(Fit.STRETCH),
            background,
            stopped,
        ) ?: return Made.NOT_DECODED
        val temporary = File(into.parentFile, into.name + TEMPORARY)
        try {
            into.parentFile?.mkdirs()
            DataOutputStream(BufferedOutputStream(FileOutputStream(temporary), BUFFER)).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(FORMAT)
                output.writeInt(FilePictureImages.DRAWING)
                output.writeInt(width)
                output.writeInt(height)
                if (!copy.compress(Bitmap.CompressFormat.JPEG, QUALITY, output)) throw IOException("not written")
            }
            if (!temporary.renameTo(into)) throw IOException("not renamed")
            return Made.MADE
        } catch (e: IOException) {
            temporary.delete()
            return Made.NOT_WRITTEN
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
                if (readHeader(input) != target()) return null
                BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            }
        } catch (e: IOException) {
            null
        }
    }

    /**
     * [picture]'s working copy, if it was [made] for the screen as it is now, drawn into a new
     * ARGB_8888 bitmap of [width] x [height] px as [framing] puts it: a tile's picture. Decoded at no
     * more than the size needed (`inSampleSize`, as [PictureFiles.draw] picks it, after a decode of
     * its size), then framed and freed. Never from the original: where the copy isn't made for this
     * screen, [Tile.NotMade]; where its header is, but it doesn't decode, [Tile.Damaged]. A JPEG cut
     * short decodes without an error from API 24 on, so only some damage shows here.
     */
    fun drawTile(picture: String, width: Int, height: Int, framing: Framing): Tile {
        val file = fileOf(picture)
        if (!file.isFile) return Tile.NotMade
        val damaged = Tile.Damaged(file.length(), file.lastModified())
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            DataInputStream(BufferedInputStream(FileInputStream(file), BUFFER)).use { input ->
                if (readHeader(input) != target()) return Tile.NotMade
                BitmapFactory.decodeStream(input, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return damaged
            val area = frame(bounds.outWidth, bounds.outHeight, width, height, framing)
            val options = BitmapFactory.Options().apply {
                inSampleSize = PictureFiles.sampleSize(minOf((area.right - area.left) / width, (area.bottom - area.top) / height))
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = DataInputStream(BufferedInputStream(FileInputStream(file), BUFFER)).use { input ->
                input.skipBytes(HEADER_BYTES)
                BitmapFactory.decodeStream(input, null, options)
            } ?: return damaged
            // A copy cut short leaves its missing rows transparent in a bitmap said to be opaque (PictureFiles.draw).
            decoded.setHasAlpha(true)
            try {
                return Tile.Drawn(PictureFiles.draw(decoded, width, height, framing, background))
            } finally {
                decoded.recycle()
            }
        } catch (e: IOException) {
            return damaged
        }
    }

    private fun header(file: File): Pair<Int, Int>? {
        if (!file.isFile) return null
        return try {
            DataInputStream(FileInputStream(file)).use(::readHeader)
        } catch (e: IOException) {
            null
        }
    }

    /** The size a file's copy covers, if it's of this format and drawing; else null. */
    private fun readHeader(input: DataInputStream): Pair<Int, Int>? {
        if (input.readInt() != MAGIC || input.readInt() != FORMAT || input.readInt() != FilePictureImages.DRAWING) return null
        return input.readInt() to input.readInt()
    }

    companion object {
        /** Their folder, in filesDir: Android doesn't delete it, as it may a cache, so they're made once. */
        const val FOLDER = "picture-copies"

        /** The ending of a copy being written: [FilesPictures]'s cleanup deletes those it finds. */
        const val TEMPORARY = ".new"

        /** "LWRK": a file of this class. */
        private const val MAGIC = 0x4C57524B

        /** The file format's version; another one is a mismatch, made again. */
        private const val FORMAT = 1

        /** Of the JPEG: its losses are invisible at a preview's size; 85 would save a third, with more artifacts. */
        private const val QUALITY = 92

        private const val BUFFER = 64 * 1024

        /** The header's length: [MAGIC], [FORMAT], the drawing's version and the size covered, an Int each. */
        private const val HEADER_BYTES = 5 * 4
    }
}
