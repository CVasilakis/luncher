package com.luncher.launcher.testing

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.luncher.launcher.ui.R
import org.junit.Assert.assertTrue
import org.robolectric.shadows.ShadowDialog

/** The dialog showing over [activity]: a margin from [screen]'s edges, its texts whole. Closes it. */
fun assertDialogFits(screen: TvScreen, activity: Activity) {
    val dialog = ShadowDialog.getLatestDialog()
    val panel = dialog.findViewById<View>(R.id.dialog_panel)
    val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    panel.measure(View.MeasureSpec.makeMeasureSpec(panel.layoutParams.width, View.MeasureSpec.EXACTLY), unspecified)
    val metrics = activity.resources.displayMetrics
    val margin = activity.resources.getDimensionPixelSize(R.dimen.dialog_margin)
    assertTrue(
        "dialog ${panel.measuredWidth} x ${panel.measuredHeight} on $screen, ${metrics.widthPixels} x ${metrics.heightPixels} less $margin around",
        panel.measuredWidth <= metrics.widthPixels - 2 * margin && panel.measuredHeight <= metrics.heightPixels - 2 * margin,
    )
    panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
    dialog.findViewById<TextView>(R.id.dialog_text).takeIf { it.visibility == View.VISIBLE }?.let { assertWhole(screen, it) }
    val entries = dialog.findViewById<ViewGroup>(R.id.dialog_entries)
    for (i in 0 until entries.childCount) assertWhole(screen, entries.getChildAt(i) as TextView)
    dialog.dismiss()
}
