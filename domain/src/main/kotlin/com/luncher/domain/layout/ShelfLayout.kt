package com.luncher.domain.layout

/**
 * The home screen while the user arranges apps: the [shown] apps' tiles, then a label, then the
 * [hidden] apps' tiles on a shelf below. Tiles are numbered in that order: the first [shownCount]
 * are the shown ones. The shelf always has room for at least one tile, the empty slot where an
 * app goes to be hidden, so the user sees where hiding is even before anything is.
 *
 * Both parts are [TileLayout]s of their own, e.g. two [TileGrid]s, starting at the top; this
 * stacks them, and with [atBottom] puts the whole at the bottom of the [height] it gets, if it
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
    atBottom: Boolean = false,
    /** The label lines up with the shelf's right edge rather than its left, as its rows do. */
    private val labelAtRight: Boolean = false,
) : TileLayout {

    override val tileWidth: Int = shown.tileWidth
    override val tileHeight: Int = shown.tileHeight

    private val shownHeight: Int = if (shownCount == 0) 0 else shown.contentHeight + gap
    private val shelfHeight: Int = if (hiddenCount == 0) hidden.tileHeight else hidden.contentHeight

    /** Above the shown tiles: room left at the top when the whole goes at the bottom. */
    private val offset: Int =
        if (atBottom) (height - (shownHeight + labelHeight + gap + shelfHeight)).coerceAtLeast(0) else 0

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

    /** Left edge of the label, [labelWidth] wide: in line with the shelf's first tile, or its last one's right edge. */
    fun labelLeft(labelWidth: Int): Int {
        if (!labelAtRight) return hidden.left(0)
        return hidden.left(maxOf(hiddenCount, 1) - 1) + tileWidth - labelWidth
    }
}
