package com.luncher.domain.layout

import com.luncher.domain.appearance.HorizontalAlignment
import com.luncher.domain.appearance.VerticalAlignment

/**
 * The home screen while the user arranges apps: the [shown] apps' tiles, then a label, then the
 * [hidden] apps' tiles on a shelf below. Tiles are numbered in that order: the first [shownCount]
 * are the shown ones. The shelf always has room for at least one tile, the empty slot where an
 * app goes to be hidden, so the user sees where hiding is even before anything is.
 *
 * Both parts are [TileLayout]s of their own, e.g. two [TileGrid]s, starting at the top; this
 * stacks them, and puts the whole where [verticalAlignment] says in the [height] it gets, if it
 * fits. [hidden] lays out at least one tile, where the empty slot goes when nothing is hidden.
 */
class ShelfLayout(
    private val shown: TileLayout,
    private val hidden: TileLayout,
    private val shownCount: Int,
    private val hiddenCount: Int,
    /** The label's height, text included. */
    labelHeight: Int,
    /** Between the shown tiles and the label, and between the label and the shelf. */
    gap: Int,
    height: Int = 0,
    verticalAlignment: VerticalAlignment = VerticalAlignment.TOP,
    /** Where the label goes over the shelf: as its rows that aren't full do. */
    private val labelAlignment: HorizontalAlignment = HorizontalAlignment.LEFT,
) : TileLayout {

    override val tileWidth: Int = shown.tileWidth
    override val tileHeight: Int = shown.tileHeight

    private val shownHeight: Int = if (shownCount == 0) 0 else shown.contentHeight + gap
    private val shelfHeight: Int = if (hiddenCount == 0) hidden.tileHeight else hidden.contentHeight

    /** Above the shown tiles: room left at the top when the whole is centered or at the bottom. */
    private val offset: Int = verticalAlignment.offset(shownHeight + labelHeight + gap + shelfHeight, height)

    /** Top edge of the shelf's label. */
    val labelTop: Int = offset + shownHeight

    /** Top edge of the shelf's first row. */
    val shelfTop: Int = labelTop + labelHeight + gap

    /** Where the empty slot goes when nothing is hidden (index [shownCount]); null when something is. */
    val emptySlot: Int? = if (hiddenCount == 0) shownCount else null

    override val contentWidth: Int = maxOf(shown.contentWidth, hidden.contentWidth)
    override val contentHeight: Int = shelfTop + shelfHeight

    override fun left(index: Int): Int =
        if (index < shownCount) shown.left(index) else hidden.left(index - shownCount)

    override fun top(index: Int): Int =
        if (index < shownCount) offset + shown.top(index) else shelfTop + hidden.top(index - shownCount)

    /**
     * Left edge of the label, [labelWidth] wide: in line with the shelf's first tile, centered
     * over the shelf, or in line with its last tile's right edge.
     */
    fun labelLeft(labelWidth: Int): Int = when (labelAlignment) {
        HorizontalAlignment.LEFT -> hidden.left(0)
        HorizontalAlignment.CENTER -> (hidden.contentWidth - labelWidth) / 2
        HorizontalAlignment.RIGHT -> hidden.left(maxOf(hiddenCount, 1) - 1) + tileWidth - labelWidth
    }
}
