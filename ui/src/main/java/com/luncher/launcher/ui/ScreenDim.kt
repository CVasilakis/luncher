package com.luncher.launcher.ui

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager

/**
 * The dim under a screen's dialogs: a window of the screen's own that covers it and draws nothing
 * else, there from the first [PanelDialog] that shows ([hold]) until the last one closes ([release]).
 *
 * Android's dim (`FLAG_DIM_BEHIND`) won't do for them. It's a layer of its own, outside the window
 * it belongs to: the screen's exit doesn't fade it, and once a screen has called `finish()` Android
 * no longer changes it, so a screen closed from its dialog (Save, Add, Discard) faded under a dim
 * that dropped in one frame at the end, or on Android 5.1 at the start. This window is the screen's,
 * so Android fades it with the screen. And there's one per screen, so a dialog handing over to
 * the next ([Handover]) leaves it as it is: each dialog with a dim of its own would make the two
 * darker while they overlap, and the dim would have to change hands between two windows.
 *
 * It takes no key and no focus, so the dialogs' own windows work as they would alone. It comes and
 * goes as they do (`Animation.Luncher.Panel`, its entrance played in the window where Android plays
 * no app dialog's, [WindowEntrance.NEEDED]). Main thread only.
 */
internal class ScreenDim private constructor(private val activity: Activity) : Application.ActivityLifecycleCallbacks {

    private val view = View(activity).apply { setBackgroundResource(R.color.dim) }
    private val entrance by lazy { WindowEntrance(view) }
    private val holders = HashSet<Dialog>()

    /** Whether the window shows. */
    var isShowing = false
        private set

    /** [dialog] is about to show: the screen is dimmed under it, the window added first so that it's below. */
    fun hold(dialog: Dialog) {
        holders.add(dialog)
        if (isShowing) return
        if (WindowEntrance.NEEDED) entrance.prepare()
        activity.windowManager.addView(view, attributes())
        isShowing = true
    }

    /** [dialog] has closed: the dim goes too if it was the last, with the same fade. */
    fun release(dialog: Dialog) {
        holders.remove(dialog)
        if (holders.isEmpty()) close()
    }

    private fun close() {
        if (!isShowing) return
        isShowing = false
        activity.windowManager.removeViewImmediate(view)
    }

    internal fun attributes() = WindowManager.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        windowAnimations = if (WindowEntrance.NEEDED) R.style.Animation_Luncher_Panel_ExitOnly else R.style.Animation_Luncher_Panel
        if (Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
    }

    // A dialog its screen left showing goes with the screen; this window mustn't outlive it either.
    override fun onActivityDestroyed(activity: Activity) {
        if (activity !== this.activity) return
        holders.clear()
        close()
        activity.application.unregisterActivityLifecycleCallbacks(this)
        activity.window.decorView.setTag(R.id.screen_dim, null)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    companion object {
        /** [activity]'s dim, kept on its window, so it lives as long as the screen and no longer. */
        fun of(activity: Activity): ScreenDim {
            val decor = activity.window.decorView
            return decor.getTag(R.id.screen_dim) as ScreenDim? ?: ScreenDim(activity).also {
                decor.setTag(R.id.screen_dim, it)
                activity.application.registerActivityLifecycleCallbacks(it)
            }
        }
    }
}
