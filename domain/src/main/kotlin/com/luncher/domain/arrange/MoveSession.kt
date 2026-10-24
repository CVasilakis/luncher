package com.luncher.domain.arrange

import com.luncher.domain.apps.ArrangedApps
import com.luncher.domain.apps.HomeApp
import com.luncher.domain.layout.Direction
import com.luncher.domain.layout.TileMoves
import com.luncher.domain.layout.opposite

/**
 * The user moving one of the home screen's shown apps, [held], to another place among them, as
 * [moves] says tiles move in the arrangement the screen shows them in. The hidden apps don't take
 * part: they stay where they are, in their own order. Right after a move Up or Down, the opposite
 * key puts the app back where it was, even where another place is as near.
 *
 * Like [com.luncher.domain.layout.TileFocus], and unlike the other rules, a session keeps state and
 * changes it in place: each move is a key press, and handling a key press allocates nothing
 * (docs/ARCHITECTURE.md, rule 5).
 */
class MoveSession(
    private val start: ArrangedApps,
    /** The app the user moves: one of [start]'s shown apps. */
    val held: HomeApp,
    /** How tiles move among the shown apps: as many as there are of them. */
    private val moves: TileMoves,
) {

    private val shownApps = ArrayList(start.shown)

    /** The shown apps, in their current order. */
    val shown: List<HomeApp> get() = shownApps

    /** The held app's place among the shown apps. */
    var heldIndex: Int = shownApps.indexOf(held)
        private set

    /** Whether the held app moved since the session started: then there's something to store. */
    var moved = false
        private set

    // After a move Up or Down, the key back and where it takes the held app: where it came from,
    // even where another place is as near, so Down then Up is a round trip.
    private var backKey: Direction? = null
    private var backIndex = -1

    init {
        require(heldIndex >= 0) { "not shown: ${held.launchable}" }
    }

    /** Moves the held app one step toward [direction]; false if it can't go further that way. */
    fun move(direction: Direction): Boolean {
        val from = heldIndex
        val to = if (direction == backKey) backIndex else moves.indexToward(from, direction) ?: return false
        shownApps.removeAt(from)
        shownApps.add(to, held)
        heldIndex = to
        // Going back to where it came from puts every app back where it was.
        val vertical = direction == Direction.UP || direction == Direction.DOWN
        backKey = if (vertical) direction.opposite else null
        backIndex = from
        moved = true
        return true
    }

    /**
     * The apps as arranged now, to store and show. Once the app moved, the shown apps keep the
     * order the user gave them from then on, instead of the one by name.
     */
    fun arranged(): ArrangedApps = if (moved) start.rearranged(shownApps.toList(), start.hidden) else start
}
