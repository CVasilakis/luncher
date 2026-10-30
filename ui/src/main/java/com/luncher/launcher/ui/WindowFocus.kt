package com.luncher.launcher.ui

import android.app.Dialog
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import java.util.WeakHashMap

/**
 * Gives a dialog's window the window focus Android left it without (Android 9, [NEEDED]).
 *
 * There, a window that gets a key in the few ms after Android adds it (OK released just as a
 * tile's menu opens, a key that came with the one opening a dialog) may never get its window focus
 * on the app's side: Android's window manager has it as the focused window and sends it every key,
 * and its view root drops them all ("no window focus"), Back too, so nothing but Home leaves it.
 * [checkAfterShow], called once the dialog shows, looks [CHECK_AFTER] later: a window still without
 * the focus is made unfocusable and, at the main thread's next turn, focusable again, so that
 * Android looks for the focused window again and tells this one (the same turn, or its attributes
 * set again, don't). It looks again after each try, [TRIES] at most, as a key can come in that change
 * too. A window that has the focus is left alone, and one another window rightly has the focus over
 * (another app's screen) stays as it is: Android gives it the focus only if it's the one in front.
 */
object WindowFocus {

    /**
     * Where the repair is needed: seen on Android 9 (tv_api28); not on 7.0, 11 or 16 (tv_api24,
     * tv_api30, tv_api36); 8.0, 8.1 and 10 not tried, and Android's focus handling there is 9's.
     */
    val NEEDED: Boolean get() = Build.VERSION.SDK_INT in 26..29

    /** How long after the dialog shows its window is looked at, in ms: Android gives it the focus as it's added, within a frame or two. */
    const val CHECK_AFTER = 400L

    /** How long after a try the window is looked at again, in ms. */
    const val AGAIN_AFTER = 300L

    /** How many times the focus is asked for at most, the window staying without it. */
    const val TRIES = 3

    /** The check waiting for each window (its decor), on the main thread only. */
    private val pending = WeakHashMap<View, Runnable>()

    /** The checks' handler: one for all, as a window shown again has another view root, whose handler couldn't take an earlier check back. */
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Looks after [dialog]'s window focus, from its showing on, where it's [NEEDED]. */
    fun checkAfterShow(dialog: Dialog) {
        if (!NEEDED) return
        val decor = dialog.window?.decorView ?: return
        val check = Check(dialog)
        pending.put(decor, check)?.let { main.removeCallbacks(it) }   // a check of an earlier showing
        main.postDelayed(check, CHECK_AFTER)
    }

    private class Check(private val dialog: Dialog) : Runnable {
        private var tries = 0

        override fun run() {
            val window = dialog.window ?: return
            val decor = window.decorView
            if (!dialog.isShowing || decor.hasWindowFocus() || tries == TRIES) {
                pending.remove(decor)
                return
            }
            val flags = window.attributes.flags
            if (flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0) {   // not this window's to have
                pending.remove(decor)
                return
            }
            tries++
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            main.post { window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) }
            main.postDelayed(this, AGAIN_AFTER)
        }
    }
}
