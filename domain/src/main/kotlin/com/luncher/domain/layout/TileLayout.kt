package com.luncher.domain.layout

/**
 * Where the home screen puts its app tiles: a rule that turns the number of tiles and the space
 * they get into sizes and positions. The view that shows the tiles only places them where a
 * [TileLayout] says, so another arrangement (a carousel, tiles aligned right or at the bottom) is
 * another implementation of this interface, not a change to the view.
 *
 * All values are pixels. Positions are relative to the top-left corner of the space the tiles
 * get; the content can be larger than that space, and the view scrolls it.
 */
interface TileLayout {

    val tileWidth: Int
    val tileHeight: Int

    /** Width of the area all tiles together cover. */
    val contentWidth: Int

    /** Height of the area all tiles together cover. */
    val contentHeight: Int

    /** Left edge of tile [index]. */
    fun left(index: Int): Int

    /** Top edge of tile [index]. */
    fun top(index: Int): Int
}
