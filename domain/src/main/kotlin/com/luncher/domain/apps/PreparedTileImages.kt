package com.luncher.domain.apps

import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.Jobs
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PreparedPictures
import com.luncher.domain.pictures.PreparingCause

/**
 * The tiles' pictures of the library ([Banner.Picture]), prepared ahead and kept ([tiles]), so the
 * home screen's main thread only [read]s them: where one isn't prepared for its drawing (the TV
 * switched resolution, an update draws pictures otherwise, it was found damaged, the user saved
 * another picture, framing or focus), the tile shows its app's own image until it's prepared here.
 * One per process (AppGraph makes it), so the preparing goes on when a screen stops, and a screen
 * follows it while it shows, as a port it can watch
 * (docs/ARCHITECTURE.md#background-work-and-changes-while-shown, point 2).
 *
 * The home screen says what each tile is drawn from at each read of the apps ([prepare]); those
 * not prepared for it are, in that order, while [state] says why, and a listener redraws each tile
 * no longer [preparing]. A tile is prepared from its picture's copy ([copies]), the copy first
 * where it isn't prepared, one job each. They're asked about only when a drawing changed since,
 * so a return to the home screen costs nothing. A tile that can't be prepared (its picture's
 * original unreadable, a full disk) is given up for the process, keeping its app's image: it isn't
 * tried again and again. The tile banner picker's Save prepares the tile it saved through here too
 * ([save]), so the home screen, back meanwhile, waits for that and prepares nothing twice.
 *
 * What's kept is what the home screen's tiles show: a tile that no longer shows a picture (the
 * app uninstalled, its picture deleted, its own banner chosen again) has what's kept of it
 * deleted, as is, once per process, what no tile names.
 *
 * Used on the main thread only; [onMain] runs a job's result there.
 */
