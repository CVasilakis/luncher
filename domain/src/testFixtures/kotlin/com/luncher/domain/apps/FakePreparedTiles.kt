package com.luncher.domain.apps

import com.luncher.domain.jobs.ManualJobs
import com.luncher.domain.pictures.FakePreparedPictures
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PreparedPictures
import com.luncher.domain.pictures.PreparingCause
import com.luncher.domain.pictures.isPrepared
import java.util.Collections

/**
 * [PreparedTiles] in memory: each app's tile kept for the drawing it was prepared for ([kept]),
 * read back as [draw] draws it, with the shape [shape] makes of that where the drawing asks for it
 * ([TileDrawing.withShape], the pictures [seeThrough]). A tile whose picture's copy [copies] doesn't have prepared isn't
 * prepared ([TilePreparing.COPY_MISSING]); those of [damagedCopies] find their copy damaged, once
 * each; [failing] apps' can't be kept. With [jobs], everything slow fails unless a job runs it: the
 * main thread never prepares. Safe on a real background thread too (the instrumented tests): its
 * collections are synchronized.
 */
class FakePreparedTiles<Image>(
    private val copies: PreparedPictures? = null,
    private val jobs: ManualJobs? = null,
    private val shape: (Image) -> ShapeMasks<Image>? = { null },
    private val draw: (TileDrawing) -> Image,
) : PreparedTiles<Image> {

    /** What each app's tile is kept for. */
    val kept: MutableMap<LaunchableApp, TileDrawing> = Collections.synchronizedMap(LinkedHashMap())

    /** Apps whose tile can't be kept (a full disk). */
    val failing: MutableSet<LaunchableApp> = Collections.synchronizedSet(LinkedHashSet())

    /** Pictures whose copy is found damaged by the next tile prepared of it: it's then missing. */
    val damagedCopies: MutableSet<PictureId> = Collections.synchronizedSet(LinkedHashSet())

    /** Pictures whose tiles are see-through. */
    val seeThrough: MutableSet<PictureId> = Collections.synchronizedSet(LinkedHashSet())

    /** Apps whose kept tile doesn't read. */
    val damaged: MutableSet<LaunchableApp> = Collections.synchronizedSet(LinkedHashSet())

    /** Every tile [prepare] prepared and kept, in order. */
    val prepared: MutableList<Pair<LaunchableApp, TileDrawing>> = Collections.synchronizedList(ArrayList())

    /** Every app whose tile [delete] deleted, and the apps [deleteAllBut] kept, in order. */
    val deleted: MutableList<LaunchableApp> = Collections.synchronizedList(ArrayList())
    val keptOnly: MutableList<Set<LaunchableApp>> = Collections.synchronizedList(ArrayList())

    /** How many times a tile was asked about ([whyPrepare]). */
    @Volatile
    var asked = 0
        private set

    override fun whyPrepare(app: LaunchableApp, drawing: TileDrawing): PreparingCause? {
        asked++
        val made = kept[app] ?: return PreparingCause.OUTDATED
        return when {
            made == drawing -> null
            made.copy(size = drawing.size) == drawing -> PreparingCause.SCREEN_CHANGED
            else -> PreparingCause.OUTDATED
        }
    }

    override fun prepare(app: LaunchableApp, drawing: TileDrawing): TilePreparing {
        inJob()
        if (copies?.isPrepared(drawing.picture) == false) return TilePreparing.COPY_MISSING
        if (damagedCopies.remove(drawing.picture)) {
            (copies as? FakePreparedPictures)?.missing?.add(drawing.picture)
            return TilePreparing.COPY_DAMAGED
        }
        if (app in failing) return TilePreparing.NOT_WRITTEN
        kept[app] = drawing
        damaged -= app
        prepared += app to drawing
        return TilePreparing.DONE
    }

    override fun make(drawing: TileDrawing, stopped: () -> Boolean): PreparedTile<Image>? {
        inJob()
        if (stopped() || copies?.isPrepared(drawing.picture) == false) return null
        return tile(drawing)
    }

    override fun read(app: LaunchableApp, drawing: TileDrawing): TileRead<Image> = when {
        kept[app] != drawing -> TileRead.NotPrepared
        app in damaged -> TileRead.Damaged
        else -> TileRead.Ready(tile(drawing))
    }

    private fun tile(drawing: TileDrawing): PreparedTile<Image> {
        val image = draw(drawing)
        val seeThrough = drawing.picture in seeThrough
        return PreparedTile(image, seeThrough, if (drawing.withShape(seeThrough)) shape(image) else null)
    }

    override fun delete(app: LaunchableApp) {
        inJob()
        kept -= app
        deleted += app
    }

    override fun deleteAllBut(apps: Set<LaunchableApp>) {
        inJob()
        synchronized(kept) { kept.keys.retainAll(apps) }
        keptOnly += apps
    }

    private fun inJob() = check(jobs == null || jobs.running) { "slow work asked for outside a job" }
}
