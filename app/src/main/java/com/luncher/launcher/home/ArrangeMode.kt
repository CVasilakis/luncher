package com.luncher.launcher.home

import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import com.luncher.domain.apps.AppArrangements
import com.luncher.domain.apps.ArrangedApps
import com.luncher.domain.arrange.ArrangeSession
import com.luncher.domain.layout.Direction
import com.luncher.launcher.R

/**
 * The home screen while the user arranges apps. They hold an app and move it with the arrows, OK
 * puts it down and picks up the focused one, Back ends the mode; moving an app onto the shelf of
 * hidden apps below the others hides it. The top bar shows the mode's title and which keys do what.
 *
 * Where a held app goes is the domain's decision ([ArrangeSession]); this turns keys into its
 * moves and shows the result. [HomeActivity] hands it every key first while it's [active], and
 * ends it when the screen stops. Each change is stored when the app is put down.
 */
class ArrangeMode(
    private val tiles: AppTilesView,
    /** What the top bar shows normally (the clock, the settings entry), hidden meanwhile. */
    private val normalBar: List<View>,
    private val title: View,
    private val hint: TextView,
    private val banners: BannerImages,
    private val arrangements: AppArrangements,
    /** Called when the mode ends, with the apps as the user left them. */
    private val onEnd: (ArrangedApps) -> Unit,
) {

    private var session: ArrangeSession? = null

    /** Whether an OK press started during the mode; only its release counts. */
    private var okDown = false

    val active: Boolean get() = session != null

    /** Starts the mode, holding [tile]'s app; [arranged] is what the tiles show. */
    fun start(arranged: ArrangedApps, tile: AppTileView) {
        if (active) return
        val session = ArrangeSession(arranged, tiles::movesFor).also { session = it }
        for (app in arranged.hidden) {
            tiles.addView(AppTileView(tiles.context, app, banners).apply { hidden = true })
        }
        tiles.shownCount = arranged.shown.size
        for (view in normalBar) view.visibility = View.GONE
        title.visibility = View.VISIBLE
        hint.visibility = View.VISIBLE
        okDown = false
        session.pickUp(tile.app)
        tile.held = true
        showHint()
    }

    /** Handles [event] if it's one of the mode's keys; false lets Android handle it (e.g. arrows moving the focus). */
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val session = session ?: return false
        val direction = direction(event.keyCode)
        return when {
            direction != null -> {
                if (session.held == null) return false          // Android moves the focus
                if (event.action == KeyEvent.ACTION_DOWN) move(session, direction)
                true
            }
            event.keyCode in CONFIRM_KEYS -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0) okDown = true
                    return true
                }
                // The end of the long press that started the mode: the tile it began on finishes it.
                if (!okDown) return false
                okDown = false
                if (session.held != null) drop(session) else pickUpFocused(session)
                true
            }
            event.keyCode == KeyEvent.KEYCODE_MENU -> true      // no settings panel meanwhile
            else -> false
        }
    }

    /** Back: puts a held app down and ends the mode. */
    fun back() = end()

    /** Ends the mode, if it's on: puts a held app down, takes the shelf away and brings the top bar back. */
    fun end() {
        val session = session ?: return
        if (session.held != null) drop(session)
        title.visibility = View.GONE
        hint.visibility = View.GONE
        for (view in normalBar) view.visibility = View.VISIBLE
        val shown = session.shown.size
        // Focus on a hidden app goes to the last shown one, next to where the shelf was, or with
        // every app hidden to the top bar (the settings entry).
        if (tiles.focusedChild.let { it != null && tiles.indexOfChild(it) >= shown }) {
            val next = if (shown > 0) tiles.getChildAt(shown - 1) else normalBar.firstOrNull { it.isFocusable }
            next?.requestFocus()
        }
        tiles.removeViews(shown, tiles.childCount - shown)
        tiles.shownCount = null
        this.session = null
        onEnd(session.arranged())
    }

    private fun move(session: ArrangeSession, direction: Direction) {
        val from = session.heldPlace
        if (!session.move(direction)) return
        val to = session.heldPlace
        tiles.moveTile(from, to)
        tiles.shownCount = session.shown.size
        (tiles.getChildAt(to) as AppTileView).hidden = to >= session.shown.size
    }

    private fun drop(session: ArrangeSession) {
        val tile = tiles.getChildAt(session.heldPlace) as AppTileView
        if (session.drop()) arrangements.save(session.arranged().arrangement)
        tile.held = false
        showHint()
    }

    private fun pickUpFocused(session: ArrangeSession) {
        val tile = tiles.focusedChild as? AppTileView ?: return
        session.pickUp(tile.app)
        tile.held = true
        showHint()
    }

    private fun showHint() {
        val holding = session?.held != null
        hint.setText(if (holding) R.string.home_arrange_hint_holding else R.string.home_arrange_hint_browsing)
    }

    private fun direction(keyCode: Int): Direction? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> Direction.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> Direction.RIGHT
        KeyEvent.KEYCODE_DPAD_UP -> Direction.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> Direction.DOWN
        else -> null
    }

    private companion object {
        /** The keys Android's views treat as OK. */
        val CONFIRM_KEYS = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
    }
}
