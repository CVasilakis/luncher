package com.luncher.launcher.settings

import android.view.Gravity
import android.view.Window

/**
 * Puts a panel's [window] at the screen's end as a side sheet (the settings panel, Hide apps in
 * its place): `settings_sheet_width` wide at most, as tall as the screen less its margin above
 * and below, the margin from the end edge too. The end is the right, or the left in a
 * right-to-left language, as the panels mirror (app/README.md#manifest-why-each-part-is-there).
 */
internal fun placeAsSheet(window: Window) {
    val resources = window.context.resources
    val margin = resources.getDimensionPixelSize(R.dimen.settings_sheet_margin)
    val metrics = resources.displayMetrics
    val width = minOf(resources.getDimensionPixelSize(R.dimen.settings_sheet_width), metrics.widthPixels - 2 * margin)
    window.setLayout(width, metrics.heightPixels - 2 * margin)
    // Resolved here: Android places a window by absolute gravity, END as RIGHT in any language.
    val end = Gravity.getAbsoluteGravity(Gravity.END, resources.configuration.layoutDirection)
    window.attributes = window.attributes.apply {
        gravity = end or Gravity.CENTER_VERTICAL
        x = margin
    }
}
