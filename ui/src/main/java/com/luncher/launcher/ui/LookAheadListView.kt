package com.luncher.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.ListView

/**
 * A list that scrolls ahead of its selection: while there's more beyond an edge, the selected row
 * stays a row away from it, so the next row shows whole before the selection gets there, and the
 * selection is never on a row the frame fades ([ScrollFrame]); only the first and the last rows go
 * to the edge. A row is the selected one's height, with the gap after it.
 *
 * It's `ListView`'s own margin for its fading edges, which it keeps when the keys move the selection
 * and when it lays itself out. The fading edges themselves stay off: the frame fades, with one
 * gradient, not a layer.
 */
open class LookAheadListView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ListView(context, attrs) {

    override fun getVerticalFadingEdgeLength(): Int {
        val row = selectedView ?: getChildAt(0) ?: return 0
        return row.height + dividerHeight
    }
}
