package com.luncher.domain.layout

/**
 * The home screen while the user arranges apps: the [shown] apps' tiles, then a label, then the
 * [hidden] apps' tiles on a shelf below. Tiles are numbered in that order: the first [shownCount]
 * are the shown ones. The shelf always has room for at least one tile, the empty slot where an
 * app goes to be hidden, so the user sees where hiding is even before anything is.
 *
 * Both parts are [TileLayout]s of their own, e.g. two [TileGrid]s; this only stacks them.
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
) : TileLayout {

    override val tileWidth: Int = shown.tileWidth
    override val tileHeight: Int = shown.tileHeight

    /** Top edge of the shelf's label. */
    val labelTop: Int = if (shownCount == 0) 0 else shown.contentHeight + gap

    /** Top edge of the shelf's first row. */
    val shelfTop: Int = labelTop + labelHeight + gap

    /** Where the empty slot goes when nothing is hidden (index [shownCount]); null when something is. */
    val emptySlot: Int? = if (hiddenCount == 0) shownCount else null

    override val contentWidth: Int = maxOf(shown.contentWidth, hidden.contentWidth)
    override val contentHeight: Int = shelfTop + if (hiddenCount == 0) hidden.tileHeight else hidden.contentHeight

    override fun left(index: Int): Int =
        if (index < shownCount) shown.left(index) else hidden.left(index - shownCount)

    override fun top(index: Int): Int =
        if (index < shownCount) shown.top(index) else shelfTop + hidden.top(index - shownCount)
}
