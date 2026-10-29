package com.luncher.launcher.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils

/**
 * A floating window's entrance, played in the window, for where Android plays no app dialog's
 * window entrance ([NEEDED]): the same as `anim/panel_enter` (a tile's menu: `anim/home_menu_enter_*`),
 * a fade in, growing from [from] of its size (1: not growing), on the window's [decor], which draws
 * the panel's background too. Made once with the window; an opening only starts it again. An
 * animator, so it follows Android's "Animator duration scale", where the window's animations follow
 * the "Window animation scale".
 */
class WindowEntrance(private val decor: View, private val from: Float = 1f) : ViewTreeObserver.OnPreDrawListener {

    private val animator = ObjectAnimator.ofPropertyValuesHolder(
        decor,
        *listOfNotNull(
            PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
            if (from != 1f) PropertyValuesHolder.ofFloat(View.SCALE_X, from, 1f) else null,
            if (from != 1f) PropertyValuesHolder.ofFloat(View.SCALE_Y, from, 1f) else null,
        ).toTypedArray(),
    ).apply {
        duration = DURATION
        interpolator = AnimationUtils.loadInterpolator(decor.context, android.R.interpolator.decelerate_cubic)
        // Faded in a layer, which the window's animation has too, rather than each part on its own.
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) = decor.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            override fun onAnimationEnd(animation: Animator) = decor.setLayerType(View.LAYER_TYPE_NONE, null)
        })
    }

    /**
     * Readies the window, before it shows, to come in as it's first drawn, growing out of
     * [pivotX], [pivotY] (px in the window): started at once, it could be over before the first
     * frame of a slow TV.
     */
    fun prepare(pivotX: Float = 0f, pivotY: Float = 0f) {
        animator.cancel()
        decor.alpha = 0f
        if (from != 1f) {
            decor.pivotX = pivotX
            decor.pivotY = pivotY
            decor.scaleX = from
            decor.scaleY = from
        }
        // Once, though a window closed before it was drawn left it there.
        decor.viewTreeObserver.removeOnPreDrawListener(this)
        decor.viewTreeObserver.addOnPreDrawListener(this)
    }

    override fun onPreDraw(): Boolean {
        decor.viewTreeObserver.removeOnPreDrawListener(this)
        animator.start()
        return true
    }

    companion object {
        /**
         * Whether Android plays no app dialog's window entrance: Android 5.1 and 6.0 (seen on the
         * tv_api22 and tv_api23 emulators, which play its exit; tv_api24 plays both).
         */
        val NEEDED: Boolean get() = Build.VERSION.SDK_INT <= 23

        /** As `anim/panel_enter`'s. */
        private const val DURATION = 100L
    }
}
