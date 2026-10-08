package com.luncher.launcher.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.Scroller
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.layout.ShelfLayout
import com.luncher.domain.layout.TileLayout
import com.luncher.domain.layout.TileLayouts
import com.luncher.domain.layout.TileMoves
import com.luncher.launcher.ui.color
import com.luncher.launcher.ui.R as UiR

/**
 * The home screen's app tiles, placed where a [TileLayout] says, scrolled so the focused tile stays
 * in view. The arrangement itself (grid, columns, alignment) is the layout's decision, made in
 * :domain, where [TileLayouts] picks it for the [tileGeometry].
 *
 * While the user arranges apps ([shownCount] set), the tiles after the shown ones are the hidden
 * apps, on a shelf below a label ([ShelfLayout]); with none, an empty slot shows where hiding is.
 *
 * D-pad focus needs no code here: Android moves focus to the nearest tile in the key's direction,
 * including tiles scrolled out of view, and [requestChildFocus] then scrolls to it. Padding is
 * the TV's overscan margin, and leaves room for the focused tile's zoom.
 */
internal class AppTilesView(context: Context, attrs: AttributeSet?) : ViewGroup(context, attrs) {

    private val pixelsPerDp = resources.displayMetrics.density
    private val scroller = Scroller(context)

    /**
     * Where the tiles go and how big they are, from the home screen's appearance. Its default is
     * the home screen without settings.
     */
    var tileGeometry: TileGeometry = TileGeometry()
        set(value) {
            if (field == value) return
            field = value
            layouts = TileLayouts(value, pixelsPerDp)
            requestLayout()
        }

    private var layouts = TileLayouts(tileGeometry, pixelsPerDp)

    /**
     * While the user arranges apps, how many of the tiles are shown apps; the others are hidden
     * ones, on the shelf. Null otherwise.
     */
    var shownCount: Int? = null
        set(value) {
            if (field == value) return
            field = value
            setWillNotDraw(value == null)   // the shelf's label and empty slot are drawn here
            requestLayout()
        }

    /** The shelf's label, empty slot and its caption: created with the first shelf. */
    private val shelf by lazy { Shelf() }

    /**
     * Where the tiles go, kept while what [createLayout] makes it from stays the same: the width,
     * the number of tiles, [shownCount] and [tileGeometry], each compared in [onMeasure] with the
     * value the layout was made from. A move in arrange mode changes none of them, only which tile
     * is where, so it lays the tiles out again without allocating (docs/ARCHITECTURE.md, rule 5).
     * Anything a layout comes to depend on is compared there too.
     */
    private var tileLayout: TileLayout = createLayout(width = 0)
    private var layoutWidth = 0
    private var layoutTileCount = 0
    private var layoutShownCount: Int? = null
    private var layoutGeometry = tileGeometry

    private fun createLayout(width: Int): TileLayout {
        val shown = shownCount ?: return layouts.tiles(childCount, width)
        return layouts.shelf(shown, childCount - shown, width, shelf.labelHeight)
    }

    /** How tiles move among [tileCount] of them, in the arrangement this view shows them in. */
    fun movesFor(tileCount: Int): TileMoves = layouts.tiles(tileCount, width - paddingLeft - paddingRight)

    /** Moves the tile at [from] to [to], the others shifting to make room. Focus stays on it. */
    fun moveTile(from: Int, to: Int) {
        if (from == to) return
        val tile = getChildAt(from)
        // Detached, not removed: removing would take the focus away from it.
        detachViewFromParent(from)
        attachViewToParent(tile, to, tile.layoutParams)
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val tilesWidth = width - paddingLeft - paddingRight
        if (tilesWidth != layoutWidth || childCount != layoutTileCount || shownCount != layoutShownCount ||
            tileGeometry != layoutGeometry
        ) {
            tileLayout = createLayout(tilesWidth)
            layoutWidth = tilesWidth
            layoutTileCount = childCount
            layoutShownCount = shownCount
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

    override fun onDraw(canvas: Canvas) {
        val layout = tileLayout as? ShelfLayout ?: return
        shelf.draw(canvas, layout)
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

    /** What the shelf draws besides its tiles: the label above it, and the empty slot while nothing is hidden. */
    private inner class Shelf {

        private val color = context.color(UiR.color.text_secondary)

        private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = this@Shelf.color
            textSize = resources.getDimension(R.dimen.home_shelf_label_text)
        }
        private val label = resources.getString(R.string.home_hidden)
        private val labelMetrics = labelPaint.fontMetricsInt   // read once: each read allocates
        val labelHeight = labelMetrics.descent - labelMetrics.ascent

        private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = this@Shelf.color
            strokeWidth = resources.getDimension(R.dimen.home_shelf_slot_line)
            val dash = resources.getDimension(R.dimen.home_shelf_slot_dash)
            pathEffect = DashPathEffect(floatArrayOf(dash, dash), 0f)
        }
        private val slot = RectF()
        private val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = this@Shelf.color
            textSize = resources.getDimension(R.dimen.home_shelf_caption_text)
        }
        private val captionPadding = resources.getDimensionPixelSize(R.dimen.home_shelf_caption_padding)
        private var caption: StaticLayout? = null   // wrapped to the slot's width, when it changes

        fun draw(canvas: Canvas, layout: ShelfLayout) {
            val left = paddingLeft + layout.left(shownCount ?: 0).toFloat()
            val labelTop = paddingTop + layout.labelTop
            canvas.drawText(label, left, labelTop - labelMetrics.ascent.toFloat(), labelPaint)

            val empty = layout.emptySlot ?: return
            val inset = slotPaint.strokeWidth / 2
            val top = paddingTop + layout.top(empty).toFloat()
            slot.set(left + inset, top + inset, left + layout.tileWidth - inset, top + layout.tileHeight - inset)
            canvas.drawRect(slot, slotPaint)

            val width = (layout.tileWidth - 2 * captionPadding).coerceAtLeast(0)
            val text = caption?.takeIf { it.width == width } ?: captionLayout(width).also { caption = it }
            canvas.save()
            canvas.translate(left + captionPadding, top + (layout.tileHeight - text.height) / 2f)
            text.draw(canvas)
            canvas.restore()
        }

        @Suppress("DEPRECATION") // StaticLayout.Builder needs API 23.
        private fun captionLayout(width: Int) = StaticLayout(
            resources.getString(R.string.home_hidden_empty), captionPaint, width,
            Layout.Alignment.ALIGN_CENTER, 1f, 0f, false,
        )
    }

    private companion object {
        const val SCROLL_DURATION_MS = 200
    }
}
