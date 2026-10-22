package com.luncher.launcher.home

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.apps.AppAction
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.layout.PixelBounds
import com.luncher.domain.layout.tileMenuPlacement

/**
 * The menu of one app's tile, which a long press of OK on it opens: a small floating panel above
 * the tile, or below it where there's no room above ([tileMenuPlacement]), listing what the user
 * can do to the app (the domain's [AppAction]s), the first one focused. OK on one closes the menu
 * and does it; Back closes it, as any dialog.
 *
 * A window of its own, so Android keeps the keys and the focus in it while it shows, and the tile
 * under it keeps its focus, zoomed as it is. Made once, the first time it's needed, and shown again
 * for each tile, with that tile's actions.
 */
internal class TileMenu(private val activity: Activity) : Dialog(activity, R.style.Theme_Luncher_Home_Menu) {

    private val entries: LinearLayout

    /** The app whose menu shows; null while it doesn't. */
    var app: InstalledApp? = null
        private set

    init {
        setContentView(R.layout.home_tile_menu)
        entries = findViewById(R.id.home_tile_menu)
        setOnDismissListener { app = null }
    }

    /** Shows the menu of [tile], with its app's [actions], in their order; OK on one gives it to [act]. */
    fun show(tile: AppTileView, actions: List<AppAction>, act: (AppAction) -> Unit) {
        entries.removeAllViews()
        for (action in actions) {
            val entry = layoutInflater.inflate(R.layout.home_tile_menu_entry, entries, false) as TextView
            entry.setText(label(action))
            entry.setOnClickListener {
                dismiss()
                act(action)
            }
            entries.addView(entry)
        }
        placeAt(tile)
        app = tile.app
        show()
        entries.getChildAt(0)?.requestFocus()
    }

    /**
     * Puts the window above or below [tile], within the TV's overscan margin. At the screen's
     * left and top, in any language: the home screen doesn't mirror, so neither does where the
     * tile is.
     */
    @SuppressLint("RtlHardcoded")
    private fun placeAt(tile: AppTileView) {
        val window = checkNotNull(window)
        val screen = activity.window.decorView
        val resources = activity.resources
        val marginX = resources.getDimensionPixelSize(R.dimen.home_padding_horizontal)
        val marginY = resources.getDimensionPixelSize(R.dimen.home_padding_vertical)
        val room = PixelBounds(marginX, marginY, screen.width - marginX, screen.height - marginY)
        val menu = window.decorView
        menu.measure(
            View.MeasureSpec.makeMeasureSpec(room.right - room.left, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(room.bottom - room.top, View.MeasureSpec.AT_MOST),
        )
        val gap = resources.getDimensionPixelSize(R.dimen.home_menu_gap)
        val placement = tileMenuPlacement(tile.focusedBounds(), menu.measuredWidth, menu.measuredHeight, room, gap)
        window.attributes = window.attributes.apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = placement.left
            y = placement.top
        }
    }

    private fun label(action: AppAction): Int = when (action) {
        AppAction.Move -> R.string.home_menu_move
        AppAction.Hide -> R.string.home_menu_hide
    }
}
