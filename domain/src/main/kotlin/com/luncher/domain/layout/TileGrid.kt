package com.luncher.domain.layout

import com.luncher.domain.appearance.HorizontalAlignment
import com.luncher.domain.appearance.VerticalAlignment
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Tiles in rows of [columns] ([columnsFor] picks how many for a width), in order left to right and
 * top to bottom. The tiles share the [width] they get between them, [gap] apart, with the banner's
 * 16:9 shape; what's left over after rounding goes to equal margins on both sides, so the grid is
 * centered. The row that isn't full is the last one, or the first with [shortRowFirst], and goes
 * where [rowAlignment] says: at the left, centered, or at the right. The rows go where
 * [verticalAlignment] says in the [height] they get, if they fit it; otherwise they start at the
 * top, and rows below the space scroll vertically.
 *
 * A moved tile goes one place along the order on Left and Right, wrapping from the end of a row to
 * the start of the next. On Up and Down it goes one row up or down, to the tile of that row
 * nearest to it across ([slot]): above or below it in full rows, and of two as near, which a
 * centered row half a column off puts on either side, the left one.
 */
class TileGrid(
    private val tileCount: Int,
    private val columns: Int,
    width: Int,
    private val gap: Int,
    private val rowAlignment: HorizontalAlignment = HorizontalAlignment.LEFT,
    private val shortRowFirst: Boolean = false,
    height: Int = 0,
    verticalAlignment: VerticalAlignment = VerticalAlignment.TOP,
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

    /** Above the first row: room left at the top when the rows are centered or at the bottom. */
    private val offset: Int = verticalAlignment.offset(rowsHeight, height)

    override val contentWidth: Int = width.coerceAtLeast(0)
    override val contentHeight: Int = offset + rowsHeight

    override fun left(index: Int): Int = margin + slot(index) * (tileWidth + gap) / 2

    override fun top(index: Int): Int = offset + row(index, tileCount) * (tileHeight + gap)

    override fun indexToward(index: Int, direction: Direction): Int? {
        val row = row(index, tileCount)
        return when (direction) {
            Direction.LEFT -> (index - 1).takeIf { it >= 0 }
            Direction.RIGHT -> (index + 1).takeIf { it < tileCount }
            Direction.UP -> if (row == 0) null else nearest(row - 1, slot(index))
            Direction.DOWN -> if (row == rows - 1) null else nearest(row + 1, slot(index))
        }
    }

    override fun row(index: Int): Int = row(index, tileCount)

    override fun slot(index: Int): Int = slot(index, tileCount)

    /**
     * The tile comes into the row at that edge as it is now, and takes the place that puts it
     * nearest to [slot] once the tiles after it have moved on one place: so a full row's last
     * tile moves on to the next row, and with [shortRowFirst], the first row's to the row above.
     * A centered row is one tile longer then, and so half a column further left.
     */
    override fun entryIndex(direction: Direction, slot: Int): Int {
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
            // Another row is always farther than any slot of the same row.
            val distance = abs(row(index, count) - rowWithIt) * (2 * columns + 1) + abs(slot(index, count) - slot)
            if (distance < bestDistance) {
                best = index
                bestDistance = distance
            }
        }
        return best
    }

    override fun edgeIndex(direction: Direction, slot: Int): Int? {
        if (tileCount == 0) return null
        return nearest(if (direction == Direction.UP) rows - 1 else 0, slot)
    }

    // Rows and slots of a grid of [count] tiles, these or with one more coming in.

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

    /** The slot of [row]'s first tile: the columns the row lacks go to its right, to both sides, or to its left. */
    private fun firstSlot(row: Int, count: Int): Int {
        val missing = columns - rowLength(row, count)
        return when (rowAlignment) {
            HorizontalAlignment.LEFT -> 0
            HorizontalAlignment.CENTER -> missing
            HorizontalAlignment.RIGHT -> 2 * missing
        }
    }

    private fun slot(index: Int, count: Int): Int {
        val row = row(index, count)
        return firstSlot(row, count) + 2 * (index - rowStart(row, count))
    }

    /**
     * The index of the tile in [row] nearest to [slot]: tiles are two slots apart, so the one at
     * or just left of it, which is the left one of two as near.
     */
    private fun nearest(row: Int, slot: Int): Int =
        rowStart(row, tileCount) + (slot - firstSlot(row, tileCount)).floorDiv(2).coerceIn(0, rowLength(row, tileCount) - 1)

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

/**
 * Room above content [contentHeight] tall, aligned so in [height]: half what's left over when
 * centered, all of it at the bottom; none when the content doesn't fit, so it starts at the top.
 */
internal fun VerticalAlignment.offset(contentHeight: Int, height: Int): Int {
    val room = (height - contentHeight).coerceAtLeast(0)
    return when (this) {
        VerticalAlignment.TOP -> 0
        VerticalAlignment.CENTER -> room / 2
        VerticalAlignment.BOTTOM -> room
    }
}
