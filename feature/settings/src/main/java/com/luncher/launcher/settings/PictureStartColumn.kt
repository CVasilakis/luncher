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
 * keeps its shape, centered, so its rounded corners are the picture's.
 */
internal class PictureStartColumn(context: Context, attrs: AttributeSet?) : ScrollView(context, attrs) {

    private lateinit var preview: View
    private lateinit var shape: () -> ScreenSize

    /** Sizes [preview], one of the column's content, to [shape]; before the column is first measured. */
    fun hold(preview: View, shape: () -> ScreenSize) {
        this.preview = preview
        this.shape = shape
    }

    // Measured twice when it doesn't fit: first with the preview at its full height, then with it
    // shorter by what the content takes beyond the screen, so the options, Save and Delete all as
    // measured decide, never an estimate of them.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val content = getChildAt(0)
        val shape = shape()
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight - content.paddingLeft - content.paddingRight
        val natural = shape.heightAt(width)
        preview.layoutParams.width = width
        preview.layoutParams.height = natural
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val room = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
        val beyond = content.measuredHeight - room
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED || beyond <= 0) return
        val height = (natural - beyond).coerceAtLeast(natural / 4)
        preview.layoutParams.height = height
        preview.layoutParams.width = shape.widthAt(height)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
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
