package com.luncher.launcher.ui

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.DialogInterface
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewTreeObserver

/**
 * One window of a screen handing over to the next (a menu's entry opening a question, the working
 * window its message, a tile's menu the rename dialog). Each dims the screen with its own window,
 * so closing the first before the next shows drops the dim for a moment, and closing it as the next
 * shows leaves nothing on screen while the next is made. Here the next shows first, and the first
 * closes at once, with no animation, once the next has drawn ([PanelDialog.takeOver]); the next
 * fades in over it. What the first would do after closing is [from]'s `open`. A window that starts
 * a screen stays until that screen has come over it ([toScreen]).
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

    /**
     * Inside [from]'s `open`: [start] starts a screen over the one the window is on (Luncher's, or
     * another app's), and says whether it did. The window stays meanwhile, taking no key but Back:
     * closed at once, it would leave the screen under it bare for the time the next one takes to
     * start. Once its screen is stopped, covered whole, it closes with no animation, unseen; a
     * screen over only part of it (Android TV's side panels) leaves it there, as it was, for when the
     * user comes back. If [start] says it didn't, [from] closes it as usual; if its screen isn't paused
     * within [SCREEN_GIVE_UP] ms, nothing came, and it closes with its animation. Main thread only.
     */
    fun toScreen(start: () -> Boolean) {
        val old = leaving
        if (!start() || old == null || leaving !== old) return
        val activity = old.context.activity() ?: return
        leaving = null
        if (old is PanelDialog) old.leaving = false
        ScreenWait(old, activity).begin()
    }

    /** [old] waiting for the screen its [activity] started to come over it. */
    private class ScreenWait(private val old: Dialog, private val activity: Activity) :
        Application.ActivityLifecycleCallbacks, ViewTreeObserver.OnWindowAttachListener, Runnable {

        private val decor = checkNotNull(old.window).decorView
        private var waiting = false
        private var paused = false

        fun begin() {
            waiting = true
            holdKeys(old, NO_KEY_BUT_BACK)
            activity.application.registerActivityLifecycleCallbacks(this)
            decor.viewTreeObserver.addOnWindowAttachListener(this)
            decor.postDelayed(this, SCREEN_GIVE_UP)
        }

        // The next screen is coming.
        override fun onActivityPaused(activity: Activity) {
            if (activity !== this.activity) return
            paused = true
            decor.removeCallbacks(this)
        }

        // Covered whole: it goes unseen, and isn't there when the user comes back.
        override fun onActivityStopped(activity: Activity) {
            if (activity !== this.activity) return
            val window = checkNotNull(old.window)
            val animations = window.attributes.windowAnimations
            window.setWindowAnimations(0)
            end(close = true)
            window.setWindowAnimations(animations)
        }

        // Back from a screen over part of it: it's there as the user left it.
        override fun onActivityResumed(activity: Activity) {
            if (activity === this.activity && paused) end(close = false)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}

        override fun onWindowAttached() {}

        // Closed meanwhile: Back, Home, the apps changing.
        override fun onWindowDetached() = end(close = false)

        // Nothing came.
        override fun run() = end(close = true)

        private fun end(close: Boolean) {
            if (!waiting) return
            waiting = false
            decor.removeCallbacks(this)
            activity.application.unregisterActivityLifecycleCallbacks(this)
            // Not inside the dispatch of the listeners.
            decor.post { decor.viewTreeObserver.removeOnWindowAttachListener(this) }
            holdKeys(old, null)
            if (close) old.dismiss()
        }
    }

    /** How long a window waits at most for the screen it started to begin coming, in ms. */
    private const val SCREEN_GIVE_UP = 1000L

    internal val NO_KEY = DialogInterface.OnKeyListener { _, _, _ -> true }
    private val NO_KEY_BUT_BACK = DialogInterface.OnKeyListener { _, keyCode, _ -> keyCode != KeyEvent.KEYCODE_BACK }

    /**
     * [dialog] takes only the keys [held] lets through, until called again with null, which gives
     * it its own back: a [PanelDialog]'s screen's listener; another dialog has none.
     */
    internal fun holdKeys(dialog: Dialog, held: DialogInterface.OnKeyListener?) {
        if (dialog is PanelDialog) dialog.holdKeys(held) else dialog.setOnKeyListener(held)
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
