package com.luncher.domain.arrange

import com.luncher.domain.apps.ArrangedApps
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.layout.Direction
import com.luncher.domain.layout.TileMoves
import com.luncher.domain.layout.opposite

/**
 * The user arranging the home screen's apps: which app they hold, and where it goes as they move
 * it. The apps are in one order, the shown ones first and the hidden ones after them, on a shelf.
 * Moving the held app Down out of the shown apps' last row hides it, into the shelf's first row;
 * moving it Up out of the shelf's first row shows it again, in the shown apps' last row. Left and
 * Right never cross between the two. Right after a move Up or Down, the opposite key puts it back
 * where it was.
 *
 * Like [com.luncher.domain.layout.TileFocus], and unlike the other rules, a session keeps state and
 * changes it in place: each move is a key press, and handling a key press allocates nothing
 * (docs/ARCHITECTURE.md, rule 5). Only a move between the shown and the hidden apps creates the
 * [TileMoves] for their new sizes.
 */
class ArrangeSession(
    private val start: ArrangedApps,
    /** How tiles move among this many of them: the arrangement the screen shows them in. */
    private val movesFor: (tileCount: Int) -> TileMoves,
) {

    private val shownApps = ArrayList(start.shown)
    private val hiddenApps = ArrayList(start.hidden)
    private var shownMoves = movesFor(shownApps.size)
    private var hiddenMoves = movesFor(hiddenApps.size)

    /** The shown apps, in their current order. */
    val shown: List<InstalledApp> get() = shownApps

    /** The hidden apps, in their current order. */
    val hidden: List<InstalledApp> get() = hiddenApps

    /** The app the user holds, or null while they only move the focus. */
    var held: InstalledApp? = null
        private set

    private var heldIndex = -1       // among the shown or the hidden apps
    private var heldHidden = false
    private var movedSincePickUp = false
    private var moved = false

    // After a move Up or Down, the key back and where it takes the held app: where it came from,
    // even where another place is as near, so Down then Up is a round trip.
    private var backKey: Direction? = null
    private var backHidden = false
    private var backIndex = -1

    /** The held app's place among all apps, shown then hidden; -1 when none is held. */
    val heldPlace: Int
        get() = when {
            held == null -> -1
            heldHidden -> shownApps.size + heldIndex
            else -> heldIndex
        }

    /** Starts holding [app], shown or hidden, where it is. */
    fun pickUp(app: InstalledApp) {
        check(held == null) { "already holding ${held?.launchable}" }
        val shownIndex = shownApps.indexOf(app)
        heldHidden = shownIndex < 0
        heldIndex = if (heldHidden) hiddenApps.indexOf(app) else shownIndex
        require(heldIndex >= 0) { "not arranged here: ${app.launchable}" }
        held = app
        movedSincePickUp = false
        backKey = null
    }

    /** Moves the held app one step toward [direction]; false if nothing is held or it can't go further that way. */
    fun move(direction: Direction): Boolean {
        val app = held ?: return false
        val fromHidden = heldHidden
        val fromIndex = heldIndex
        val moves = if (heldHidden) hiddenMoves else shownMoves
        var toHidden = heldHidden
        val to: Int
        if (direction == backKey) {
            toHidden = backHidden
            to = backIndex
        } else {
            val within = moves.indexToward(heldIndex, direction)
            if (within != null) {
                to = within
            } else {
                // Out of the shown apps' last row goes Down, out of the shelf's first row goes Up.
                if (direction != (if (heldHidden) Direction.UP else Direction.DOWN)) return false
                toHidden = !heldHidden
                to = movesFor(list(toHidden).size).entryIndex(direction, moves.slot(heldIndex))
            }
        }
        list(fromHidden).removeAt(fromIndex)
        list(toHidden).add(to, app)
        heldHidden = toHidden
        heldIndex = to
        if (toHidden != fromHidden) {
            shownMoves = movesFor(shownApps.size)
            hiddenMoves = movesFor(hiddenApps.size)
        }
        // Going back to where it came from puts every app back where it was.
        val vertical = direction == Direction.UP || direction == Direction.DOWN
        backKey = if (vertical) direction.opposite else null
        backHidden = fromHidden
        backIndex = fromIndex
        movedSincePickUp = true
        moved = true
        return true
    }

    /** Puts the held app down where it is. True if it moved since it was picked up: then there's something to store. */
    fun drop(): Boolean {
        held = null
        heldIndex = -1
        return movedSincePickUp.also { movedSincePickUp = false }
    }

    /**
     * The apps as arranged now, to store and show. Once anything moved, the shown apps keep the
     * order the user gave them from then on, instead of the one by label.
     */
    fun arranged(): ArrangedApps = if (moved) start.rearranged(shownApps.toList(), hiddenApps.toList()) else start

    private fun list(hidden: Boolean) = if (hidden) hiddenApps else shownApps
}
