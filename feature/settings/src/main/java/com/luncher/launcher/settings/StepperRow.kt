package com.luncher.launcher.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import com.luncher.launcher.ui.ListEntry

/**
 * An option's row of the appearance editor: its name, and its value's chip at its end, beside the
 * name; or, where the chip leaves the name too little room (more than two lines, or a word wider
 * than the room: a long translation on a narrow screen), below the name, at the row's end, so the name shows whole. It
 * places the two itself, so which way they go never asks for another layout pass. The end is the
 * left in a right-to-left language, as the row mirrors.
 */
internal class StepperRow(context: Context, attrs: AttributeSet?) : ListEntry(context, attrs) {

    private val gap = resources.getDimensionPixelSize(R.dimen.settings_appearance_value_gap)
    private var below = false

    private val label: TextView get() = findViewById(R.id.settings_option_label)
    private val chip: View get() = getChildAt(1)

    override fun measureContent(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val label = label
        val chip = chip
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val inner = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        chip.measure(unspecified, unspecified)
        val room = inner - chip.measuredWidth - gap
        if (room > 0) label.measure(MeasureSpec.makeMeasureSpec(room, MeasureSpec.EXACTLY), unspecified)
        below = room <= 0 || !fitsBeside(label, room)
        val content = if (below) {
            label.measure(MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY), unspecified)
            label.measuredHeight + gap + chip.measuredHeight
        } else {
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

    /** Whether [label], laid out [room] px wide, takes two lines at most, each whole: no word wider than the room. */
    private fun fitsBeside(label: TextView, room: Int): Boolean {
        val layout = label.layout ?: return false
        if (layout.lineCount > LINES_BESIDE) return false
        return (0 until layout.lineCount).none { layout.getEllipsisCount(it) > 0 || layout.getLineMax(it) > room - label.totalPaddingLeft - label.totalPaddingRight }
    }

    private companion object {
        /** The most lines the name takes beside the chip. */
        const val LINES_BESIDE = 2
    }
}
