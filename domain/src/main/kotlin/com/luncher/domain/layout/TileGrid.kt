package com.luncher.domain.layout

/**
 * Tiles in rows of [columns], filled from the top left. The tiles share the [width] they get
 * between them, [gap] apart, with the banner's 16:9 shape; what's left over after rounding goes to
 * equal margins on both sides, so the grid is centered. A last row that isn't full starts at the
 * left. Rows below the space scroll vertically.
 */
class TileGrid(
    tileCount: Int,
    private val columns: Int,
    width: Int,
    private val gap: Int,
) : TileLayout {

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

    companion object {
        /** Tiles per row until a setting chooses it. */
        const val DEFAULT_COLUMNS = 5

        // Android TV banners are 320x180 dp.
        private const val BANNER_WIDTH = 16
        private const val BANNER_HEIGHT = 9
    }
}
