package com.luncher.launcher.ui

import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.Window
import com.luncher.domain.appearance.ColorRoles

/**
 * A floating panel's window (a settings panel, a dialog, a menu): the theme's panel color, with
 * rounded corners of [corner] px (a panel's, `corner_panel`, unless a tile's menu follows the
 * tiles'), which its shadow follows; and everything in it styled ([styleAll]).
 */
fun Window.stylePanel(roles: ColorRoles, corner: Float = context.resources.getDimension(R.dimen.corner_panel)) {
    setBackgroundDrawable(roundRect(roles.surface, corner))
    decorView.styleAll(roles)
}

/**
 * A floating panel that's a view in a window covering the screen (the settings panel, the window
 * after a share), the window drawing the dim: the theme's panel color with a panel's rounded
 * corners, which its shadow follows, and everything in it styled. A window of its own would leave
 * the dim to Android, which doesn't fade it with the window ([ScreenDim]).
 */
fun View.stylePanel(roles: ColorRoles) {
    background = roundRect(roles.surface, context.resources.getDimension(R.dimen.corner_panel))
    styleAll(roles)
}

/** No wider than the screen leaves beside a dialog's margins, on a small or low-density one. */
fun View.fitPanelToScreen() {
    val margin = context.resources.getDimensionPixelSize(R.dimen.dialog_margin)
    layoutParams.width = minOf(
        context.resources.getDimensionPixelSize(R.dimen.dialog_width),
        context.resources.displayMetrics.widthPixels - 2 * margin,
    )
}

/** A screen's own window (an editor, Add from folder): the theme's screen color behind it, and everything in it styled. */
fun Window.styleScreen(roles: ColorRoles) {
    setBackgroundDrawable(ColorDrawable(roles.screen))
    decorView.styleAll(roles)
}
