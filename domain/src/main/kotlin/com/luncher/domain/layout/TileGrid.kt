package com.luncher.domain.layout

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Tiles in rows of [columns] ([columnsFor] picks how many for a width), in order left to right and
 * top to bottom. The tiles share the [width] they get between them, [gap] apart, with the banner's
 * 16:9 shape; what's left over after rounding goes to equal margins on both sides, so the grid is
 * centered. The row that isn't full is the last one, or the first with [shortRowFirst], and lines
 * up at the left, or the right with [alignRight]. The rows start at the top, or with [atBottom]
 * end at the bottom of the [height] they get, if they fit it; rows below the space scroll
 * vertically.
 *
 * A moved tile goes one place along the order on Left and Right, wrapping from the end of a row to
 * the start of the next, and one row up or down on Up and Down, staying in its column. Where the
 * next row has no tile in that column (it isn't full), it takes the place in that row nearest to it.
 */
class TileGrid(
    private val tileCount: Int,
    private val columns: Int,
    width: Int,
    private val gap: Int,
    private val alignRight: Boolean = false,
    private val shortRowFirst: Boolean = false,
    height: Int = 0,
    atBottom: Boolean = false,
) : TileLayout, TileMoves {

    init {
        require(columns >= 1) { "columns must be at least 1: $columns" }
        require(tileCount >= 0) { "tileCount must not be negative: $tileCount" }
    }

    override val tileWidth: Int = ((width - gap * (columns - 1)) / columns).coerceAtLeast(0)
    override val tileHeight: Int = tileWidth * BANNER_HEIGHT / BANNER_WIDTH

    private val margin: Int = ((width - tileWidth * columns - gap * (columns - 1)) / 2).coerceAtLeast(0)
    private val rows: Int = rows(tileCount)

    private val rowsHeight: Int = if (rows == 0) 0 else rows * tileHeight + (rows - 1) * gap

    /** Above the first row: room left at the top when the rows go at the bottom. */
    private val offset: Int = if (atBottom) (height - rowsHeight).coerceAtLeast(0) else 0

    override val contentWidth: Int = width.coerceAtLeast(0)
    override val contentHeight: Int = offset + rowsHeight

    override fun left(index: Int): Int = margin + column(index) * (tileWidth + gap)

    override fun top(index: Int): Int = offset + row(index, tileCount) * (tileHeight + gap)

    override fun indexToward(index: Int, direction: Direction): Int? {
        val row = row(index, tileCount)
        return when (direction) {
            Direction.LEFT -> (index - 1).takeIf { it >= 0 }
            Direction.RIGHT -> (index + 1).takeIf { it < tileCount }
            Direction.UP -> if (row == 0) null else nearest(row - 1, column(index))
            Direction.DOWN -> if (row == rows - 1) null else nearest(row + 1, column(index))
        }
    }

    override fun column(index: Int): Int = column(index, tileCount)

    /**
     * The tile comes into the row at that edge as it is now, and takes the place that puts it
     * nearest to [column] once the tiles after it have moved on one place: so a full row's last
     * tile moves on to the next row, and with [shortRowFirst], the first row's to the row above.
     */
    override fun entryIndex(direction: Direction, column: Int): Int {
        if (tileCount == 0) return 0
        val row = if (direction == Direction.UP) rows - 1 else 0
        val start = rowStart(row, tileCount)
        // The same row with the tile in: counted from the top, or from the bottom when the first
        // row is the short one, since then a new row starts at the top.
        val count = tileCount + 1
        val rowWithIt = if (shortRowFirst) row + rows(count) - rows else row
        var best = start
        var bestDistance = Int.MAX_VALUE
        for (index in start..start + rowLength(row, tileCount)) {
            // Another row is always farther than any column of the same row.
            val distance = abs(row(index, count) - rowWithIt) * (columns + 1) + abs(column(index, count) - column)
            if (distance < bestDistance) {
                best = index
                bestDistance = distance
            }
        }
        return best
    }

    // Rows and columns of a grid of [count] tiles, these or with one more coming in.

    private fun rows(count: Int): Int = (count + columns - 1) / columns

    /** How many tiles the first row has when it's the short one; 0 when only the last row can be short. */
    private fun shortFirst(count: Int): Int = if (shortRowFirst) count % columns else 0

    private fun row(index: Int, count: Int): Int {
        val short = shortFirst(count)
        return when {
            short == 0 -> index / columns
            index < short -> 0
            else -> 1 + (index - short) / columns
        }
    }

    /** The index of [row]'s first tile. */
    private fun rowStart(row: Int, count: Int): Int {
        val short = shortFirst(count)
        return when {
            short == 0 -> row * columns
            row == 0 -> 0
            else -> short + (row - 1) * columns
        }
    }

    private fun rowLength(row: Int, count: Int): Int = minOf(rowStart(row + 1, count), count) - rowStart(row, count)

    /** The column of [row]'s first tile. */
    private fun firstColumn(row: Int, count: Int): Int = if (alignRight) columns - rowLength(row, count) else 0

    private fun column(index: Int, count: Int): Int {
        val row = row(index, count)
        return index - rowStart(row, count) + firstColumn(row, count)
    }

    /** The index of the tile in [row] nearest to [column]. */
    private fun nearest(row: Int, column: Int): Int =
        rowStart(row, tileCount) + (column - firstColumn(row, tileCount)).coerceIn(0, rowLength(row, tileCount) - 1)

    companion object {
        /**
         * How many columns of tiles about [tileWidth] wide, [gap] apart, fit [width]: the nearest
         * whole number, at least one. So tiles keep about that size, next to text of a fixed size,
         * on any screen: more of them on a wider one, or at a lower density.
         */
        fun columnsFor(width: Int, tileWidth: Int, gap: Int): Int {
            require(tileWidth > 0) { "tileWidth must be positive: $tileWidth" }
            return ((width + gap).toFloat() / (tileWidth + gap)).roundToInt().coerceAtLeast(1)
        }

        // Android TV banners are 320x180 dp.
        private const val BANNER_WIDTH = 16
        private const val BANNER_HEIGHT = 9
    }
}
