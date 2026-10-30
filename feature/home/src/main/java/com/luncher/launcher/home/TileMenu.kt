package com.luncher.launcher.home

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.appearance.ColorRoles
import com.luncher.domain.appearance.Corners
import com.luncher.domain.apps.AppAction
import com.luncher.domain.apps.AppMenuEntry
import com.luncher.domain.apps.HomeApp
import com.luncher.domain.layout.MenuSide
import com.luncher.domain.layout.PixelBounds
import com.luncher.domain.layout.tileMenuHeight
import com.luncher.domain.layout.tileMenuPlacement
import com.luncher.launcher.ui.Handover
import com.luncher.launcher.ui.ListEntry
import com.luncher.launcher.ui.LookAheadScrollView
import com.luncher.launcher.ui.ScrollFrame
import com.luncher.launcher.ui.WindowEntrance
import com.luncher.launcher.ui.stylePanel
import com.luncher.launcher.ui.R as UiR

/**
 * The menu of one app's tile, which a long press of OK on it opens: a small floating panel above
 * the tile, below it where there's no room above, or beside it where there's room for it neither
 * above nor below ([tileMenuPlacement]), listing what the user can do to the app (the domain's
 * [AppAction]s), each with its icon, the first one focused. No title: the tile under it, focused
 * and zoomed, says which app it's for. One the device can't do is dimmed, and the focus passes it by. OK on one closes the
 * menu and does it; Back closes it, as any dialog. In the theme's colors, its corners as round as
 * the tiles' up to a panel's ([style], [menuCorner]).
 *
 * A window of its own, so Android keeps the keys and the focus in it while it shows, and the tile
 * under it keeps its focus, zoomed as it is. Made once, the first time it's needed, and shown again
 * for each tile, with that tile's actions. It comes out of the tile, from the side it's on, and
 * fades as it goes: animations of its own, quicker than Android's dialog fade ([placeAt]).
 */
internal class TileMenu(private val activity: Activity) : Dialog(activity, R.style.Theme_Luncher_Home_Menu) {

    private val entries: LinearLayout
    private val scroll: LookAheadScrollView

    /** The menu's own layout, the window's size: the frame of the list of entries, with a margin around them inside it. */
    private val panel: ScrollFrame
    private var roles: ColorRoles? = null
    private var corners: Corners? = null

    /** The menu's corners and its entries', in px, as [style] last set them. */
    private var corner = 0f
    private var entryCorner = 0f

    /** The app whose menu shows; null while it doesn't. */
    var app: HomeApp? = null
        private set

    init {
        setContentView(R.layout.home_tile_menu)
        entries = findViewById(R.id.home_tile_menu)
        scroll = findViewById(R.id.home_tile_menu_scroll)
        panel = findViewById(R.id.home_tile_menu_frame)
        setOnDismissListener { app = null }
    }

