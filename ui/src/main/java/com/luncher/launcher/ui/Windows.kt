package com.luncher.launcher.ui

import android.graphics.drawable.ColorDrawable
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

/** A screen's own window (an editor, Add from folder): the theme's screen color behind it, and everything in it styled. */
fun Window.styleScreen(roles: ColorRoles) {
    setBackgroundDrawable(ColorDrawable(roles.screen))
    decorView.styleAll(roles)
}
