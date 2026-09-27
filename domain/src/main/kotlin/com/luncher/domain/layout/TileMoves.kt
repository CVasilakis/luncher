package com.luncher.domain.layout

/**
 * Where a tile goes when the user moves it with the D-pad, in an arrangement of tiles. A rule of
 * the arrangement, like its positions ([TileLayout]), but independent of sizes: it depends only on
 * how many tiles there are and how they're arranged (a grid's columns, a carousel's single row).
 */
interface TileMoves {

    /** Where the tile at [index] goes on [direction]: the index it takes among these tiles, or null at the edge. */
    fun indexToward(index: Int, direction: Direction): Int?

    /** The column of the tile at [index]: tiles in one column are above each other. */
    fun column(index: Int): Int

    /**
     * Where a tile that comes in over the edge goes, as its index among these tiles plus itself:
     * moving [Direction.DOWN] it comes in at the top, moving [Direction.UP] at the bottom; in
     * [column], or as near to it as there are tiles.
     */
    fun entryIndex(direction: Direction, column: Int): Int
}