    /**
     * The menu's entrance, played in its window, where Android plays no app dialog's window
     * entrance ([WindowEntrance.NEEDED]). Null where the window's animation plays it.
     */
    private val entrance = if (WindowEntrance.NEEDED) WindowEntrance(checkNotNull(window).decorView, from = GROWN_FROM) else null

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
        for (i in 0 until entries.childCount) (entries.getChildAt(i) as ListEntry).corner = entryCorner
        panel.corner = corner
        checkNotNull(window).stylePanel(roles, corner)
    }

    /**
     * Shows the menu of [tile], with its app's [entries], in their order; OK on an available one
     * gives its action to [act]. The first available one has the focus.
     */
    fun show(tile: AppTileView, entries: List<AppMenuEntry>, act: (AppAction) -> Unit) {
        this.entries.removeAllViews()
        for (entry in entries) this.entries.addView(entryView(entry, act))
        roles?.let { checkNotNull(window).stylePanel(it, corner) }   // the new entries too
        scroll.scrollTo(0, 0)   // where the last menu was scrolled to
        // No title shows; the app's name for screen readers, as the menu appears. Not the window's
        // title, which would rename it: Android names a dialog's window after its activity.
        if (Build.VERSION.SDK_INT >= 28) panel.accessibilityPaneTitle = tile.app.name
        placeAt(tile)
        app = tile.app
        show()
        for (i in 0 until this.entries.childCount) if (this.entries.getChildAt(i).requestFocus()) break
    }

    /** The view of [entry]: its icon and label; unavailable, dimmed, out of the focus's way, and OK does nothing. */
    private fun entryView(entry: AppMenuEntry, act: (AppAction) -> Unit): ListEntry {
        val view = layoutInflater.inflate(R.layout.home_tile_menu_entry, entries, false) as ListEntry
        if (roles != null) view.corner = entryCorner
        view.findViewById<TextView>(R.id.home_tile_menu_label).setText(label(entry.action))
        view.findViewById<ImageView>(R.id.home_tile_menu_icon).setImageResource(icon(entry.action))
        if (entry.available) {
            view.setOnClickListener { Handover.from(this) { act(entry.action) } }
        } else {
            view.alpha = DIMMED
            view.isFocusable = false
            view.isFocusableInTouchMode = false
        }
        return view
    }

    /**
     * Puts the window above, below or beside [tile], within the TV's overscan margin and below the
     * top bar, so the clock, the date and the settings entry show; as tall as its entries, or, where
     * they fit nowhere whole beside the tile, as tall as the most room there is, the entries
     * scrolling in it ([tileMenuHeight]). At the screen's left and top, in any language: the home
     * screen doesn't mirror, so neither does where the tile is.
     *
     * With the window's animation of the side it's on: it grows out of the middle of its edge that
     * faces the tile (`anim/home_menu_enter_*`), so the theme's alone isn't enough; or, where
     * Android plays no dialog's entrance, the window's exit only, and the [entrance] in it.
     */
    @SuppressLint("RtlHardcoded")
    private fun placeAt(tile: AppTileView) {
        val window = checkNotNull(window)
        val screen = activity.window.decorView
        val resources = activity.resources
        val marginX = resources.getDimensionPixelSize(UiR.dimen.screen_padding_horizontal)
        val marginY = resources.getDimensionPixelSize(R.dimen.home_padding_vertical)
        val topBar = activity.findViewById<View>(R.id.home_top_bar)
        topBar.getLocationOnScreen(location)
        val room = PixelBounds(marginX, maxOf(marginY, location[1] + topBar.height), screen.width - marginX, screen.height - marginY)
        val bounds = tile.focusedBounds()
        val gap = resources.getDimensionPixelSize(R.dimen.home_menu_gap)
        // The panel, not the window's decor, which adds nothing around it: under Robolectric the
        // decor of a dialog shown before measures it shorter, by where its window was last.
        measurePanel(room.right - room.left, room.bottom - room.top)
        // Two rows, squeezed by a fifth at most, as the list fits whole rows: the focused one and the next.
        val least = 2 * resources.getDimensionPixelSize(R.dimen.home_menu_entry_height) * 4 / 5 + 2 * resources.getDimensionPixelSize(R.dimen.home_menu_padding)
        val height = tileMenuHeight(bounds, panel.measuredWidth, panel.measuredHeight, room, gap, least)
        if (height < panel.measuredHeight) measurePanel(room.right - room.left, height)   // whole rows that fit, scrolling
        val placement = tileMenuPlacement(bounds, panel.measuredWidth, panel.measuredHeight, room, gap)
        // Out of the middle of its edge that faces the tile.
        entrance?.prepare(
            pivotX = when (placement.side) {
                MenuSide.ABOVE, MenuSide.BELOW -> panel.measuredWidth / 2f
                MenuSide.LEFT -> panel.measuredWidth.toFloat()
                MenuSide.RIGHT -> 0f
            },
            pivotY = when (placement.side) {
                MenuSide.ABOVE -> panel.measuredHeight.toFloat()
                MenuSide.BELOW -> 0f
                MenuSide.LEFT, MenuSide.RIGHT -> panel.measuredHeight / 2f
            },
        )
        window.attributes = window.attributes.apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = placement.left
            y = placement.top
            // As measured, so the window's own layout gives the list the same room.
            width = panel.measuredWidth
            this.height = panel.measuredHeight
            windowAnimations = if (entrance != null) {
                R.style.Animation_Luncher_Home_Menu_ExitOnly
            } else {
                when (placement.side) {
                    MenuSide.ABOVE -> R.style.Animation_Luncher_Home_Menu_Above
                    MenuSide.BELOW -> R.style.Animation_Luncher_Home_Menu_Below
                    MenuSide.LEFT -> R.style.Animation_Luncher_Home_Menu_Left
                    MenuSide.RIGHT -> R.style.Animation_Luncher_Home_Menu_Right
                }
            }
        }
    }

    private fun measurePanel(width: Int, height: Int) {
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST),
        )
    }

    private fun label(action: AppAction): Int = when (action) {
        AppAction.Move -> R.string.home_menu_move
        AppAction.Hide -> R.string.home_menu_hide
        AppAction.Rename -> R.string.home_menu_rename
        AppAction.TileBanner -> R.string.home_menu_tile_banner
        AppAction.Info -> R.string.home_menu_info
    }

    private fun icon(action: AppAction): Int = when (action) {
        AppAction.Move -> UiR.drawable.icon_move
        AppAction.Hide -> UiR.drawable.icon_eye_off
        AppAction.Rename -> UiR.drawable.icon_pencil
        AppAction.TileBanner -> UiR.drawable.icon_image
        AppAction.Info -> UiR.drawable.icon_info
    }

    private val location = IntArray(2)

    private companion object {
        /** An entry the device can't do: as the settings' dimmed entries (Delete all with no pictures). */
        const val DIMMED = 0.4f

        /** The size the menu grows from where it plays its entrance in its window, as `anim/home_menu_enter_*`'s. */
        const val GROWN_FROM = 0.92f
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
