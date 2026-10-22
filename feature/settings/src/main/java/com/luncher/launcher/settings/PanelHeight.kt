package com.luncher.launcher.settings

import android.view.View

/**
 * The height of a side sheet's [list] of [rowCount] rows (Hide apps): as tall as its rows, and no
 * taller than the sheet leaves room for beside the rest of the [panel] (its title, its padding),
 * e.g. on a 720p screen at high density. When the rows don't fit, the list ends on half a row,
 * which shows there's more, and fades out there ([ScrollFrame][com.luncher.launcher.ui.ScrollFrame]).
 * [panel] is measured here at the sheet's [width] without the list, which must be 0 px tall so far.
 */
internal fun listHeight(panel: View, list: View, rowCount: Int, width: Int): Int {
    val resources = panel.resources
    val row = resources.getDimensionPixelSize(R.dimen.settings_row_height) + resources.getDimensionPixelSize(R.dimen.settings_entry_gap)
    val padding = list.paddingTop + list.paddingBottom
    // At the sheet's width, where its texts wrap as they will.
    panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
    val margin = resources.getDimensionPixelSize(R.dimen.settings_sheet_margin)
    val room = resources.displayMetrics.heightPixels - 2 * margin - panel.measuredHeight - padding
    if (rowCount * row <= room) return rowCount * row + padding
    return ((room - row / 2) / row).coerceAtLeast(0) * row + row / 2 + padding
}
