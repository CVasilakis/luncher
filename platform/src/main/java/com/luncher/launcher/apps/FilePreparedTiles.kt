package com.luncher.launcher.apps

import android.content.Context
import android.graphics.Bitmap
import com.luncher.domain.apps.LaunchableApp
import com.luncher.domain.apps.PreparedTile
import com.luncher.domain.apps.PreparedTiles
import com.luncher.domain.apps.ShapeMasks
import com.luncher.domain.apps.TileDrawing
import com.luncher.domain.apps.TileMask
import com.luncher.domain.apps.TilePreparing
import com.luncher.domain.apps.TileRead
import com.luncher.domain.pictures.PreparingCause
import com.luncher.launcher.images.PixelFiles
import com.luncher.launcher.pictures.FilePictureImages
import com.luncher.launcher.pictures.WorkingCopies
import com.luncher.launcher.ui.ScreenSize
import com.luncher.launcher.ui.TileShapes
import com.luncher.launcher.ui.color
import com.luncher.launcher.ui.screenSize
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import com.luncher.launcher.ui.R as UiR

/**
 * [PreparedTiles] on files: each app's tile's picture of the library, drawn from the picture's
 * working copy ([WorkingCopies]) at the tile's size, never from the original, and kept in [folder],
 * one file per app, so the home screen's main thread only reads it. A see-through picture is drawn
 * over nothing, its transparent parts kept, any other over the background, as before; where the
 * tile's drawing asks for its shape ([TileDrawing.withShape]), its band and shadow ([TileShapes])
 * are kept with it, at half its scale. In filesDir: Android never deletes it, as it may a cache, so
 * a start never finds a tile missing that was prepared, and never shows a window for it.
 *
 * The file format: [PixelFiles], its header what the tile was made for (the picture, its framing,
 * the tile's size, [FilePictureImages.DRAWING] and the copies' [WorkingCopies.FORMAT]), whether
 * it's see-through, and whether its shape is kept, with its masks' sizes and places; then the
 * image, and the masks. One kept for anything else isn't prepared for it: another size is another
 * screen, the rest outdated. A copy found damaged as a tile is drawn is deleted, to be made again.
 *
 * Safe in any job: it keeps nothing, but the buffers [read] reuses on the main thread.
 */
