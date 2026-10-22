package com.luncher.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import com.luncher.domain.appearance.ColorRoles

/**
 * The frame of a list that scrolls (a `ListView` or a `ScrollView`, its one child): what scrolls
 * under its edges is cut along its rounded corners, its outline, by the GPU (no layer, no
 * bitmap), so the list keeps its corners while it scrolls; and while there's more below, the last
 * half row fades into the frame's color: one gradient drawn over it, shown and hidden as the list
 * scrolls, never while its end shows. Its `fill`: the color it has, a list's container on a
 * screen of its own (`surface`) or a field's (`high`); or none, on a panel, whose color the fade
 * goes to.
 *
 * The list gives its focused entry room for its zoom inside itself, as padding with
 * `clipToPadding="false"`: a parent that clips cuts each child to the child's own bounds, so room
 * outside the list would be cut with it.
 */
open class ScrollFrame @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs), Themed, ViewTreeObserver.OnScrollChangedListener {

    private val fill: Int
    private val radius = resources.getDimension(R.dimen.corner_container)
    private val fadeHeight = resources.getDimensionPixelSize(R.dimen.scroll_fade)
    private val fade = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0, 0))
    private var fadeShown = false
    private var styled: ColorRoles? = null

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ScrollFrame)
        fill = a.getInt(R.styleable.ScrollFrame_fill, FILL_NONE)
        a.recycle()
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        clipToOutline = true
    }

    override fun style(roles: ColorRoles) {
        if (roles == styled) return
        styled = roles
        val color = if (fill == FILL_HIGH) roles.surfaceHigh else roles.surface
        background = if (fill == FILL_NONE) null else roundRect(color, radius)
        fade.colors = intArrayOf(withAlpha(color, 0f), color)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnScrollChangedListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnScrollChangedListener(this)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        fade.setBounds(0, height - fadeHeight, width, height)
        showFade()
    }

    // Any view of the window scrolled: this list, maybe.
    override fun onScrollChanged() = showFade()

    /** Whether the fade shows: while the list goes on below what shows of it. */
    private fun showFade() {
        val shown = getChildAt(0)?.canScrollVertically(1) == true
        if (shown == fadeShown) return
        fadeShown = shown
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (fadeShown) fade.draw(canvas)
    }

    private companion object {
        const val FILL_NONE = 0
        const val FILL_HIGH = 1
    }
}
