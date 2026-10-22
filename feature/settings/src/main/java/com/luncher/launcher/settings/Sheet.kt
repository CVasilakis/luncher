package com.luncher.launcher.settings

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.Window
import com.luncher.domain.appearance.TopBarAppearance
import com.luncher.domain.layout.TopBarItem
import com.luncher.domain.layout.topBarLayout

/**
 * Puts a panel's [window] as a side sheet (the settings panel, Hide apps in its place) at the side
 * of the screen where the home screen's settings entry is, as [topBar] places it: opposite the
 * clock, at the right without one. So the panel opens beside the gear that opened it.
 * `settings_sheet_width` wide at most, as tall as the screen less its margin above and below, the
 * margin from its edge too. The side is the screen's, the same in any language: the home screen
 * doesn't mirror (app/README.md#manifest-why-each-part-is-there), though the panel's content does.
 */
@SuppressLint("RtlHardcoded")
internal fun placeAsSheet(window: Window, topBar: TopBarAppearance) {
    val resources = window.context.resources
    val margin = resources.getDimensionPixelSize(R.dimen.settings_sheet_margin)
    val metrics = resources.displayMetrics
    val width = minOf(resources.getDimensionPixelSize(R.dimen.settings_sheet_width), metrics.widthPixels - 2 * margin)
    window.setLayout(width, metrics.heightPixels - 2 * margin)
    val side = if (TopBarItem.SETTINGS in topBarLayout(topBar).left) Gravity.LEFT else Gravity.RIGHT
    window.attributes = window.attributes.apply {
        gravity = side or Gravity.CENTER_VERTICAL
        x = margin
    }
}
