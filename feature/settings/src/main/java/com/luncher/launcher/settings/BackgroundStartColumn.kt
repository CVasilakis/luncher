package com.luncher.launcher.settings

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.ScrollView
import com.luncher.launcher.ui.screenSize

/**
 * The background editor's start: the preview, the options, and Save with Delete all, one below
 * the other. The preview gets the height the others leave, at most its height at the column's
 * width (of the TV's shape, as its sample screen), so Save and Delete all show without scrolling;
 * at least a quarter of that, so it still shows on the smallest screens, where the column then
 * scrolls as the focus moves: a focused view comes inside its padding, the overscan margin, with
 * room for its zoom and shadow there. It keeps the TV's shape, centered, so its rounded corners
 * are the picture's.
 */
internal class BackgroundStartColumn(context: Context, attrs: AttributeSet?) : ScrollView(context, attrs) {

    // Measured twice when it doesn't fit: first with the preview at its full height, then with it
    // shorter by what the content takes beyond the screen, so the options, Save and Delete all as
    // measured decide, never an estimate of them.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val content = getChildAt(0)
        val preview = content.findViewById<View>(R.id.settings_background_preview)
        val screen = context.screenSize()
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight - content.paddingLeft - content.paddingRight
        val natural = screen.heightAt(width)
        preview.layoutParams.width = width
        preview.layoutParams.height = natural
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val room = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
        val beyond = content.measuredHeight - room
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED || beyond <= 0) return
        val height = (natural - beyond).coerceAtLeast(natural / 4)
        preview.layoutParams.height = height
        preview.layoutParams.width = screen.widthAt(height)
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
