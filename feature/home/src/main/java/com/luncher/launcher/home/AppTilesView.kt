package com.luncher.launcher.home

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.Scroller
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.TileStyle
import com.luncher.domain.layout.Direction
import com.luncher.domain.layout.TileFocus
import com.luncher.domain.layout.TileLayout
import com.luncher.domain.layout.TileLayouts
import com.luncher.domain.layout.TileMoves

/**
 * The home screen's app tiles, placed where a [TileLayout] says, scrolled so the focused tile stays
 * in view. The arrangement itself (grid, columns, alignment, placement) is the layout's decision,
 * made in :domain, where [TileLayouts] picks it for the [tileGeometry]. How each tile is drawn
 * ([tileStyle]) is in [paints], which all the tiles share.
 *
 * While names show, the focused tile, or every tile, draws its app's name below itself, in the
 * room the layout leaves below each row; this view doesn't clip its tiles (clipChildren, in home_activity.xml),
 * so the name and a zoomed or lifted tile's shadow show past a tile's bounds.
 *
 * D-pad focus goes where the layout's [TileFocus] says, a rule of :domain: Android's own search
 * would go to the nearest view that way anywhere, past the end of a row to another row or to the
 * top bar, and pick one of two tiles as near by itself. Only Up out of the first row is left to
 * Android's search, which finds the top bar. [requestChildFocus] then scrolls to the focused tile.
 * Padding is the TV's overscan margin, and leaves room for the focused tile's zoom.
 */
internal class AppTilesView(context: Context, attrs: AttributeSet?) : ViewGroup(context, attrs) {

    private val pixelsPerDp = resources.displayMetrics.density
    private val scroller = Scroller(context)

    /** What every tile draws with: give it to each new tile. */
    val paints = TilePaints(context)

    /**
     * Where the tiles go and how big they are, from the home screen's appearance. Its default is
     * the home screen without settings. Whether the tiles' names show is part of it: the rows
     * leave the names room ([TilePaints.nameHeight]), so it changes the layout.
     */
    var tileGeometry: TileGeometry = TileGeometry()
        set(value) {
            if (field == value) return
            field = value
            layouts = TileLayouts(value, pixelsPerDp, paints.nameHeight)
            paints.names = value.appName
            for (i in 0 until childCount) getChildAt(i).invalidate()
            requestLayout()
        }

    private var layouts = TileLayouts(tileGeometry, pixelsPerDp, paints.nameHeight)

    /** The focused tile's frame's color: the theme's accent, as the home screen shows it. */
    var accent: Int = paints.focusFrame.color
        set(value) {
            if (field == value) return
            field = value
            paints.focusFrame.color = value
            for (i in 0 until childCount) getChildAt(i).invalidate()
        }

    /**
     * How the tiles are drawn, from the home screen's appearance: a new one changes [paints] and
     * restyles the tiles, with the places and images they have.
     */
    var tileStyle: TileStyle = TileStyle()
        set(value) {
            if (field == value) return
            field = value
            paints.corners = value.corners
            paints.framed = value.focusFrame
            for (i in 0 until childCount) (getChildAt(i) as AppTileView).restyle()
        }

    /**
     * Where the tiles go, kept while what it's made from stays the same: the width and height,
     * the number of tiles and [tileGeometry], each compared in [onMeasure] with the value the
     * layout was made from. Moving an app changes none of them, only which tile is where, so it
     * lays the tiles out again without allocating (docs/ARCHITECTURE.md, rule 5).
     * Anything a layout comes to depend on is compared there too.
     */
    private var tileLayout: TileLayout = layouts.tiles(childCount, width = 0, height = 0)
    private var layoutWidth = 0
    private var layoutHeight = 0
    private var layoutTileCount = 0
    private var layoutGeometry = tileGeometry

    /** Where the D-pad takes the focus among the tiles as [tileLayout] places them: made with it. */
    private var tileFocus: TileFocus = layouts.focus(childCount, width = 0)

    /** How tiles move among [tileCount] of them, in the arrangement this view shows them in. */
    fun movesFor(tileCount: Int): TileMoves = layouts.moves(tileCount, width - paddingLeft - paddingRight)

