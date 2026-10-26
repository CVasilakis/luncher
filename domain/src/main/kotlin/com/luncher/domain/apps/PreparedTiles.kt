package com.luncher.domain.apps

import com.luncher.domain.layout.TileSize
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PreparedPictures
import com.luncher.domain.pictures.PreparingCause

/**
 * What a tile's picture is prepared from: the [picture] of the library, as [framing] puts it in a
 * tile of [size] px, and what the tile's [focus] follows, which decides whether the picture's
 * shape is prepared too ([withShape]). The tile's backdrop isn't in it: it's drawn live, so
 * changing it prepares nothing.
 */
data class TileDrawing(val picture: PictureId, val framing: Framing, val focus: TileFocusShape, val size: TileSize) {

    /**
     * Whether a tile of this drawing is prepared with its picture's shape ([PreparedTile.shape]):
     * only where the focus follows the shape of a picture that's [seeThrough]. An opaque picture's
     * shape is the tile's, so its focus changes nothing to prepare.
     */
    fun withShape(seeThrough: Boolean): Boolean = seeThrough && focus == TileFocusShape.SHAPE
}

/**
 * Port: each tile's picture of the library prepared ahead, one per app, and kept for good, so the
 * home screen's main thread only reads it, and never decodes a picture
 * (docs/ARCHITECTURE.md#background-work-and-changes-while-shown, point 7). A tile is prepared from
 * its picture's prepared copy ([PreparedPictures]), never the original: the copy first, then the
 * tile. [PreparedTileImages] says when, and is the only one that calls [prepare]. [Image] is the
 * UI's image type, as in [AppImages].
 */
interface PreparedTiles<Image> {

    /**
     * Why [app]'s tile isn't prepared for [drawing], or null if it is: quick, for the main thread.
     * Prepared for a tile of another size is for another screen; none, or for another picture,
     * framing or shape, or by another version, is outdated.
     */
    fun whyPrepare(app: LaunchableApp, drawing: TileDrawing): PreparingCause?

    /** Prepares [app]'s tile for [drawing], from its picture's copy, and keeps it: slow, a job. */
    fun prepare(app: LaunchableApp, drawing: TileDrawing): TilePreparing

    /**
     * A tile's parts for [drawing], from its picture's copy, kept nowhere: what a preview shows.
     * Slow, a job; null if the copy isn't prepared, or once [stopped] says so.
     */
    fun make(drawing: TileDrawing, stopped: () -> Boolean): PreparedTile<Image>?

    /** [app]'s tile as prepared for [drawing]: quick, for the main thread. */
    fun read(app: LaunchableApp, drawing: TileDrawing): TileRead<Image>

    /** Deletes what's kept of [app]'s tile: a job. */
    fun delete(app: LaunchableApp)

    /** Deletes what's kept of every tile but [apps]': a job. */
    fun deleteAllBut(apps: Set<LaunchableApp>)
}

/** How preparing a tile went ([PreparedTiles.prepare]). */
enum class TilePreparing {
    /** Prepared and kept. */
    DONE,

    /** Its picture's copy isn't prepared for this screen: the copy comes first. */
    COPY_MISSING,

    /** Its picture's copy doesn't decode: it's deleted, to be prepared again. */
    COPY_DAMAGED,

    /** Drawn, but there's no room to keep it (a full disk). */
    NOT_WRITTEN,
}

/** A tile read as prepared ([PreparedTiles.read]). */
sealed interface TileRead<out Image> {
    data class Ready<Image>(val tile: PreparedTile<Image>) : TileRead<Image>

    /** None kept, or one kept for another drawing. */
    data object NotPrepared : TileRead<Nothing>

    /** Kept for this drawing, but it doesn't read: to be prepared again. */
    data object Damaged : TileRead<Nothing>
}

/**
 * A tile's picture as prepared: its [image] at the tile's size, see-through parts kept, whether
 * it's [seeThrough], and the picture's [shape] where its drawing asks for it ([TileDrawing.withShape]).
 */
class PreparedTile<Image>(val image: Image, val seeThrough: Boolean, val shape: ShapeMasks<Image>?)

/**
 * A see-through picture's shape on its tile, what a focus that follows it is drawn with: a [band]
 * around its outline, for the frame, and its soft [shadow], for the lift. Both at half the tile's
 * scale: a blur hides it, and it takes a quarter of the memory.
 */
class ShapeMasks<Image>(val band: TileMask<Image>, val shadow: TileMask<Image>)

/** A mask of a tile's picture, at half the tile's scale, its top left corner at ([left], [top]) of the tile's, at that scale. */
class TileMask<Image>(val image: Image, val left: Int, val top: Int)
