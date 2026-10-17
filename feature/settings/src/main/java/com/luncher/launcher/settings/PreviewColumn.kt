package com.luncher.launcher.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.luncher.launcher.ui.screenSize

/**
 * Add from folder's column beside the list: the preview, of the TV's shape as the wallpaper fills
 * it, at most `settings_pictures_preview_width` wide, and the note below it. On a screen too short
 * for both, the preview gets smaller, so the note still shows whole. The preview is drawn at the
 * size it gets here.
 */
internal class PreviewColumn(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val preview = getChildAt(0)
        val note = getChildAt(1)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val screen = context.screenSize()
        val gap = (note.layoutParams as LayoutParams).topMargin
        note.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        var previewHeight = screen.heightAt(width)
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            previewHeight = previewHeight.coerceAtMost(MeasureSpec.getSize(heightMeasureSpec) - note.measuredHeight - gap).coerceAtLeast(0)
        }
        preview.layoutParams.width = screen.widthAt(previewHeight)
        preview.layoutParams.height = previewHeight
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** The preview's size as last laid out: what it's drawn at. 0 x 0 before the first layout. */
    val previewSize: Pair<Int, Int> get() = getChildAt(0).let { it.width to it.height }

    /** Called when the preview's size changes, to draw it again at the new size. */
    var onPreviewSized: (() -> Unit)? = null

    private var laidOut = 0 to 0

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (previewSize == laidOut) return
        laidOut = previewSize
        onPreviewSized?.invoke()
    }
}
