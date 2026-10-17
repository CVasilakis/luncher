package com.luncher.launcher.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ScrollView

/**
 * The background editor's start: the preview, the options and Save, one below the other. The
 * preview gets the height the others leave, at most its 16:9 height at the column's width, so
 * Save shows without scrolling; at least a quarter of that, so it still shows on the smallest
 * screens, where the column then scrolls as the focus moves.
 */
internal class BackgroundStartColumn(context: Context, attrs: AttributeSet?) : ScrollView(context, attrs) {

    // Measured twice when it doesn't fit: first with the preview at its 16:9 height, then with it
    // shorter by what the content takes beyond the screen, so the options and Save as measured
    // decide, never an estimate of them.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val content = getChildAt(0)
        val preview = content.findViewById<View>(R.id.settings_background_preview)
        val natural = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight) * 9 / 16
        preview.layoutParams.height = natural
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val room = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
        val beyond = content.measuredHeight - room
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED || beyond <= 0) return
        preview.layoutParams.height = (natural - beyond).coerceAtLeast(natural / 4)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
