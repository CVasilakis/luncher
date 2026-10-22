package com.luncher.launcher.ui

import android.graphics.drawable.ColorDrawable
import android.view.Window
import com.luncher.domain.appearance.ColorRoles

/**
 * A floating panel's window (a settings panel, a dialog, a menu): the theme's panel color, with a
 * panel's rounded corners, which its shadow follows; and everything in it styled ([styleAll]).
 */
fun Window.stylePanel(roles: ColorRoles) {
    setBackgroundDrawable(roundRect(roles.surface, context.resources.getDimension(R.dimen.corner_panel)))
    decorView.styleAll(roles)
}

/** A screen's own window (an editor, Add from folder): the theme's screen color behind it, and everything in it styled. */
fun Window.styleScreen(roles: ColorRoles) {
    setBackgroundDrawable(ColorDrawable(roles.screen))
    decorView.styleAll(roles)
}
