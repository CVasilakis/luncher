package com.luncher.launcher.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView

/**
 * The background editor's start: the preview, the options and Save, one below the other. The preview gets the height the others leave, at most its 16:9 height at the column's
 * width, so Save shows without scrolling; at least a quarter of that, so it still shows on the
 * smallest screens, where the column then scrolls as the focus moves.
 */
internal class BackgroundStartColumn(context: Context, attrs: AttributeSet?) : ScrollView(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val content = getChildAt(0) as ViewGroup
        val preview = content.findViewById<View>(R.id.settings_background_preview)
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        var others = 0
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            val margins = child.layoutParams as MarginLayoutParams
            if (child !== preview) {
                child.measure(MeasureSpec.makeMeasureSpec(width - margins.leftMargin - margins.rightMargin, MeasureSpec.EXACTLY), unspecified)
                others += child.measuredHeight
            }
            others += margins.topMargin + margins.bottomMargin
        }
        val natural = width * 9 / 16
        val room = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom - others
        preview.layoutParams.height = room.coerceIn(natural / 4, natural)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
