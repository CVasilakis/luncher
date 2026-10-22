package com.luncher.launcher.home

import android.widget.LinearLayout
import android.widget.TextView
import org.robolectric.shadows.ShadowDialog

/** The tile menu that shows now, or null. */
internal fun shownMenu(): TileMenu? = (ShadowDialog.getLatestDialog() as? TileMenu)?.takeIf { it.isShowing }

/** The menu's entries, in order. */
internal fun TileMenu.entries(): List<TextView> {
    val list = findViewById<LinearLayout>(R.id.home_tile_menu)
    return (0 until list.childCount).map { list.getChildAt(it) as TextView }
}

/** The menu's entries' labels, in order. */
internal fun TileMenu.labels(): List<String> = entries().map { it.text.toString() }

/** Starts moving this tile's app, as a long press of OK on it and then Move in its menu would. */
internal fun AppTileView.startMoving() {
    requestFocus()
    performLongClick()
    val menu = checkNotNull(shownMenu()) { "no menu" }
    menu.entries().single { it.text == context.getString(R.string.home_menu_move) }.performClick()
}
