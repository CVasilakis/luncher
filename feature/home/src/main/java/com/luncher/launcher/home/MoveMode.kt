package com.luncher.launcher.home

import android.view.KeyEvent
import android.view.View
import com.luncher.domain.apps.AppArrangements
import com.luncher.domain.apps.ArrangedApps
import com.luncher.domain.arrange.MoveSession
import com.luncher.domain.layout.Direction

/**
 * The home screen while the user moves one app, chosen in its [TileMenu]: they hold its tile and
 * move it among the shown apps with the arrows; OK or Back puts it down and ends the mode. The top
 * bar shows the mode's title and which keys do what. The hidden apps don't show meanwhile, so the
 * other tiles stay where they are.
 *
 * Where the held app goes is the domain's decision ([MoveSession]); this turns keys into its moves
 * and shows the result. [HomeActivity] hands it every key first while it's [active], and ends it
 * when the screen stops. The new order is stored when the mode ends, if the app moved.
 */
internal class MoveMode(
    private val tiles: AppTilesView,
    /** What the top bar shows normally (the clock, the settings entry), hidden meanwhile. */
    private val normalBar: List<View>,
    private val title: View,
    private val hint: View,
    private val arrangements: AppArrangements,
    /** Called when the mode ends, with the apps as the user left them. */
    private val onEnd: (ArrangedApps) -> Unit,
) {

    private var session: MoveSession? = null
    private var heldTile: AppTileView? = null

    /** Whether an OK press started during the mode; only its release counts. */
    private var okDown = false

    val active: Boolean get() = session != null

    /** Starts the mode, holding [tile]'s app, one of [arranged]'s shown apps, which the tiles show. */
    fun start(arranged: ArrangedApps, tile: AppTileView) {
        if (active) return
        session = MoveSession(arranged, tile.app, tiles.movesFor(arranged.shown.size))
        heldTile = tile
        for (view in normalBar) view.visibility = View.GONE
        title.visibility = View.VISIBLE
        hint.visibility = View.VISIBLE
        okDown = false
        tile.requestFocus()
        tile.held = true
    }

    /** Handles [event] if it's one of the mode's keys; false lets Android handle it. */
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val session = session ?: return false
        val direction = direction(event.keyCode)
        return when {
            direction != null -> {
                if (event.action == KeyEvent.ACTION_DOWN) move(session, direction)
                true
            }
            event.keyCode in CONFIRM_KEYS -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0) okDown = true
                } else if (okDown) {
                    end()
                }
                true
            }
            event.keyCode == KeyEvent.KEYCODE_MENU -> true      // no settings panel meanwhile
            else -> false
        }
    }

    /** Back: puts the app down where it is and ends the mode. */
    fun back() = end()

    /**
     * Ends the mode, if it's on: puts the app down where it is, stores the new order if it moved,
     * and brings the top bar back.
     */
    fun end() {
        val session = session ?: return
        val arranged = session.arranged()
        // Stored while still on: the home screen's listener then knows the tiles show it already.
        if (session.moved) arrangements.save(arranged.arrangement)
        this.session = null
        heldTile?.held = false
        heldTile = null
        title.visibility = View.GONE
        hint.visibility = View.GONE
        for (view in normalBar) view.visibility = View.VISIBLE
        onEnd(arranged)
    }

    private fun move(session: MoveSession, direction: Direction) {
        val from = session.heldIndex
        if (session.move(direction)) tiles.moveTile(from, session.heldIndex)
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
