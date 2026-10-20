package com.luncher.launcher.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.luncher.launcher.ui.screenSize

/**
 * Add from folder's column beside the list: the preview, of the TV's shape as the wallpaper fills
 * it, at most `settings_pictures_preview_width` wide, and below it Add all and the note. On a
 * screen too short for them all, the preview gets smaller, so the others still show whole; at
 * least a quarter of its height, so it still shows on the smallest screens, where the column then
 * scrolls: to its end while Add all has the focus, so the note shows too, and back to its top
 * when the list has it. The preview is drawn at the size it gets here.
 */
internal class PreviewColumn(context: Context, attrs: AttributeSet?) : ScrollView(context, attrs) {

    private val content: ViewGroup get() = getChildAt(0) as ViewGroup

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val preview = content.getChildAt(0)
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val screen = context.screenSize()
        // What the views below the preview take, with their gaps.
        var below = 0
        for (i in 1 until content.childCount) {
            val child = content.getChildAt(i)
            if (child.visibility == GONE) continue
            val params = child.layoutParams as LinearLayout.LayoutParams
            child.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                if (params.height >= 0) MeasureSpec.makeMeasureSpec(params.height, MeasureSpec.EXACTLY) else MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            below += child.measuredHeight + params.topMargin
        }
        val natural = screen.heightAt(width)
        var previewHeight = natural
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            val room = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom - below
            previewHeight = previewHeight.coerceAtMost(room).coerceAtLeast(natural / 4)
        }
        preview.layoutParams.width = screen.widthAt(previewHeight)
        preview.layoutParams.height = previewHeight
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** The preview's size as last laid out: what it's drawn at. 0 x 0 before the first layout. */
    val previewSize: Pair<Int, Int> get() = content.getChildAt(0).let { it.width to it.height }

    /** Called when the preview's size changes, to draw it again at the new size. */
    var onPreviewSized: (() -> Unit)? = null

    private var laidOut = 0 to 0

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (previewSize == laidOut) return
        laidOut = previewSize
        onPreviewSized?.invoke()
    }

    // Add all, the only view here that takes the focus: the end shows, the note below it too.
    override fun requestChildFocus(child: View?, focused: View?) {
        super.requestChildFocus(child, focused)
        scrollTo(0, (content.height - (height - paddingTop - paddingBottom)).coerceAtLeast(0))
    }

    override fun clearChildFocus(child: View?) {
        super.clearChildFocus(child)
        scrollTo(0, 0)
    }
}
