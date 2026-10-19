package com.luncher.launcher.ui

import android.app.Dialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView

/**
 * A floating panel over the dimmed screen, with a spinning wheel and a message, for work the user
 * waits for (the wallpaper drawn, a picture added): it takes every key, so nothing else happens
 * meanwhile. Work that can be stopped shows Cancel too, focused, which Back does as well; other
 * work ignores Back. [show] shows it at once; [close] closes it no sooner than [SHORTEST] ms after
 * it showed ([ShortestShowing]), so it never just flashes; Cancel closes it at once, the user's own
 * choice. Main thread only.
 */
class WorkingDialog(context: Context) {

    private val dialog = Dialog(context, R.style.Theme_Luncher_Working).apply {
        setContentView(R.layout.working_dialog)
        setCancelable(false)
    }
    private val message: TextView = dialog.findViewById(R.id.working_message)
    private val cancel: TextView = dialog.findViewById(R.id.working_cancel)
    private var onCancel: (() -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())
    private var afterClosing: () -> Unit = {}
    private val closing = Runnable {
        dialog.dismiss()
        afterClosing().also { afterClosing = {} }
    }
    private val showing = ShortestShowing()

    val isShowing: Boolean get() = dialog.isShowing

    /** The message it shows. */
    val text: CharSequence get() = message.text

    init {
        cancel.setOnClickListener { cancel() }
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) cancel()
            true
        }
    }

    /**
     * Shows it with [text], or changes its message if it shows; a close on its way is called off,
     * and what it would have run. With [cancelLabel], it shows that entry too, focused, and Cancel
     * or Back closes it at once, then runs [onCancel]; without, Back does nothing.
     */
    fun show(text: CharSequence, cancelLabel: CharSequence? = null, onCancel: (() -> Unit)? = null) {
        main.removeCallbacks(closing)
        afterClosing = {}
        message.text = text
        this.onCancel = if (cancelLabel != null) onCancel ?: {} else null
        cancel.text = cancelLabel
        cancel.visibility = if (cancelLabel != null) View.VISIBLE else View.GONE
        if (cancelLabel != null) cancel.requestFocus()
        if (!dialog.isShowing) {
            dialog.show()
            showing.stop()
            showing.start()
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
        main.postAtTime(closing, showing.endsAt)
    }

    /** Cancel, or Back, with Cancel shown: closes it at once, then runs what [show] was given. */
    private fun cancel() {
        val action = onCancel ?: return
        onCancel = null
        dismiss()
        action()
    }

    /** Closes it at once, as the screen under it goes; what [close] would have run afterwards doesn't run. */
    fun dismiss() {
        main.removeCallbacks(closing)
        afterClosing = {}
        dialog.dismiss()
    }

    companion object {
        /** How long it shows at least, in ms. */
        const val SHORTEST = ShortestShowing.SHORTEST
    }
}
