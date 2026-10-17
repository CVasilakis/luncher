package com.luncher.launcher.ui

import android.app.Dialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.TextView

/**
 * A floating panel over the dimmed screen, with a spinning wheel and a message, for work the user
 * waits for (the wallpaper drawn): it takes every key, Back too, so nothing else happens
 * meanwhile. [show] shows it at once; [close] closes it no sooner than [SHORTEST] ms after it
 * showed, so it never just flashes. Main thread only.
 */
class WorkingDialog(context: Context) {

    private val dialog = Dialog(context, R.style.Theme_Luncher_Working).apply {
        setContentView(R.layout.working_dialog)
        setCancelable(false)
    }
    private val message: TextView = dialog.findViewById(R.id.working_message)
    private val main = Handler(Looper.getMainLooper())
    private var afterClosing: () -> Unit = {}
    private val closing = Runnable {
        dialog.dismiss()
        afterClosing().also { afterClosing = {} }
    }
    private var shownAt = 0L

    val isShowing: Boolean get() = dialog.isShowing

    /** The message it shows. */
    val text: CharSequence get() = message.text

    /** Shows it with [text], or changes its message if it shows; a close on its way is called off, and what it would have run. */
    fun show(text: CharSequence) {
        main.removeCallbacks(closing)
        afterClosing = {}
        message.text = text
        if (!dialog.isShowing) {
            dialog.show()
            shownAt = SystemClock.uptimeMillis()
        }
    }

    /**
     * Closes it once it has shown for [SHORTEST] ms, at once if it has, then runs [then]; if it
     * isn't showing, runs [then] at once.
     */
    fun close(then: () -> Unit = {}) {
        if (!dialog.isShowing) return then()
        main.removeCallbacks(closing)
        afterClosing = then
        main.postAtTime(closing, shownAt + SHORTEST)
    }

    /** Closes it at once, as the screen under it goes; what [close] would have run afterwards doesn't run. */
    fun dismiss() {
        main.removeCallbacks(closing)
        afterClosing = {}
        dialog.dismiss()
    }

    companion object {
        /** How long it shows at least, in ms. */
        const val SHORTEST = 500L
    }
}
