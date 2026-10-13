package com.luncher.domain.layout

/**
 * Where the D-pad takes the focus among the home screen's tiles: the [shownCount] tiles that
 * [shown] arranges, then, while the user arranges apps, the hidden ones that [shelf] arranges below
 * them. Tiles are numbered as the screen numbers them, the shown ones first.
 *
 * Left and Right go to the next tile along the row, and do nothing at its ends: never to another
 * row, nor to the top bar. Up and Down go to the next row's tile nearest across
 * ([TileMoves.indexToward]), and between the shown tiles' last row and the shelf's first. Right
 * after a move Up or Down, the opposite key goes back to the tile it came from, even where another
 * is as near or nearer: so Down then Up is a round trip, also where a centered row half a column
 * off has two tiles as near. Only Up out of the first row leaves the tiles, to the top bar.
 *
 * Like [com.luncher.domain.arrange.ArrangeSession], it keeps state, the last move, and changes it
 * in place, so a key press allocates nothing (docs/ARCHITECTURE.md, rule 5).
 */
class TileFocus(
    private val shown: TileMoves,
    private val shownCount: Int,
    /** The hidden apps' tiles, while the user arranges apps; null otherwise. */
    private val shelf: TileMoves? = null,
) {

    // The way back from the last move Up or Down: the key, the tile it's from (where that move
    // landed) and the tile it goes to (where that move started).
    private var backKey: Direction? = null
    private var backFrom = -1
    private var backTo = -1

    /**
     * Where the focus goes from the tile at [index] on [direction]: another tile's index, [index]
     * itself where nothing is that way and the focus stays, or null where it leaves the tiles (Up
     * from the first row, to the top bar).
     */
    fun toward(index: Int, direction: Direction): Int? {
        val to = if (direction == backKey && index == backFrom) backTo else next(index, direction)
        when {
            to == null -> forget()
            to == index -> Unit   // it didn't move, so the way back is still the way back
            direction == Direction.UP || direction == Direction.DOWN -> {
                backKey = direction.opposite
                backFrom = to
                backTo = index
            }
            else -> forget()
        }
        return to
    }

    /** Forgets the last move: the tiles moved, so the one it came from may be elsewhere now. */
    fun forget() {
        backKey = null
        backFrom = -1
        backTo = -1
    }

    private fun next(index: Int, direction: Direction): Int? {
        if (index >= shownCount) {
            val shelf = shelf ?: return index
            val hidden = index - shownCount
            val to = within(shelf, hidden, direction)
            return when {
                to != null -> to + shownCount
                // To the shown tiles' last row; with none, to the top bar.
                direction == Direction.UP -> shown.edgeIndex(Direction.UP, shelf.slot(hidden))
                else -> index
            }
        }
        val to = within(shown, index, direction)
        return when {
            to != null -> to
            direction == Direction.UP -> null
            direction == Direction.DOWN -> shelf?.edgeIndex(Direction.DOWN, shown.slot(index))?.plus(shownCount) ?: index
            else -> index
        }
    }

    /** The next tile of [moves] toward [direction], or null at the edge; Left and Right only along the row. */
    private fun within(moves: TileMoves, index: Int, direction: Direction): Int? {
        val to = moves.indexToward(index, direction) ?: return null
        val alongRow = direction == Direction.LEFT || direction == Direction.RIGHT
        return if (alongRow && moves.row(to) != moves.row(index)) null else to
    }
}
