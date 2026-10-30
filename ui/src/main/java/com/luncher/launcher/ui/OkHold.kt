package com.luncher.launcher.ui

import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import com.luncher.domain.apps.menuHold

/**
 * OK held on a view that opens something on a long press (a tile's menu, a picture's menu): the
 * hold is Luncher's own, [menuHold] (350 ms), where it's shorter than Android's long press, which
 * the view's own would time from OK down ([ViewConfiguration.getLongPressTimeout], read at each
 * press, as the user may change it). A view calls [down] after its own `onKeyDown` pressed it, [up]
 * at its `onKeyUp`, and [cancel] when it leaves its window. The hold acts only while the view is
 * pressed, which Android ends as the view loses the focus, or its window does.
 *
 * When the hold runs out with OK still down, Android's own long press, still to come, is cancelled
 * (it would open the menu again) and the view is long-clicked. OK's release, if it comes to the view
 * after that, ends the press and opens nothing ([up]): from Android 12 on the menu takes the focus
 * only once it's drawn, and a release before that comes here as it is, which would click the view.
 *
 * Made once per view, with its runnable, so a press allocates nothing.
 */
class OkHold(private val view: View) {

    private var opened = false

    private val run = Runnable {
        if (!view.isPressed) return@Runnable
        view.cancelLongPress()
        opened = view.performLongClick()
    }

    /** OK went down on [view], which Android has pressed: the hold starts, where it's shorter than Android's. */
    fun down(keyCode: Int, event: KeyEvent) {
        if (!isConfirmKey(keyCode) || event.repeatCount != 0 || !view.isPressed) return
        opened = false
        view.removeCallbacks(run)
        val system = ViewConfiguration.getLongPressTimeout()
        val time = menuHold(system)
        if (time < system) view.postDelayed(run, time.toLong())
    }

    /** OK went up on [view]: true if the hold had opened what the view opens, and its release is to be consumed. */
    fun up(keyCode: Int): Boolean {
        if (!isConfirmKey(keyCode)) return false
        view.removeCallbacks(run)
        if (!opened) return false
        opened = false
        view.isPressed = false
        return true
    }

    /** The hold is dropped: the view left its window (it would stay pressed there). */
    fun cancel() {
        view.removeCallbacks(run)
    }

    private companion object {
        /**
         * The keys Android presses a view with, as its hidden `KeyEvent.isConfirmKey` says on some
         * version: OK and Enter on Android 6, Space and the keypad's Enter too on 16. Whether it
         * did on this one, the press says ([down]).
         */
        fun isConfirmKey(keyCode: Int) =
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_SPACE
    }
}
