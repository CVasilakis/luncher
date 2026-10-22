package com.luncher.launcher.home

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.appearance.ColorRoles
import com.luncher.domain.appearance.Corners
import com.luncher.domain.apps.AppAction
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.layout.PixelBounds
import com.luncher.domain.layout.tileMenuPlacement
import com.luncher.launcher.ui.EntryText
import com.luncher.launcher.ui.stylePanel
import com.luncher.launcher.ui.R as UiR

/**
 * The menu of one app's tile, which a long press of OK on it opens: a small floating panel above
 * the tile, or below it where there's no room above ([tileMenuPlacement]), the app's name, then
 * what the user can do to the app (the domain's [AppAction]s), each with its icon, the first one
 * focused. OK on one closes the menu and does it; Back closes it, as any dialog. In the theme's
 * colors, its corners as round as the tiles' up to a panel's ([style], [menuCorner]).
 *
 * A window of its own, so Android keeps the keys and the focus in it while it shows, and the tile
 * under it keeps its focus, zoomed as it is. Made once, the first time it's needed, and shown again
 * for each tile, with that tile's actions.
 */
internal class TileMenu(private val activity: Activity) : Dialog(activity, R.style.Theme_Luncher_Home_Menu) {

    private val entries: LinearLayout
    private val title: TextView
    private var roles: ColorRoles? = null
    private var corners: Corners? = null

    /** The menu's corners and its entries', in px, as [style] last set them. */
    private var corner = 0f
    private var entryCorner = 0f

    /** The app whose menu shows; null while it doesn't. */
    var app: InstalledApp? = null
        private set

    init {
        setContentView(R.layout.home_tile_menu)
        entries = findViewById(R.id.home_tile_menu)
        title = findViewById(R.id.home_tile_menu_title)
        setOnDismissListener { app = null }
    }

    /**
     * In the theme's colors, [roles], its corners following the tiles' [corners] (see
     * [menuCorner]), its entries' concentric with them ([entryCorner]). Made anew only when either changes.
     */
    fun style(roles: ColorRoles, corners: Corners) {
        if (roles == this.roles && corners == this.corners) return
        this.roles = roles
        this.corners = corners
        val resources = context.resources
        corner = menuCorner(corners, resources.getDimension(UiR.dimen.corner_panel), resources.displayMetrics.density)
        val density = resources.displayMetrics.density
        entryCorner = entryCorner(corner, resources.getDimension(R.dimen.home_menu_padding), resources.getDimension(R.dimen.home_menu_entry_corner_least), density)
        for (i in 0 until entries.childCount) (entries.getChildAt(i) as EntryText).corner = entryCorner
        checkNotNull(window).stylePanel(roles, corner)
    }

    /** Shows the menu of [tile], with its app's [actions], in their order; OK on one gives it to [act]. */
    fun show(tile: AppTileView, actions: List<AppAction>, act: (AppAction) -> Unit) {
        entries.removeAllViews()
        title.text = tile.app.label
        for (action in actions) {
            val entry = layoutInflater.inflate(R.layout.home_tile_menu_entry, entries, false) as EntryText
            if (roles != null) entry.corner = entryCorner
            entry.setText(label(action))
            entry.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(action), 0, 0, 0)
            entry.setOnClickListener {
                dismiss()
                act(action)
            }
            entries.addView(entry)
        }
        roles?.let { checkNotNull(window).stylePanel(it, corner) }   // the new entries too
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

/**
 * A tile's menu's corner radius, in px, for tiles with [corners]: as round as the tiles', so the
 * menu looks of a piece with them, but no rounder than a panel's ([panel], `corner_panel`): a
 * small menu with larger corners would look like a pill, so pill tiles and larger radii get a
 * panel's corners. [pixelsPerDp] turns a radius in dp into px.
 */
internal fun menuCorner(corners: Corners, panel: Float, pixelsPerDp: Float): Float = when (corners) {
    is Corners.Radius -> minOf(corners.dp * pixelsPerDp, panel)
    Corners.Pill -> panel
}

/**
 * The corner radius, in px, of an entry in a tile's menu with corners of [menu] px: concentric
 * with them, [menu] less the [padding] between them, where that's round enough; else a third of
 * [menu], in whole dp ([pixelsPerDp]), and at least [least] (2 dp): where concentric ones would
 * be square or nearly (tiles of 4, 8 or 12 dp), the entry's corners still read as rounded, as
 * the menu's do, and in proportion to them. 2 dp for tiles of 4 and 8 dp, 4 dp for 12, from 16 dp
 * on concentric. A square menu's (tiles of 0 dp) are square.
 */
internal fun entryCorner(menu: Float, padding: Float, least: Float, pixelsPerDp: Float): Float {
    if (menu <= 0f) return 0f
    val third = kotlin.math.floor(menu / pixelsPerDp / 3) * pixelsPerDp
    return maxOf(menu - padding, third, least)
}
