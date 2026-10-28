package com.luncher.launcher.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.luncher.launcher.ui.ListEntry

/**
 * An option's row of the appearance editor: its name, and its value's chip at its end, beside the
 * name; or below the name, at the row's end, so the name shows whole. Below in every row of the
 * list where any row's chip, with its option's widest value ([widestValue]), leaves its name too
 * little room (more than two lines, or a word wider than the room: a long translation on a narrow
 * screen), so the rows are alike, and a row's height and name stay put as its value changes. It
 * places the two itself, so which way they go never asks for another layout pass. The end is the
 * left in a right-to-left language, as the row mirrors.
 */
internal class StepperRow(context: Context, attrs: AttributeSet?) : ListEntry(context, attrs) {

    private val gap = resources.getDimensionPixelSize(R.dimen.settings_appearance_value_gap)
    private var below = false

    private val label: TextView get() = findViewById(R.id.settings_option_label)
    private val value: TextView get() = findViewById(R.id.settings_option_value)
    private val chip: View get() = getChildAt(1)

    /** How wide the text of the option's widest value is, in px: the row goes by it, whichever value it shows. */
    var widestValue = 0
        set(value) {
            if (value == field) return
            field = value
            fitWidth = -1
            requestLayout()
        }

    /** The row's width [fits] was found for; -1: none yet. */
    private var fitWidth = -1
    private var fits = false

    override fun measureContent(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val label = label
        val chip = chip
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val inner = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        // Every row of the list alike, those of the groups that are closed too.
        val column = parent as? ViewGroup
        below = if (column == null) !fitsBeside(width) else (0 until column.childCount).any { (column.getChildAt(it) as? StepperRow)?.fitsBeside(width) == false }
        chip.measure(unspecified, unspecified)
        val content = if (below) {
            label.measure(MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY), unspecified)
            label.measuredHeight + gap + chip.measuredHeight
        } else {
            label.measure(MeasureSpec.makeMeasureSpec(roomBeside(inner), MeasureSpec.EXACTLY), unspecified)
            maxOf(label.measuredHeight, chip.measuredHeight)
        }
        setMeasuredDimension(width, maxOf(content + paddingTop + paddingBottom, minimumHeight))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val label = label
        val chip = chip
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val width = r - l
        val height = b - t
        val start = if (rtl) width - paddingRight else paddingLeft
        val end = if (rtl) paddingLeft else width - paddingRight
        fun place(view: View, fromStart: Boolean, top: Int) {
            val left = when {
                fromStart && !rtl -> start
                fromStart -> start - view.measuredWidth
                !rtl -> end - view.measuredWidth
                else -> end
            }
            view.layout(left, top, left + view.measuredWidth, top + view.measuredHeight)
        }
        if (below) {
            val top = paddingTop + (height - paddingTop - paddingBottom - label.measuredHeight - gap - chip.measuredHeight) / 2
            place(label, fromStart = true, top)
            place(chip, fromStart = false, top + label.measuredHeight + gap)
        } else {
            val middle = paddingTop + (height - paddingTop - paddingBottom) / 2
            place(label, fromStart = true, middle - label.measuredHeight / 2)
            place(chip, fromStart = false, middle - chip.measuredHeight / 2)
        }
    }

    /**
     * Whether the name fits beside the chip with the widest value, the row [width] px wide: in two
     * lines at most, each whole, no word wider than the room. Found once for a width.
     */
    private fun fitsBeside(width: Int): Boolean {
        if (width == fitWidth) return fits
        val label = label
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        chip.measure(unspecified, unspecified)
        val room = roomBeside((width - paddingLeft - paddingRight).coerceAtLeast(0))
        fits = room > 0 && run {
            label.measure(MeasureSpec.makeMeasureSpec(room, MeasureSpec.EXACTLY), unspecified)
            val layout = label.layout
            layout != null && layout.lineCount <= LINES_BESIDE &&
                (0 until layout.lineCount).none { layout.getEllipsisCount(it) > 0 || layout.getLineMax(it) > room - label.totalPaddingLeft - label.totalPaddingRight }
        }
        fitWidth = width
        return fits
    }

    /** The name's room beside the chip, in a row [inner] px wide inside its padding: what the chip leaves with its widest value, the chip measured. */
    private fun roomBeside(inner: Int): Int {
        val value = value
        val shown = value.measuredWidth - value.totalPaddingLeft - value.totalPaddingRight
        return (inner - chip.measuredWidth - maxOf(0, widestValue - shown) - gap).coerceAtLeast(0)
    }

    private companion object {
        /** The most lines the name takes beside the chip. */
        const val LINES_BESIDE = 2
    }
}
