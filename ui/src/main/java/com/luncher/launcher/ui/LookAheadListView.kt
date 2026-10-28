package com.luncher.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.SoundEffectConstants
import android.view.View
import android.widget.ListView

/**
 * A list that scrolls a row ahead of its selection: while there's more beyond an edge, the
 * selected row stays a row away from it, so the next row shows whole before the selection gets
 * there, and the selection is never on a row the frame fades ([ScrollFrame]); only the first and
 * the last rows go to the edge. Its rows are [ListEntry]s of one height, the divider a gap.
 *
 * Its window ends on a whole row: while the rows don't all fit, each is a little taller or
 * shorter, so a whole number of them fill the room ([fitRows]), and the list is that tall. Up and
 * Down move the selection while the row beyond the next one shows whole; else the list slides a
 * row under the selection, which stays where it is ([FocusSlide], [slideFor]). [setSelection]
 * shows the row a row ahead, the list scrolled by whole rows. The usual row is the first one,
 * measured before any shows.
 *
 * `ListView`'s own margin for its fading edges, a row, keeps a selection made any other way a row
 * from an edge too. The fading edges themselves stay off: the frame fades, with one gradient, not
 * a layer.
 */
open class LookAheadListView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ListView(context, attrs), FittedRows {

    final override var rowHeight = 0
        private set
    final override var rowExtra = 0
        private set
    final override var fadeLength = 0
        private set

    /** A row and the gap after it, as the window shows them; 0 while all rows show. */
    private var pitch = 0

    /** The first row, drawn apart to measure a row before the list shows any. */
    private var probe: View? = null

    private val fromBounds = Rect()
    private val toBounds = Rect()
    private var sliding = -1
    private var slideTop = 0
    private val slide = FocusSlide(this) { offset -> slideRowTo(slideTop - offset) }

    override fun getVerticalFadingEdgeLength(): Int {
        val row = selectedView ?: getChildAt(0) ?: return 0
        return row.height + dividerHeight
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val count = adapter?.count ?: 0
        val row = if (count == 0) 0 else rowHeight(widthMeasureSpec)
        val room = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
        val fit = if (row == 0 || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) null
        else fitRows(room, count * (row + dividerHeight) - dividerHeight, row, dividerHeight)
        if (fit == null) {
            fitTo(0, 0)
            pitch = 0
            fadeLength = 0
            return
        }
        fitTo(row + fit.extra, fit.extra)
        pitch = row + fit.extra + dividerHeight
        fadeLength = row + fit.extra + maxOf(paddingTop, paddingBottom)
        setMeasuredDimension(measuredWidth, fit.window + paddingTop + paddingBottom)
    }

    /** A row's height as the adapter makes it, without the height this gives it. */
    private fun rowHeight(widthMeasureSpec: Int): Int {
        getChildAt(0)?.let { return (it as? ListEntry)?.naturalHeight ?: it.measuredHeight }
        val adapter = adapter ?: return 0
        val row = adapter.getView(0, probe?.takeIf { adapter.viewTypeCount == 1 }, this)
        probe = row
        val params = row.layoutParams
        val width = getChildMeasureSpec(widthMeasureSpec, paddingLeft + paddingRight, params?.width ?: LayoutParams.MATCH_PARENT)
        val height = params?.height?.takeIf { it > 0 }?.let { MeasureSpec.makeMeasureSpec(it, MeasureSpec.EXACTLY) }
            ?: MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        row.measure(width, height)   // not in the list: as tall as it is
        return row.measuredHeight
    }

    /** Gives the rows [height] px, [extra] more than the usual; they're measured again as they're laid out. */
    private fun fitTo(height: Int, extra: Int) {
        if (height == rowHeight && extra == rowExtra) return
        rowHeight = height
        rowExtra = extra
        for (i in 0 until childCount) getChildAt(i).forceLayout()
    }

