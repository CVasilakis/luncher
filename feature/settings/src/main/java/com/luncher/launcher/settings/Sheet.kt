package com.luncher.launcher.settings

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import com.luncher.domain.appearance.TopBarAppearance
import com.luncher.domain.layout.TopBarItem
import com.luncher.domain.layout.topBarLayout

/**
 * A panel's place as a side sheet (the settings panel, Hide apps in its place): at the side of the
 * screen where the home screen's settings entry is, as [topBar] places it: opposite the clock, at
 * the right without one. So the panel opens beside the gear that opened it. `settings_sheet_width`
 * wide at most, as tall as the screen less its margin above and below, the margin from its edge too.
 * The side is the screen's, the same in any language: the home screen doesn't mirror
 * (app/README.md#manifest-why-each-part-is-there), though the panel's content does.
 */
private class Sheet(context: Context, topBar: TopBarAppearance) {
    val margin = context.resources.getDimensionPixelSize(R.dimen.settings_sheet_margin)
    private val metrics = context.resources.displayMetrics
    val width = minOf(context.resources.getDimensionPixelSize(R.dimen.settings_sheet_width), metrics.widthPixels - 2 * margin)
    val height = metrics.heightPixels - 2 * margin

    @SuppressLint("RtlHardcoded")
    val gravity = (if (TopBarItem.SETTINGS in topBarLayout(topBar).left) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL
}

/** Puts a panel's [window] as a side sheet (Hide apps, a floating window). */
internal fun placeAsSheet(window: Window, topBar: TopBarAppearance) {
    val sheet = Sheet(window.context, topBar)
    window.setLayout(sheet.width, sheet.height)
    window.attributes = window.attributes.apply {
        gravity = sheet.gravity
        x = sheet.margin
    }
}

/** Puts a [panel] that's a view in a window covering the screen as a side sheet (the settings panel). */
internal fun placeAsSheet(panel: View, topBar: TopBarAppearance) {
    val sheet = Sheet(panel.context, topBar)
    panel.layoutParams = FrameLayout.LayoutParams(sheet.width, sheet.height, sheet.gravity).apply {
        leftMargin = sheet.margin
        rightMargin = sheet.margin
    }
}
