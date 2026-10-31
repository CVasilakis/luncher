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
import kotlin.math.roundToInt

/**
 * The frame of a list that scrolls (a [LookAheadListView] or a [LookAheadScrollView], its one
 * child): what scrolls under its edges is cut along its rounded corners, its outline, by the GPU
 * (no layer, no bitmap), so the list keeps its corners while it scrolls; and while there's more
 * below, or above, the list fades into the frame's color at that edge: one gradient drawn over
 * it, shown and hidden as the list scrolls, never while that end of it shows. The list scrolls
 * ahead of the focus, so the focused row is never under a fade. A list whose window ends on a
 * whole row ([FittedRows]) has the fade over the whole row at the edge and the margin beyond it,
 * so that row looks dimmed; the list may end a few pixels above the frame, and the fade with it.
 * In a window of two rows the fades follow the focus ([FittedRows.focusAlong]): none over the row
 * that has it, only the margin beyond it covered, so what's past it doesn't peek in.
 * Its `fill`: the color it has, a
 * list's container on a screen of its own (`surface`) or a field's (`high`); or none, on a panel,
 * whose color the fades go to.
 *
 * The list keeps a margin around its rows inside itself, as padding with `clipToPadding="false"`,
 * its rows' corners concentric with the frame's (`corner_entry_in_container`): a parent that clips
 * cuts each child to the child's own bounds, so a margin outside the list would be cut with it.
 */
open class ScrollFrame @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs), Themed, ViewTreeObserver.OnScrollChangedListener {

    private val fill: Int

    /**
     * Its corners' radius, in px, which what scrolls under its edges is cut along:
     * `corner_container`'s; set, from then on (a tile's menu, whose corners follow the tiles').
     */
    var corner: Float = resources.getDimension(R.dimen.corner_container)
        set(value) {
            if (value == field) return
            field = value
            invalidateOutline()
            styled?.let { styled = null; style(it) }
        }
    private val fadeHeight = resources.getDimensionPixelSize(R.dimen.scroll_fade)
    private val fadeBelow = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0, 0))
    private val fadeAbove = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0, 0))
    private val marginBelow = GradientDrawable()
    private val marginAbove = GradientDrawable()
    private var moreBelow = false
    private var moreAbove = false
    private var focusAlong = NO_TURNS
    private var styled: ColorRoles? = null

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ScrollFrame)
        fill = a.getInt(R.styleable.ScrollFrame_fill, FILL_NONE)
        a.recycle()
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, corner)
            }
        }
        clipToOutline = true
    }

    override fun style(roles: ColorRoles) {
        if (roles == styled) return
        styled = roles
        val color = if (fill == FILL_HIGH) roles.surfaceHigh else roles.surface
        background = if (fill == FILL_NONE) null else roundRect(color, corner)
        val colors = intArrayOf(withAlpha(color, 0f), color)
        fadeBelow.colors = colors
        fadeAbove.colors = colors
        marginBelow.setColor(color)
        marginAbove.setColor(color)
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
        val list = getChildAt(0)
        val length = (list as? FittedRows)?.fadeLength?.takeIf { it > 0 } ?: fadeHeight
        val listTop = list?.top ?: 0
        val listBottom = list?.bottom ?: height
        fadeBelow.setBounds(0, listBottom - length, width, listBottom)
        fadeAbove.setBounds(0, listTop, width, listTop + length)
        val margin = (list as? FittedRows)?.let { it.fadeLength - it.rowHeight }?.coerceAtLeast(0) ?: 0
        marginBelow.setBounds(0, listBottom - margin, width, listBottom)
        marginAbove.setBounds(0, listTop, width, listTop + margin)
        showFades()
    }

    // Any view of the window scrolled: this list, maybe.
    override fun onScrollChanged() = showFades()

    /**
     * Which fades show: where the list goes on beyond what shows of it, and in a window of two rows
     * away from the focus. Told by the list too, as the focus moves without a scroll.
     */
    internal fun showFades() {
        val list = getChildAt(0)
        val below = list?.canScrollVertically(1) == true
        val above = list?.canScrollVertically(-1) == true
        val along = (list as? FittedRows)?.focusAlong ?: NO_TURNS
        if (below == moreBelow && above == moreAbove && along == focusAlong) return
        moreBelow = below
        moreAbove = above
        focusAlong = along
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (moreBelow) drawFade(canvas, fadeBelow, marginBelow, 1f - focusAlong)
        if (moreAbove) drawFade(canvas, fadeAbove, marginAbove, focusAlong)
    }

    /** [fade], whole, or in a window of two rows as much as [share] says, the rest of it [margin] alone. */
    private fun drawFade(canvas: Canvas, fade: GradientDrawable, margin: GradientDrawable, share: Float) {
        if (focusAlong < 0f) return fade.draw(canvas)
        val alpha = (share * OPAQUE).roundToInt()
        fade.alpha = alpha
        fade.draw(canvas)
        margin.alpha = OPAQUE - alpha
        margin.draw(canvas)
    }

    private companion object {
        const val FILL_NONE = 0
        const val FILL_HIGH = 1
        const val NO_TURNS = -1f
        const val OPAQUE = 255
    }
}
