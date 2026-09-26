package com.luncher.launcher.home

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.Scroller
import com.luncher.domain.layout.TileGrid
import com.luncher.domain.layout.TileLayout
import com.luncher.launcher.R

/**
 * The home screen's app tiles, placed where a [TileLayout] says, scrolled so the focused tile stays
 * in view. The arrangement itself (grid, columns, alignment) is the layout's decision, made in
 * :domain; [createLayout] is the one place that picks which one.
 *
 * D-pad focus needs no code here: Android moves focus to the nearest tile in the key's direction,
 * including tiles scrolled out of view, and [requestChildFocus] then scrolls to it. Padding is
 * the TV's overscan margin, and leaves room for the focused tile's zoom.
 */
class AppTilesView(context: Context, attrs: AttributeSet?) : ViewGroup(context, attrs) {

    private val gap = resources.getDimensionPixelSize(R.dimen.home_tile_gap)
    private val scroller = Scroller(context)
    private var tileLayout: TileLayout = createLayout(width = 0)

    private fun createLayout(width: Int): TileLayout = TileGrid(childCount, TileGrid.DEFAULT_COLUMNS, width, gap)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        tileLayout = createLayout(width - paddingLeft - paddingRight)
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

    private fun targetScrollY(child: View, from: Int) =
        scrollToShow(child.top - paddingTop, child.bottom + paddingBottom, from, height, maxScrollY())

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
