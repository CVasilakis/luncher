package com.luncher.launcher.settings

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.ScrollView
import com.luncher.launcher.ui.ScreenSize

/**
 * A picture editor's start: its preview, then the options, and Save with Delete all, one below
 * the other. The preview, which the screen gives with its shape ([hold]: the background editor's,
 * the home screen at the TV's shape), gets the height the others leave, at most its height at the
 * column's width, so Save and Delete all show without scrolling; at least a quarter of that, so it
 * still shows on the smallest screens, where the column then scrolls as the focus moves: a focused
 * view comes inside its padding, the overscan margin, with room for its zoom and shadow there. It
 * keeps its shape, centered, so its rounded corners are the picture's. A screen may give it a least
 * height instead (the tile banner picker's, as Add from folder's preview): shorter than that, too
 * small to show anything, it goes, and the others get its room.
 */
internal class PictureStartColumn(context: Context, attrs: AttributeSet?) : ScrollView(context, attrs) {

    private lateinit var preview: View
    private lateinit var shape: () -> ScreenSize
    private var least: Int? = null

    /**
     * Sizes [preview], one of the column's content, to [shape]; before the column is first
     * measured. Where it would be shorter than [least] px, if given, it goes.
     */
    fun hold(preview: View, least: Int? = null, shape: () -> ScreenSize) {
        this.preview = preview
        this.least = least
        this.shape = shape
    }

    // Measured twice when it doesn't fit: first with the preview at its full height, then with it
    // shorter by what the content takes beyond the screen, so the options, Save and Delete all as
    // measured decide, never an estimate of them. What the others take is worked out with the
    // preview counted as shown, whether it shows now or not, so its going, which leaves more room,
    // never brings it back.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val content = getChildAt(0)
        val shape = shape()
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight - content.paddingLeft - content.paddingRight
        val natural = shape.heightAt(width)
        preview.layoutParams.width = width
        preview.layoutParams.height = natural
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val room = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
        val shown = if (preview.visibility == GONE) 0 else natural
        val beyond = content.measuredHeight - shown + natural - room
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED || beyond <= 0) {
            if (show(true)) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val least = least
        if (least != null && natural - beyond < least) {
            if (show(false)) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        show(true)
        val height = (natural - beyond).coerceAtLeast(natural / 4)
        preview.layoutParams.height = height
        preview.layoutParams.width = shape.widthAt(height)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** Shows the preview, or not; whether that changed. */
    private fun show(shown: Boolean): Boolean {
        val visibility = if (shown) VISIBLE else GONE
        if (preview.visibility == visibility) return false
        preview.visibility = visibility
        return true
    }

    // Inside the padding: ScrollView brings a focused view to its own edges, into the overscan
    // margin, where nothing cuts the content (clipToPadding and clipChildren off) but the screen
    // does, a button's shadow first. [rect] is in the content's place without the scroll.
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        val content = getChildAt(0) ?: return 0
        val top = scrollY + paddingTop
        val bottom = scrollY + height - paddingBottom
        val delta = when {
            rect.bottom > bottom && rect.top > top -> minOf(rect.bottom - bottom, rect.top - top)
            rect.top < top && rect.bottom < bottom -> maxOf(rect.top - top, rect.bottom - bottom)
            else -> 0
        }
        val range = (content.height - (height - paddingTop - paddingBottom)).coerceAtLeast(0)
        return (scrollY + delta).coerceIn(0, range) - scrollY
    }
}
