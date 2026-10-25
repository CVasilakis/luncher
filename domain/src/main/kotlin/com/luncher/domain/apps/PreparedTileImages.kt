package com.luncher.domain.apps

import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.Jobs
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PreparedPictures
import com.luncher.domain.pictures.PreparingCause

/**
 * The pictures the tiles show ([TileImage]), prepared for the screen as it is now: a tile's
 * picture is drawn from what [prepared] keeps of it, and where that's missing (the TV switched
 * resolution, an update draws pictures otherwise, it was found damaged), the tile shows its app's
 * own image until it's prepared again here. One per process (AppGraph makes it), so the preparing
 * goes on when the home screen stops, and a screen follows it while it shows, as a port it can
 * watch (docs/ARCHITECTURE.md#background-work-and-changes-while-shown, point 2).
 *
 * The home screen says which pictures its tiles show, and the screen's size, at each read of the
 * apps ([prepare]); those that aren't prepared are, one job each, in that order, while [state] says
 * why, and a listener redraws each tile whose picture is no longer [preparing]. They're asked
 * about only when the pictures or the screen changed since, so a return to the home screen costs
 * nothing. A picture that can't be prepared (its original unreadable, a full disk) is given up
 * for the process, its tile keeping its app's image: it isn't tried again and again.
 *
 * Used on the main thread only; [onMain] runs a job's result there.
 */
class PreparedTileImages(
    private val prepared: PreparedPictures,
    private val jobs: Jobs,
    private val onMain: (() -> Unit) -> Unit,
) {

    /** Whether tiles' pictures are being prepared, and why. */
    sealed interface State {
        data object None : State

        data class Preparing(val cause: PreparingCause) : State
    }

    var state: State = State.None
        private set

    /** What [prepare] last checked: the pictures and the screen's size. */
    private var checked: Checked? = null

    private data class Checked(val pictures: Set<PictureId>, val width: Int, val height: Int)

    /** The pictures being prepared, in the order they came, each with why. */
    private val preparing = LinkedHashMap<PictureId, PreparingCause>()

    /** Pictures that couldn't be prepared: left as they are for the process. */
    private val givenUp = HashSet<PictureId>()

    /** Pictures prepared again after they were found damaged: found so again, they're given up. */
    private val remade = HashSet<PictureId>()

    private val listeners = mutableListOf<() -> Unit>()

    /**
     * Prepares those of [pictures], the tiles', that aren't prepared for a screen of [width] x
     * [height] px; nothing is asked when it asks for what it last did.
     */
    fun prepare(pictures: Set<PictureId>, width: Int, height: Int) {
        val wanted = Checked(pictures, width, height)
        if (wanted == checked) return
        checked = wanted
        var added = false
        for (picture in pictures) {
            if (picture in preparing || picture in givenUp) continue
            val cause = prepared.whyPrepare(picture) ?: continue
            start(picture, cause)
            added = true
        }
        if (added) changed()
    }

    /**
     * [picture]'s prepared copy was found damaged as a tile was drawn, and deleted: it's prepared
     * again, once; found damaged again after that, it's given up.
     */
    fun damaged(picture: PictureId) {
        if (picture in preparing || picture in givenUp) return
        if (!remade.add(picture)) {
            givenUp += picture
            return
        }
        start(picture, PreparingCause.OUTDATED)
        changed()
    }

    /** Whether [picture] is being prepared: its tile shows its app's image meanwhile. */
    fun preparing(picture: PictureId): Boolean = picture in preparing

    /** Calls [listener] on the main thread whenever a picture is prepared, or given up, and when [state] changes. */
    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    private fun start(picture: PictureId, cause: PreparingCause) {
        preparing[picture] = cause
        // Nothing stops it: a picture prepared serves every screen that shows it.
        jobs.run(JobPriority.PICTURE_SCREENS) {
            val done = prepared.prepare(picture) { false }
            onMain { finished(picture, done) }
        }
    }

    private fun finished(picture: PictureId, done: Boolean) {
        preparing.remove(picture) ?: return
        if (!done) givenUp += picture
        changed()
    }

    /**
     * The state as the pictures being prepared say: the screen changed, if that's why for every
     * one, else outdated; then the listeners.
     */
    private fun changed() {
        state = when {
            preparing.isEmpty() -> State.None
            preparing.values.all { it == PreparingCause.SCREEN_CHANGED } -> State.Preparing(PreparingCause.SCREEN_CHANGED)
            else -> State.Preparing(PreparingCause.OUTDATED)
        }
        for (listener in listeners.toList()) listener()
    }
}
