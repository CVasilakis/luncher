package com.luncher.launcher.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.ScrollView

/**
 * A column of rows that scrolls ahead of the focus, as [LookAheadListView] does: while there's more
 * beyond an edge, the focused row stays a row away from it, so the next row shows whole before the
 * focus gets there, and the focus is never on a row the frame fades ([ScrollFrame]); only the first
 * and the last rows go to the edge. A row is the focused one's height.
 *
 * The focused row is shown with a row more above and below it, where there's more: the room
 * `ScrollView` would otherwise leave for its fading edges, which stay off.
 */
open class LookAheadScrollView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ScrollView(context, attrs) {

    private val ahead = Rect()

    // The rectangle, in the content's coordinates, to show: the focused row's, as the focus moves.
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        val end = getChildAt(0)?.height ?: return super.computeScrollDeltaToGetChildRectOnScreen(rect)
        val row = rect.height()
        ahead.set(rect.left, (rect.top - row).coerceAtLeast(0), rect.right, (rect.bottom + row).coerceAtMost(end))
        return super.computeScrollDeltaToGetChildRectOnScreen(ahead)
    }
}
