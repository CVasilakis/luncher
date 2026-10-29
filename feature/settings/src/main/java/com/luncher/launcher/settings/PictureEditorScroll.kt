package com.luncher.launcher.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.ScrollView

/**
 * What scrolls in a picture editor's start column ([PictureStartColumn]): the preview, the app's
 * name, the options, between the title's line and Save with Delete all, where they don't all fit
 * (a short screen, large text, a long translation). It cuts what it holds at its edges, fading it
 * out where there's more; the room for a focused field's zoom is inside them (its content's
 * padding), so a focused field shows whole, and clear of the fade but at the ends.
 */
internal class PictureEditorScroll(context: Context, attrs: AttributeSet?) : ScrollView(context, attrs) {

    // Never focused itself: a ScrollView takes the focus while it holds nothing that can (every
    // option dimmed, as with No picture), where nothing shows it. Left from the pictures goes on
    // to Save instead, and Up from Save stays there. Set here, as ScrollView sets it after the
    // layout's attributes.
    init {
        isFocusable = false
    }

    // Inside the content's padding, and the fade where there's more beyond it. [rect] is in the
    // content's place without the scroll.
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        val content = getChildAt(0) ?: return 0
        val fade = verticalFadingEdgeLength
        val top = scrollY + content.paddingTop + fade
        val bottom = scrollY + height - content.paddingBottom - fade
        val delta = when {
            rect.bottom > bottom && rect.top > top -> minOf(rect.bottom - bottom, rect.top - top)
            rect.top < top && rect.bottom < bottom -> maxOf(rect.top - top, rect.bottom - bottom)
            else -> 0
        }
        val range = (content.height - height).coerceAtLeast(0)
        return (scrollY + delta).coerceIn(0, range) - scrollY
    }

    // Cut at its edges: the column, which leaves room around Save and Delete all for their zoom
    // and shadow, cuts nothing, and without a padding a view doesn't cut what it holds.
    override fun dispatchDraw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.clipRect(scrollX, scrollY, scrollX + width, scrollY + height)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saved)
    }
}
