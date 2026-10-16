package com.luncher.launcher.settings

import android.view.View

/**
 * The height of a panel's list of [rowCount] rows: as tall as its rows, up to [max], and no
 * taller than the screen leaves room for beside the rest of the [panel] (its title, its padding)
 * and its margins, e.g. on a 720p screen at high density. When the rows don't fit, the list ends
 * on half a row, which shows there's more. [panel] is measured here without the list, which must
 * be 0 px tall so far.
 */
internal fun listHeight(panel: View, rowCount: Int, max: Int = Int.MAX_VALUE): Int {
    val resources = panel.resources
    val row = resources.getDimensionPixelSize(R.dimen.settings_entry_height)
    val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    panel.measure(unspecified, unspecified)
    val margin = resources.getDimensionPixelSize(R.dimen.settings_panel_margin)
    val room = minOf(max, resources.displayMetrics.heightPixels - 2 * margin - panel.measuredHeight)
    if (rowCount * row <= room) return rowCount * row
    return ((room - row / 2) / row).coerceAtLeast(0) * row + row / 2
}
