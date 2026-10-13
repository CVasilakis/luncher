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

    /**
     * Where a tile that comes in over the edge goes, as its index among these tiles plus itself:
     * moving [Direction.DOWN] it comes in at the top, moving [Direction.UP] at the bottom; at
     * [slot], or as near to it as there are tiles.
     */
    fun entryIndex(direction: Direction, slot: Int): Int

    /**
     * Where the focus lands when it comes in over the edge: moving [Direction.DOWN], the first
     * row's tile nearest to [slot], moving [Direction.UP], the last row's; of two as near, the
     * left one. Null without tiles.
     */
    fun edgeIndex(direction: Direction, slot: Int): Int?
}
