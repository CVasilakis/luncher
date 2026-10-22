package com.luncher.launcher.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * The appearance editor's start: the title, the preview and the buttons, one below the other. The
 * preview is as tall as its screen at the column's width, unless that leaves the others too little
 * room (a short screen, large text): then it gets what they leave, at least a quarter of that, and
 * draws its screen smaller, centered.
 */
internal class EditorStartColumn(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val preview = findViewById<View>(R.id.settings_appearance_preview)
        preview.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) return super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // What each one takes as large as it is, the preview at the column's width.
        val width = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight, MeasureSpec.EXACTLY)
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        var others = paddingTop + paddingBottom
        var natural = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            val params = child.layoutParams as LayoutParams
            child.measure(width, unspecified)
            if (child === preview) natural = child.measuredHeight else others += child.measuredHeight
            others += params.topMargin + params.bottomMargin
        }
        val room = MeasureSpec.getSize(heightMeasureSpec) - others
        if (room < natural) preview.layoutParams.height = room.coerceAtLeast(natural / 4)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