class FilePreparedTiles(
    context: Context,
    private val folder: File = File(context.filesDir, FOLDER),
    copies: File = File(context.filesDir, WorkingCopies.FOLDER),
    screen: () -> ScreenSize = { context.applicationContext.screenSize() },
) : PreparedTiles<Bitmap> {

    private val workingCopies = WorkingCopies(copies, context.color(UiR.color.background), screen)
    private val shapes = TileShapes(context)

    /** For the main thread's reads, whose buffers it reuses; the jobs' writes allocate their own. */
    private val pixelFiles = PixelFiles(MAGIC, FORMAT)

    /** Where a mask of a tile's file goes on the tile, and its size, at half the tile's scale. */
    private class Placed(val width: Int, val height: Int, val left: Int, val top: Int)

    /** What a file's tile was made for, as its header says. */
    private class Made(
        val picture: String,
        val fit: Int,
        val position: Int,
        val width: Int,
        val height: Int,
        val drawing: Int,
        val copies: Int,
        val seeThrough: Boolean,
        /** The band's and the shadow's places, if the picture's shape is kept. */
        val band: Placed?,
        val shadow: Placed?,
    )

    /** What [prepare] and [make] draw: a tile, or why there's none. */
    private sealed interface Drawn {
        class Tile(val tile: PreparedTile<Bitmap>) : Drawn

        data object CopyMissing : Drawn

        data object CopyDamaged : Drawn
    }

    override fun whyPrepare(app: LaunchableApp, drawing: TileDrawing): PreparingCause? =
        whyPrepare(pixelFiles.header(fileOf(app), ::readMade), drawing)

    override fun prepare(app: LaunchableApp, drawing: TileDrawing): TilePreparing {
        val tile = when (val drawn = draw(drawing)) {
            is Drawn.Tile -> drawn.tile
            Drawn.CopyMissing -> return TilePreparing.COPY_MISSING
            Drawn.CopyDamaged -> return TilePreparing.COPY_DAMAGED
        }
        val shape = tile.shape
        val images = if (shape == null) listOf(tile.image) else listOf(tile.image, shape.band.image, shape.shadow.image)
        try {
            val written = PixelFiles(MAGIC, FORMAT).writeAll(fileOf(app), images) { output -> writeMade(output, drawing, tile) }
            return if (written) TilePreparing.DONE else TilePreparing.NOT_WRITTEN
        } finally {
            for (image in images) image.recycle()
        }
    }

    override fun make(drawing: TileDrawing, stopped: () -> Boolean): PreparedTile<Bitmap>? {
        if (stopped()) return null
        val tile = (draw(drawing) as? Drawn.Tile)?.tile ?: return null
        if (!stopped()) return tile
        tile.image.recycle()
        tile.shape?.let {
            it.band.image.recycle()
            it.shadow.image.recycle()
        }
        return null
    }

    override fun read(app: LaunchableApp, drawing: TileDrawing): TileRead<Bitmap> {
        var made: Made? = null
        val images = pixelFiles.readAll(fileOf(app)) { input ->
            val header = readMade(input)
            if (whyPrepare(header, drawing) != null) return@readAll null
            made = header
            listOfNotNull(PixelFiles.Shape(header.width, header.height), header.band?.let(::maskShape), header.shadow?.let(::maskShape))
        }
        val header = made ?: return TileRead.NotPrepared
        if (images == null) return TileRead.Damaged
        val band = header.band
        val shadow = header.shadow
        val shape = if (band != null && shadow != null) ShapeMasks(TileMask(images[1], band.left, band.top), TileMask(images[2], shadow.left, shadow.top)) else null
        return TileRead.Ready(PreparedTile(images[0], header.seeThrough, shape))
    }

    override fun delete(app: LaunchableApp) {
        val file = fileOf(app)
        file.delete()
        file.parentFile?.delete()   // only if it's empty now
    }

    override fun deleteAllBut(apps: Set<LaunchableApp>) {
        for (packageFolder in folder.listFiles().orEmpty()) {
            for (file in packageFolder.listFiles().orEmpty()) {
                // A write that didn't finish too: its name isn't an app's.
                if (LaunchableApp(packageFolder.name, file.name) !in apps) file.delete()
            }
            packageFolder.delete()   // only if it's empty now
        }
    }

    /**
     * [drawing]'s tile, from its picture's working copy: none where the copy isn't made for this
     * screen; one that doesn't decode is deleted, here, as nothing else writes it meanwhile (the
     * jobs run one at a time).
     */
    private fun draw(drawing: TileDrawing): Drawn {
        val picture = drawing.picture.value
        val size = drawing.size
        return when (val tile = workingCopies.drawTile(picture, size.width, size.height, drawing.framing)) {
            is WorkingCopies.Tile.Drawn -> {
                val shape = if (drawing.withShape(tile.seeThrough)) shapes.make(tile.image) else null
                Drawn.Tile(PreparedTile(tile.image, tile.seeThrough, shape))
            }
            WorkingCopies.Tile.NotMade -> Drawn.CopyMissing
            WorkingCopies.Tile.Damaged -> {
                workingCopies.fileOf(picture).delete()
                Drawn.CopyDamaged
            }
        }
    }

    /**
     * Why a tile [made] as its file's header says isn't prepared for [drawing], or null if it is:
     * another size is another screen; none, or another picture, framing, shape, drawing or copy, is
     * outdated. An opaque picture's shape is its tile's, so with it the focus asks for nothing.
     */
    private fun whyPrepare(made: Made?, drawing: TileDrawing): PreparingCause? {
        if (made == null || made.picture != drawing.picture.value || made.fit != drawing.framing.fit.ordinal ||
            made.position != drawing.framing.position.ordinal || made.drawing != FilePictureImages.DRAWING ||
            made.copies != WorkingCopies.FORMAT || (made.band != null) != drawing.withShape(made.seeThrough)
        ) {
            return PreparingCause.OUTDATED
        }
        return if (made.width == drawing.size.width && made.height == drawing.size.height) null else PreparingCause.SCREEN_CHANGED
    }

    private fun maskShape(placed: Placed) = PixelFiles.Shape(placed.width, placed.height, Bitmap.Config.ALPHA_8)

    private fun readMade(input: DataInputStream): Made {
        val picture = input.readUTF()
        val fit = input.readInt()
        val position = input.readInt()
        val width = input.readInt()
        val height = input.readInt()
        val drawing = input.readInt()
        val copies = input.readInt()
        val seeThrough = input.readBoolean()
        val shape = input.readBoolean()
        val band = if (shape) readPlaced(input) else null
        val shadow = if (shape) readPlaced(input) else null
        return Made(picture, fit, position, width, height, drawing, copies, seeThrough, band, shadow)
    }

    private fun readPlaced(input: DataInputStream) = Placed(input.readInt(), input.readInt(), input.readInt(), input.readInt())

    /** [readMade]'s header, of [tile] drawn for [drawing]. */
    private fun writeMade(output: DataOutputStream, drawing: TileDrawing, tile: PreparedTile<Bitmap>) {
        output.writeUTF(drawing.picture.value)
        output.writeInt(drawing.framing.fit.ordinal)
        output.writeInt(drawing.framing.position.ordinal)
        output.writeInt(drawing.size.width)
        output.writeInt(drawing.size.height)
        output.writeInt(FilePictureImages.DRAWING)
        output.writeInt(WorkingCopies.FORMAT)
        output.writeBoolean(tile.seeThrough)
        val shape = tile.shape
        output.writeBoolean(shape != null)
        if (shape != null) {
            writePlaced(output, shape.band)
            writePlaced(output, shape.shadow)
        }
    }

    private fun writePlaced(output: DataOutputStream, mask: TileMask<Bitmap>) {
        output.writeInt(mask.image.width)
        output.writeInt(mask.image.height)
        output.writeInt(mask.left)
        output.writeInt(mask.top)
    }

    /** One folder per package, a file per activity: what [deleteAllBut] needs to find the app. */
    private fun fileOf(app: LaunchableApp) = File(File(folder, app.packageName), app.activityName)

    companion object {
        /** Their folder, in filesDir: Android doesn't delete it, as it may a cache. */
        const val FOLDER = "tile-pictures"

        /** "LTIL": a file of this class. */
        private const val MAGIC = 0x4C54494C

        /** The file format's version: another one isn't prepared, and is made again. */
        private const val FORMAT = 1
    }
}
