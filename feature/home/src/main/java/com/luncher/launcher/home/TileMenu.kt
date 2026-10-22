package com.luncher.launcher.home

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.appearance.ColorRoles
import com.luncher.domain.apps.AppAction
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.layout.PixelBounds
import com.luncher.domain.layout.tileMenuPlacement
import com.luncher.launcher.ui.stylePanel
import com.luncher.launcher.ui.R as UiR

/**
 * The menu of one app's tile, which a long press of OK on it opens: a small floating panel above
 * the tile, or below it where there's no room above ([tileMenuPlacement]), the app's name, then
 * what the user can do to the app (the domain's [AppAction]s), each with its icon, the first one
 * focused. OK on one closes the menu and does it; Back closes it, as any dialog. In the theme's
 * colors ([style]).
 *
 * A window of its own, so Android keeps the keys and the focus in it while it shows, and the tile
 * under it keeps its focus, zoomed as it is. Made once, the first time it's needed, and shown again
 * for each tile, with that tile's actions.
 */
internal class TileMenu(private val activity: Activity) : Dialog(activity, R.style.Theme_Luncher_Home_Menu) {

    private val entries: LinearLayout
    private val title: TextView
    private var roles: ColorRoles? = null

    /** The app whose menu shows; null while it doesn't. */
    var app: InstalledApp? = null
        private set

    init {
        setContentView(R.layout.home_tile_menu)
        entries = findViewById(R.id.home_tile_menu)
        title = findViewById(R.id.home_tile_menu_title)
        setOnDismissListener { app = null }
    }

    /** In the theme's colors: [roles]. Made anew only when they change. */
    fun style(roles: ColorRoles) {
        if (roles == this.roles) return
        this.roles = roles
        checkNotNull(window).stylePanel(roles)
    }

    /** Shows the menu of [tile], with its app's [actions], in their order; OK on one gives it to [act]. */
    fun show(tile: AppTileView, actions: List<AppAction>, act: (AppAction) -> Unit) {
        entries.removeAllViews()
        title.text = tile.app.label
        for (action in actions) {
            val entry = layoutInflater.inflate(R.layout.home_tile_menu_entry, entries, false) as TextView
            entry.setText(label(action))
            entry.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(action), 0, 0, 0)
            entry.setOnClickListener {
                dismiss()
                act(action)
            }
            entries.addView(entry)
        }
        roles?.let { checkNotNull(window).stylePanel(it) }   // the new entries too
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

    private fun icon(action: AppAction): Int = when (action) {
        AppAction.Move -> UiR.drawable.icon_move
        AppAction.Hide -> UiR.drawable.icon_eye_off
    }
}