    // A row ahead of the selection, by whole rows: the row before it at the top, or the last
    // window of rows near the end.
    override fun setSelection(position: Int) {
        slide.finish()
        val shown = if (pitch == 0) 0 else (height - paddingTop - paddingBottom + dividerHeight) / pitch
        if (shown == 0 || isInTouchMode) return selectFromTop(position, 0)
        val first = (position - 1).coerceIn(0, (count - shown).coerceAtLeast(0))
        selectFromTop(position, (position - first) * pitch)
    }

    /**
     * Selects [position], its row [y] px below the list's top padding. `ListView` adds the padding
     * as it was at its last measure: none before the first one, which put the row at the list's top
     * edge, its margin left out, until something scrolled it (Add from folder, whose rows don't
     * all show).
     */
    private fun selectFromTop(position: Int, y: Int) {
        setSelectionFromTop(position, y + paddingTop - listPaddingTop)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val down = keyCode == KeyEvent.KEYCODE_DPAD_DOWN
        if (!(down || keyCode == KeyEvent.KEYCODE_DPAD_UP) || !event.hasNoModifiers() || isInTouchMode) {
            return super.onKeyDown(keyCode, event)
        }
        slide.finish()
        return arrow(down) || super.onKeyDown(keyCode, event)
    }

    /** Moves the selection a row [down] or up, or slides the list under it; false: not here. */
    private fun arrow(down: Boolean): Boolean {
        val selected = selectedItemPosition
        if (selected < 0) return false
        val target = if (down) selected + 1 else selected - 1
        if (target !in 0 until count) return false
        val fromRow = getChildAt(selected - firstVisiblePosition) ?: return false
        val toRow = getChildAt(target - firstVisiblePosition) ?: return false
        val beyondPosition = if (down) target + 1 else target - 1
        // Not laid out: beyond the window, so it doesn't show whole.
        val beyond = if (beyondPosition in 0 until count) getChildAt(beyondPosition - firstVisiblePosition) else null
        val hidden = beyondPosition in 0 until count && beyond == null
        val distance = if (down) {
            val last = getChildAt(childCount - 1)
            val left = (count - 1 - lastVisiblePosition) * (toRow.height + dividerHeight) + last.bottom - (height - paddingBottom)
            slideFor(if (hidden) Int.MAX_VALUE else beyond?.bottom, toRow.bottom, height - paddingBottom, toRow.top - fromRow.top, left)
        } else {
            val first = getChildAt(0)
            val left = firstVisiblePosition * (toRow.height + dividerHeight) + paddingTop - first.top
            slideFor(if (hidden) Int.MAX_VALUE else beyond?.let { height - it.top }, height - toRow.top, height - paddingTop, fromRow.top - toRow.top, left)
        }
        playSoundEffect(SoundEffectConstants.getContantForFocusDirection(if (down) View.FOCUS_DOWN else View.FOCUS_UP))
        if (distance != 0 && fromRow is ListEntry && toRow is ListEntry && isLaidOut) {
            fromBounds.set(fromRow.left, fromRow.top, fromRow.right, fromRow.bottom)
            toBounds.set(toRow.left, toRow.top, toRow.right, toRow.bottom)
            select(target, toRow.top)
            sliding = target
            slideTop = toRow.top
            slide.start(fromRow, fromBounds, toRow, toBounds, if (down) distance else -distance)
        } else {
            select(target, toRow.top - if (down) distance else -distance)
        }
        return true
    }

    /**
     * Selects [position], its row at [top], at once, as `ListView`'s own arrows do: laid out now,
     * not at the next frame, so OK right after the arrow is on that row.
     */
    private fun select(position: Int, top: Int) {
        selectFromTop(position, top - paddingTop)
        layoutChildren()
    }

    /** Scrolls the list so the row the slide goes to is at [top]; from where it is, as the list may have laid itself out since. */
    private fun slideRowTo(top: Int) {
        val row = getChildAt(sliding - firstVisiblePosition) ?: return
        if (row.top != top) scrollListBy(row.top - top)
    }

    override fun layoutChildren() {
        super.layoutChildren()
        // Laid out with the selection where it was as the slide started: back where the slide is.
        if (slide.running) slide.reapply()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        slide.draw(canvas, 0)
    }

    override fun onDetachedFromWindow() {
        slide.release()
        super.onDetachedFromWindow()
    }
}