    /** Moves the tile at [from] to [to], the others shifting to make room. Focus stays on it. */
    fun moveTile(from: Int, to: Int) {
        if (from == to) return
        val tile = getChildAt(from)
        // Detached, not removed: removing would take the focus away from it.
        detachViewFromParent(from)
        attachViewToParent(tile, to, tile.layoutParams)
        tileFocus.forget()   // the tile the focus came from may have moved
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val tilesWidth = width - paddingLeft - paddingRight
        val tilesHeight = height - paddingTop - paddingBottom
        if (tilesWidth != layoutWidth || tilesHeight != layoutHeight || childCount != layoutTileCount ||
            tileGeometry != layoutGeometry
        ) {
            tileLayout = layouts.tiles(childCount, tilesWidth, tilesHeight)
            tileFocus = layouts.focus(childCount, tilesWidth)
            layoutWidth = tilesWidth
            layoutHeight = tilesHeight
            layoutTileCount = childCount
            layoutGeometry = tileGeometry
        }
        val tileWidth = MeasureSpec.makeMeasureSpec(tileLayout.tileWidth, MeasureSpec.EXACTLY)
        val tileHeight = MeasureSpec.makeMeasureSpec(tileLayout.tileHeight, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(tileWidth, tileHeight)
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val left = paddingLeft + tileLayout.left(i)
            val top = paddingTop + tileLayout.top(i)
            getChildAt(i).layout(left, top, left + tileLayout.tileWidth, top + tileLayout.tileHeight)
        }
        // Tiles may have moved or gone: show the focused one at once, without animating.
        scroller.forceFinished(true)
        val focused = focusedChild
        if (focused != null) {
            scrollTo(targetScrollX(focused, scrollX), targetScrollY(focused, scrollY))
        } else {
            scrollTo(scrollX.coerceIn(0, maxScrollX()), scrollY.coerceIn(0, maxScrollY()))
        }
    }

    override fun focusSearch(focused: View, direction: Int): View? {
        val arrow = when (direction) {
            FOCUS_LEFT -> Direction.LEFT
            FOCUS_RIGHT -> Direction.RIGHT
            FOCUS_UP -> Direction.UP
            FOCUS_DOWN -> Direction.DOWN
            else -> null
        }
        val index = indexOfChild(focused)
        // Until the next measure, tiles added or removed, or a new geometry, aren't in tileFocus yet.
        val current = childCount == layoutTileCount && tileGeometry == layoutGeometry
        if (arrow != null && index >= 0 && current) {
            // The focused tile itself where the focus stays, which moves nothing.
            tileFocus.toward(index, arrow)?.let { return getChildAt(it) }
        }
        return super.focusSearch(focused, direction)
    }

    override fun requestChildFocus(child: View, focused: View) {
        super.requestChildFocus(child, focused)
        if (isLayoutRequested) return   // positions aren't known yet; onLayout scrolls
        // From where a running scroll is heading, so fast key presses don't fall behind.
        val fromX = if (scroller.isFinished) scrollX else scroller.finalX
        val fromY = if (scroller.isFinished) scrollY else scroller.finalY
        val dx = targetScrollX(child, fromX) - scrollX
        val dy = targetScrollY(child, fromY) - scrollY
        if (dx == 0 && dy == 0) return
        scroller.startScroll(scrollX, scrollY, dx, dy, SCROLL_DURATION_MS)
        postInvalidateOnAnimation()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, scroller.currY)
            postInvalidateOnAnimation()
        }
    }

    private fun targetScrollX(child: View, from: Int) =
        scrollToShow(child.left - paddingLeft, child.right + paddingRight, from, width, maxScrollX())

    // The focused tile's name too, below it, while names show.
    private fun targetScrollY(child: View, from: Int) =
        scrollToShow(child.top - paddingTop, child.bottom + layouts.nameRoom + paddingBottom, from, height, maxScrollY())

    /** The scroll position nearest to [from] that shows [start] to [end] within [size], if it fits. */
    private fun scrollToShow(start: Int, end: Int, from: Int, size: Int, max: Int): Int {
        val scroll = when {
            start < from -> start
            end > from + size -> end - size
            else -> from
        }
        return scroll.coerceIn(0, max)
    }

    private fun maxScrollX() = (paddingLeft + tileLayout.contentWidth + paddingRight - width).coerceAtLeast(0)

    private fun maxScrollY() = (paddingTop + tileLayout.contentHeight + paddingBottom - height).coerceAtLeast(0)

    private companion object {
        const val SCROLL_DURATION_MS = 200
    }
}
