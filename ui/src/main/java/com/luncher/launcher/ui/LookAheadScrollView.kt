package com.luncher.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView

/**
 * A column of rows that scrolls a row ahead of the focus, as [LookAheadListView] does: its one
 * child, a vertical layout with a margin inside around its rows (its padding), each row a
 * [ListEntry]. While there's more beyond an edge, the focused row stays a row away from it, so the
 * next row shows whole before the focus gets there, and the focus is never on a row the frame
 * fades ([ScrollFrame]); only the first and the last rows go to the edge.
 *
 * Its window ends on a whole row: while the rows don't all fit, each is a little taller or
 * shorter, the same for all, so a whole number of them fill the room ([fitRows]), and the view is
 * that tall. Up and Down move the focus while the row beyond the next one shows whole; else the
 * column slides a row under the focus, which stays where it is ([FocusSlide], [slideFor]). Moved
 * any other way (a screen giving a row the focus), the focus is shown a row ahead at once, the
 * column scrolled by whole rows.
 */
open class LookAheadScrollView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ScrollView(context, attrs), FittedRows {

    final override var rowHeight = 0
        private set
    final override var rowExtra = 0
        private set
    final override var fadeLength = 0
        private set

    private val ahead = Rect()
    private val fromBounds = Rect()
    private val toBounds = Rect()
    private var slideStart = 0
    private var moving = false
    private val slide = FocusSlide(this) { scrollTo(scrollX, slideStart + it) }

    private val column: ViewGroup? get() = getChildAt(0) as? ViewGroup

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val column = column ?: return
        val margin = column.paddingTop + column.paddingBottom
        val room = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom - margin
        // The rows as they are, without what this gave them.
        var added = 0
        for (i in 0 until column.childCount) {
            val row = column.getChildAt(i)
            if (row is ListEntry && row.visibility != View.GONE) added += row.measuredHeight - row.naturalHeight
        }
        val usual = usualHeight(column.childCount) { i ->
            val row = column.getChildAt(i)
            if (row is ListEntry && row.visibility != View.GONE) row.naturalHeight else 0
        }
        val fit = if (usual == 0 || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) null
        else fitRows(room, column.measuredHeight - margin - added, usual, 0)
        if (fitTo(fit, usual)) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (fit != null) setMeasuredDimension(measuredWidth, fit.window + margin + paddingTop + paddingBottom)
    }

    /** Gives the rows [fit]'s height, the usual row being [usual] px; whether it changed, so the column is to be measured again. */
    private fun fitTo(fit: RowFit?, usual: Int): Boolean {
        val column = column
        fadeLength = if (fit == null || column == null) 0 else usual + fit.extra + maxOf(column.paddingTop, column.paddingBottom)
        val height = if (fit == null) 0 else usual + fit.extra
        val extra = fit?.extra ?: 0
        if (height == rowHeight && extra == rowExtra) return false
        rowHeight = height
        rowExtra = extra
        if (column == null) return true
        column.forceLayout()
        for (i in 0 until column.childCount) column.getChildAt(i).forceLayout()
        return true
    }

    // Up and Down, a row at a time; the rest as a ScrollView does, and the arrows where no row is
    // that way in the column (the focus leaves it, or it scrolls to what isn't a row).
    override fun executeKeyEvent(event: KeyEvent): Boolean {
        val down = event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
        if (event.action != KeyEvent.ACTION_DOWN || !(down || event.keyCode == KeyEvent.KEYCODE_DPAD_UP) || !event.hasNoModifiers()) {
            return super.executeKeyEvent(event)
        }
        slide.finish()
        return arrow(down) || super.executeKeyEvent(event)
    }

    /** Moves the focus a row [down] or up, or slides the column under it; false: no row that way. */
    private fun arrow(down: Boolean): Boolean {
        val column = column ?: return false
        val direction = if (down) View.FOCUS_DOWN else View.FOCUS_UP
        val focused = findFocus() ?: return false
        val next = FocusFinder.getInstance().findNextFocus(this, focused, direction) ?: return false
        val fromRow = rowOf(focused, column) ?: return false
        val toRow = rowOf(next, column) ?: return false
        val beyond = rowAfter(toRow, column, if (down) 1 else -1)
        val top = column.top - scrollY
        val windowTop = paddingTop + column.paddingTop
        val windowBottom = height - paddingBottom - column.paddingBottom
        val distance = if (down) {
            val end = column.height - (height - paddingBottom - paddingTop)
            slideFor(beyond?.let { top + it.bottom }, top + toRow.bottom, windowBottom, toRow.top - fromRow.top, end - scrollY)
        } else {
            slideFor(beyond?.let { height - (top + it.top) }, height - (top + toRow.top), height - windowTop, fromRow.top - toRow.top, scrollY)
        }
        val signed = if (down) distance else -distance
        if (distance != 0 && fromRow is ListEntry && toRow is ListEntry && isLaidOut) {
            fromBounds.set(column.left + fromRow.left, top + fromRow.top, column.left + fromRow.right, top + fromRow.bottom)
            toBounds.set(column.left + toRow.left, top + toRow.top, column.left + toRow.right, top + toRow.bottom)
            slideStart = scrollY
            slide.start(fromRow, fromBounds, toRow, toBounds, signed)
        } else {
            scrollBy(0, signed)
        }
        // The rule above decided how far the column goes; the focus's move doesn't scroll it more.
        moving = true
        try {
            return next.requestFocus(direction)
        } finally {
            moving = false
        }
    }

    /** The row of [column] that [view] is, or is in. */
    private fun rowOf(view: View, column: ViewGroup): View? {
        var child = view
        while (true) {
            val parent = child.parent ?: return null
            if (parent === column) return child
            child = parent as? View ?: return null
        }
    }

    /** The next row shown after [row] ([step] 1) or before it (-1), if there's one. */
    private fun rowAfter(row: View, column: ViewGroup, step: Int): View? {
        var i = column.indexOfChild(row) + step
        while (i in 0 until column.childCount) {
            val child = column.getChildAt(i)
            if (child.visibility != View.GONE) return child
            i += step
        }
        return null
    }

    // The rectangle, in the content's coordinates, to show: the focused row's, as the focus moves
    // other than by an arrow, with a row and the column's margin before and after it, so the
    // column scrolls by whole rows. None while it slides, or an arrow moves the focus: the arrow's
    // rule has decided.
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        if (slide.running || moving) return 0
        val column = column ?: return super.computeScrollDeltaToGetChildRectOnScreen(rect)
        val row = rect.height()
        ahead.set(
            rect.left,
            (rect.top - row - column.paddingTop).coerceAtLeast(0),
            rect.right,
            (rect.bottom + row + column.paddingBottom).coerceAtMost(column.height),
        )
        return super.computeScrollDeltaToGetChildRectOnScreen(ahead)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        slide.draw(canvas, scrollY)
    }

    override fun onDetachedFromWindow() {
        slide.release()
        super.onDetachedFromWindow()
    }
}
