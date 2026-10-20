package com.luncher.launcher.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/**
 * Entries side by side, each half the row (a third, for three, …), as long as each one's text fits
 * its share; else one below the other, each the whole row, so no text is cut (a long translation,
 * a narrow column). The background editor's Save and Delete all.
 */
internal class EntriesRow(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val share = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight) / childCount.coerceAtLeast(1)
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        var fits = true
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(unspecified, unspecified)
            if (child.measuredWidth > share) fits = false
        }
        orientation = if (fits) HORIZONTAL else VERTICAL
        for (i in 0 until childCount) {
            val params = getChildAt(i).layoutParams as LayoutParams
            params.width = if (fits) 0 else LayoutParams.MATCH_PARENT
            params.weight = if (fits) 1f else 0f
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
