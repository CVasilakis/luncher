package com.luncher.domain.layout

/**
 * Where a tile goes when the user moves it with the D-pad, in an arrangement of tiles, and where
 * the focus goes Up and Down among them ([TileFocus]). A rule of the arrangement, like its
 * positions ([TileLayout]), but independent of sizes: it depends only on how many tiles there are
 * and how they're arranged (a grid's columns and alignment, a carousel's single row).
 */
interface TileMoves {

    /** Where the tile at [index] goes on [direction]: the index it takes among these tiles, or null at the edge. */
    fun indexToward(index: Int, direction: Direction): Int?

    /** The row of the tile at [index], counted from the top. */
    fun row(index: Int): Int

    /**
     * Where the tile at [index] is across its row, in halves of a column from the left: 0 is the
     * first column, 2 the second, and an odd slot is between two columns, where a centered row
     * that isn't full can put its tiles. Tiles at the same slot are above each other.
     */
    fun slot(index: Int): Int
}
