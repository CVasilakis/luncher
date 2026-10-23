package com.luncher.launcher.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * The appearance editor's two columns: the title, the preview and the buttons, centered together
 * in the height, and the groups' container, whose top is level with the title's, and which reaches
 * down to the bottom margin from the start, as the side sheets' lists do, whether its groups are
 * open or not; its groups scroll once they're taller.
 *
 * The container is measured for the room below the title as the columns are measured: where the
 * title will be follows from the start column's measures, centered as that column centers its
 * children. Measured for more room first, the list in it would lay its rows out for that room and
 * lose its scroll ([LookAheadScrollView][com.luncher.launcher.ui.LookAheadScrollView] fits its rows
 * to its room). Laid out, the container is moved level with the title; if the title isn't where
 * the measures said, it's measured again there.
 */
internal class AppearanceEditorLayout(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    private val title: View get() = findViewById(R.id.settings_appearance_title)
    private val start: ViewGroup get() = findViewById(R.id.settings_appearance_start)
    private val end: View get() = findViewById(R.id.settings_appearance_end)
    private val frame: View get() = findViewById(R.id.settings_appearance_frame)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val end = end
        val endTop = paddingTop + (end.layoutParams as MarginLayoutParams).topMargin
        measureFrame(frameTop(titleTop() - endTop, end), end.measuredHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val end = end
        val frame = frame
        val title = title
        val top = frameTop((title.parent as View).top + title.top - end.top, end)
        if (end.height - top - end.paddingBottom != measuredRoom) measureFrame(top, end.height)
        frame.layout(frame.left, top, frame.right, top + frame.measuredHeight)
    }

    /** The room below [top] in the end column, [endHeight] tall, the container measured for it. */
    private var measuredRoom = -1

    private fun measureFrame(top: Int, endHeight: Int) {
        val end = end
        val frame = frame
        measuredRoom = endHeight - top - end.paddingBottom
        frame.measure(
            MeasureSpec.makeMeasureSpec(frame.measuredWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(measuredRoom.coerceAtLeast(0), MeasureSpec.EXACTLY),
        )
    }

    /** The container's top in the end column: level with the title, [titleTop] from the column's top, within the column's padding. */
    private fun frameTop(titleTop: Int, end: View) = titleTop.coerceAtLeast(end.paddingTop)

    /** Where the title will be, from this layout's top: the start column centers its children in its height. */
    private fun titleTop(): Int {
        val start = start
        var total = start.paddingTop + start.paddingBottom
        for (i in 0 until start.childCount) {
            val child = start.getChildAt(i)
            if (child.visibility == View.GONE) continue
            val params = child.layoutParams as MarginLayoutParams
            total += child.measuredHeight + params.topMargin + params.bottomMargin
        }
        val startTop = paddingTop + (start.layoutParams as MarginLayoutParams).topMargin
        return startTop + start.paddingTop + (start.measuredHeight - total) / 2 + (title.layoutParams as MarginLayoutParams).topMargin
    }
}
