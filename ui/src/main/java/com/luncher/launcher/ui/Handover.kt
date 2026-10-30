package com.luncher.launcher.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper

/**
 * One window of a screen handing over to the next (a menu's entry opening a question, the working
 * window its message, a tile's menu the rename dialog). Each dims the screen with its own window,
 * so closing the first before the next shows drops the dim for a moment, and closing it as the next
 * shows leaves nothing on screen while the next is made. Here the next shows first, and the first
 * closes at once, with no animation, once the next has drawn ([PanelDialog.takeOver]); the next
 * fades in over it. What the first would do after closing is [from]'s `open`.
 */
object Handover {

    private var leaving: Dialog? = null

    /**
     * Closes [old] as [open] shows the next window, a [PanelDialog]; if it shows none (a move, an
     * app started), [old] closes as it does alone, with its animation, once [open] returns, unless its
     * screen finishes meanwhile: then it goes with the screen. Main thread only.
     */
    fun from(old: Dialog, open: () -> Unit) {
        leaving = old
        // From now on, whoever shows the next window closing this one first (a screen replacing
        // its dialog) closes nothing: the next takes it over.
        if (old is PanelDialog) old.leaving = true
        try {
            open()
        } finally {
            if (leaving === old) {
                leaving = null
                if (old is PanelDialog) old.leaving = false
                // As its screen finishes, the screen's own exit takes it along with the screen's:
                // closed first, it would fade away alone, and the screen under show bright for
                // the moment Android takes to start the screen's exit.
                if (!(old.context.activity()?.isFinishing ?: false)) old.dismiss()
            }
        }
    }

    /** A [PanelDialog] is to show: the window it takes the place of, if one is being handed over. */
    internal fun claim(next: PanelDialog): Dialog? {
        val old = leaving ?: return null
        if (old === next) return null
        leaving = null
        return old
    }
}

private fun Context.activity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
