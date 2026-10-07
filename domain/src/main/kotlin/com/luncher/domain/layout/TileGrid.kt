package com.luncher.domain.layout

import kotlin.math.roundToInt

/**
 * Tiles in rows of [columns] ([columnsFor] picks how many for a width), filled from the top left.
 * The tiles share the [width] they get between them, [gap] apart, with the banner's 16:9 shape;
 * what's left over after rounding goes to equal margins on both sides, so the grid is centered. A
 * last row that isn't full starts at the left. Rows below the space scroll vertically.
 *
 * A moved tile goes one place along the order on Left and Right, wrapping from the end of a row to
 * the start of the next, and one row up or down on Up and Down. Down where the next row is too short
 * takes it to the last place.
 */
class TileGrid(
    private val tileCount: Int,
    private val columns: Int,
    width: Int,
    private val gap: Int,
) : TileLayout, TileMoves {

    init {
        require(columns >= 1) { "columns must be at least 1: $columns" }
        require(tileCount >= 0) { "tileCount must not be negative: $tileCount" }
    }

    override val tileWidth: Int = ((width - gap * (columns - 1)) / columns).coerceAtLeast(0)
    override val tileHeight: Int = tileWidth * BANNER_HEIGHT / BANNER_WIDTH

    private val margin: Int = ((width - tileWidth * columns - gap * (columns - 1)) / 2).coerceAtLeast(0)
    private val rows: Int = (tileCount + columns - 1) / columns

    override val contentWidth: Int = width.coerceAtLeast(0)
    override val contentHeight: Int = if (rows == 0) 0 else rows * tileHeight + (rows - 1) * gap

    override fun left(index: Int): Int = margin + index % columns * (tileWidth + gap)

    override fun top(index: Int): Int = index / columns * (tileHeight + gap)

    override fun indexToward(index: Int, direction: Direction): Int? = when (direction) {
        Direction.LEFT -> (index - 1).takeIf { it >= 0 }
        Direction.RIGHT -> (index + 1).takeIf { it < tileCount }
        Direction.UP -> (index - columns).takeIf { it >= 0 }
        Direction.DOWN -> when {
            index + columns < tileCount -> index + columns
            index / columns < (tileCount - 1) / columns -> tileCount - 1
            else -> null
        }
    }

    override fun column(index: Int): Int = index % columns

    override fun entryIndex(direction: Direction, column: Int): Int {
        val lastRowStart = if (tileCount == 0) 0 else (tileCount - 1) / columns * columns
        val index = if (direction == Direction.UP) lastRowStart + column else column
        return index.coerceIn(0, tileCount)
    }

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