class PreparedTileImages<Image>(
    private val copies: PreparedPictures,
    private val tiles: PreparedTiles<Image>,
    private val jobs: Jobs,
    private val onMain: (() -> Unit) -> Unit,
) {

    /** Whether tiles' pictures are being prepared, and why. */
    sealed interface State {
        data object None : State

        data class Preparing(val cause: PreparingCause) : State

        /** A tile saved in the tile banner picker is being prepared: said before any other cause. */
        data object Saving : State
    }

    var state: State = State.None
        private set

    /** What [prepare] last checked: each tile's drawing. Null before the first, which deletes what no tile names. */
    private var checked: Map<LaunchableApp, TileDrawing>? = null

    /**
     * A tile being prepared for [drawing], and why: null [cause] for a tile saved, whose [done]
     * runs once it's prepared or given up. [copyTried]: its picture's copy was prepared for it, so a
     * copy still missing after that is given up.
     */
    private class Preparing(val drawing: TileDrawing, val cause: PreparingCause?, val done: (() -> Unit)?, var copyTried: Boolean)

    /** The tiles being prepared, in the order they came. */
    private val preparing = LinkedHashMap<LaunchableApp, Preparing>()

    /** The copies being prepared: the tiles queued after them wait for them, as jobs run in order. */
    private val copying = HashSet<PictureId>()

    /** Tiles that couldn't be prepared: left as they are for the process, whatever they're drawn from, until saved again. */
    private val givenUp = HashSet<LaunchableApp>()

    /** Pictures whose copy couldn't be prepared (the original unreadable, a full disk): their tiles are given up too. */
    private val givenUpCopies = HashSet<PictureId>()

    /** Pictures whose copy was prepared again after it was found damaged: found so again, its tile is given up. */
    private val remadeCopies = HashSet<PictureId>()

    /** Tiles prepared again after they were found damaged: found so again, they're given up. */
    private val remadeTiles = HashSet<LaunchableApp>()

    private val listeners = mutableListOf<() -> Unit>()

    /**
     * Prepares the tiles of [drawings] (each app's tile showing a picture, what it's drawn from)
     * that aren't prepared for it, and deletes what's kept of the tiles no longer among them;
     * nothing is asked when they're what it last asked for.
     */
    fun prepare(drawings: Map<LaunchableApp, TileDrawing>) {
        val last = checked
        if (drawings == last) return
        val wanted = LinkedHashMap(drawings)
        checked = wanted
        if (last == null) {
            val kept = HashSet(wanted.keys)
            jobs.run(JobPriority.PICTURE_SCREENS) { tiles.deleteAllBut(kept) }
        } else {
            for (app in last.keys) if (app !in wanted) jobs.run(JobPriority.PICTURE_SCREENS) { tiles.delete(app) }
        }
        var added = false
        for ((app, drawing) in wanted) {
            if (preparing[app]?.drawing == drawing || app in givenUp || drawing.picture in givenUpCopies) continue
            val cause = tiles.whyPrepare(app, drawing) ?: continue
            start(app, drawing, cause, done = null)
            added = true
        }
        if (added) changed()
    }

    /**
     * Prepares [app]'s tile for [drawing], just saved, while [state] says so, whatever was given up
     * of it before; [done] runs on the main thread once it's prepared, or can't be. False, and
     * [done] isn't called, if it's prepared already: nothing to wait for.
     */
    fun save(app: LaunchableApp, drawing: TileDrawing, done: () -> Unit): Boolean {
        givenUp -= app
        givenUpCopies -= drawing.picture
        remadeTiles.remove(app)
        if (tiles.whyPrepare(app, drawing) == null) return false
        start(app, drawing, cause = null, done)
        changed()
        return true
    }

    /** [app]'s tile shows no picture any more: what's kept of it is deleted, and it isn't prepared. */
    fun forget(app: LaunchableApp) {
        jobs.run(JobPriority.PICTURE_SCREENS) { tiles.delete(app) }
        if (preparing.remove(app) != null) changed()
    }

    /**
     * [app]'s tile as prepared for [drawing], or null where it isn't (yet): its app's own image
     * shows meanwhile. One that doesn't read is prepared again, once; damaged again after that,
     * it's given up.
     */
    fun read(app: LaunchableApp, drawing: TileDrawing): PreparedTile<Image>? =
        when (val read = tiles.read(app, drawing)) {
            is TileRead.Ready -> read.tile
            TileRead.NotPrepared -> null
            TileRead.Damaged -> {
                damaged(app, drawing)
                null
            }
        }

    /** Whether [app]'s tile is being prepared: it shows its app's image meanwhile. */
    fun preparing(app: LaunchableApp): Boolean = app in preparing

    /** Calls [listener] on the main thread whenever a tile is prepared, or given up, and when [state] changes. */
    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    private fun damaged(app: LaunchableApp, drawing: TileDrawing) {
        if (app in preparing || app in givenUp) return
        if (!remadeTiles.add(app)) {
            givenUp += app
            return
        }
        start(app, drawing, PreparingCause.OUTDATED, done = null)
        changed()
    }

    private fun start(app: LaunchableApp, drawing: TileDrawing, cause: PreparingCause?, done: (() -> Unit)?) {
        val entry = Preparing(drawing, cause, done, copyTried = false)
        preparing[app] = entry
        entry.copyTried = copyFirst(drawing.picture)
        queue(app, drawing)
    }

    /**
     * Prepares [picture]'s copy, in a job before the tiles queued after it, unless it's prepared or
     * being prepared; whether it was tried for them: being prepared now, or given up.
     */
    private fun copyFirst(picture: PictureId): Boolean {
        if (picture in copying || picture in givenUpCopies) return true
        if (copies.whyPrepare(picture) == null) return false
        copying += picture
        // Nothing stops it: a picture prepared serves every screen that shows it.
        jobs.run(JobPriority.PICTURE_SCREENS) {
            val done = copies.prepare(picture) { false }
            onMain {
                copying -= picture
                if (!done) givenUpCopies += picture
            }
        }
        return true
    }

    private fun queue(app: LaunchableApp, drawing: TileDrawing) {
        jobs.run(JobPriority.PICTURE_SCREENS) {
            val result = tiles.prepare(app, drawing)
            onMain { finished(app, drawing, result) }
        }
    }

    private fun finished(app: LaunchableApp, drawing: TileDrawing, result: TilePreparing) {
        // Forgotten, or another drawing asked for since: that one's job comes later.
        val entry = preparing[app]?.takeIf { it.drawing == drawing } ?: return
        val again = when (result) {
            TilePreparing.DONE -> false
            TilePreparing.COPY_MISSING -> !entry.copyTried
            TilePreparing.COPY_DAMAGED -> remadeCopies.add(drawing.picture)
            TilePreparing.NOT_WRITTEN -> false
        }
        if (again) {
            // Once: a copy still missing after this, though prepared, isn't tried again and again.
            copyFirst(drawing.picture)
            entry.copyTried = true
            return queue(app, drawing)
        }
        if (result != TilePreparing.DONE) givenUp += app
        preparing.remove(app)
        changed()
        entry.done?.invoke()
    }

    /**
     * The state as the tiles being prepared say: saving, if one was saved; else the screen
     * changed, if that's why for every one, else outdated; then the listeners.
     */
    private fun changed() {
        state = when {
            preparing.isEmpty() -> State.None
            preparing.values.any { it.cause == null } -> State.Saving
            preparing.values.all { it.cause == PreparingCause.SCREEN_CHANGED } -> State.Preparing(PreparingCause.SCREEN_CHANGED)
            else -> State.Preparing(PreparingCause.OUTDATED)
        }
        for (listener in listeners.toList()) listener()
    }
}
