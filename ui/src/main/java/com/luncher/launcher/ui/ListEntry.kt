package com.luncher.launcher.ui

import android.animation.AnimatorInflater
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.luncher.domain.appearance.ColorRoles

/**
 * An entry of a panel or a list, Material for TV's list item: nothing behind it (or a field's
 * color, `fill="high"`, an option of the background editor), the inverse color while it has the
 * focus or is a list's selected row, so it stands out from afar, and a tint of the accent while
 * it's chosen (`activated`); a field, outlined in the accent while it's open (`activated`: its
 * values' list shows). A row of a list stays its size while focused; a field (`zooms="true"`)
 * grows a little ([R.animator.entry_zoom]), with no shadow, so whatever holds it leaves room
 * around it for that (docs: ui/README.md). Its corners are `corner_entry`'s, or `entryCorner`'s
 * in a container, concentric with the container's. Its texts and icons are [ThemedText]s and
 * [ThemedIcon]s that take its state (`duplicateParentState`).
 *
 * In a list that scrolls a row ahead ([LookAheadListView], [LookAheadScrollView]), a row is as tall
 * as its list makes its rows, so the list's window ends on a whole row ([FittedRows]), and while the list
 * slides under the focus ([FocusSlide]), the list draws the row's focused look, and the row itself
 * looks at rest meanwhile.
 */
open class ListEntry @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs), Themed {

    private val fill: Int
    private var styled: ColorRoles? = null

    /**
     * Its corners' radius, in px: `entryCorner`'s, or `corner_entry`'s; set, it's drawn with it
     * from then on (a tile's menu, whose corners follow the tiles').
     */
    var corner: Float = 0f
        set(value) {
            if (value == field) return
            field = value
            styled?.let { styled = null; style(it) }
        }

    /** The focused fill alone, drawn by its list where the focus is while the list slides. */
    private var focusFill: Drawable? = null

    /** While a list slides under the focus: looks at rest, even focused or selected. */
    internal var lookHeld = false
        set(value) {
            if (field == value) return
            field = value
            refreshDrawableState()
        }

    /** While [drawFocused] draws it: looks focused. */
    private var lookForced = false

    /** As tall as it is, before its list gives it a height ([FittedRows]). */
    internal var naturalHeight = 0
        private set

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ListEntry)
        fill = a.getInt(R.styleable.ListEntry_fill, FILL_NONE)
        corner = a.getDimension(R.styleable.ListEntry_entryCorner, resources.getDimension(R.dimen.corner_entry))
        if (a.getBoolean(R.styleable.ListEntry_zooms, false)) {
            stateListAnimator = AnimatorInflater.loadStateListAnimator(context, R.animator.entry_zoom)
        }
        a.recycle()
    }

    override fun style(roles: ColorRoles) {
        if (roles == styled) return
        styled = roles
        val radius = corner
        focusFill = roundRect(roles.inverseSurface, radius)
        background = stateBackground(
            focused = roundRect(roles.inverseSurface, radius),
            activated = when (fill) {
                FILL_HIGH -> roundRect(roles.surfaceHigh, radius, resources.getDimensionPixelSize(R.dimen.open_field_edge), roles.primary)
                else -> roundRect(withAlpha(roles.primary, CHOSEN), radius)
            },
            rest = when (fill) {
                FILL_HIGH -> roundRect(roles.surfaceHigh, radius)
                else -> null
            },
        )
    }

    // As tall as its list makes its rows (FittedRows): in a column of a LookAheadScrollView, or a
    // LookAheadListView. Final, so an entry that places its content itself (measureContent) can't
    // leave the list's height out.
    final override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        measureContent(widthMeasureSpec, heightMeasureSpec)
        // A height its parent forces (a column as wide as its widest row measures them all again,
        // each as tall as it was) isn't its own: only the first, for a row of a fixed height.
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY || naturalHeight == 0) naturalHeight = measuredHeight
        val list = parent as? FittedRows ?: parent?.parent as? FittedRows ?: return
        if (list.rowHeight > 0) setMeasuredDimension(measuredWidth, maxOf(list.rowHeight, naturalHeight + list.rowExtra))
    }

    /** Measures the entry as tall as its content needs; a subclass that places its content itself measures it here. */
    @SuppressLint("WrongCall")   // the measuring itself, called from onMeasure, not a measure of another view
    protected open fun measureContent(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    // Held or forced, its state is one of a few fixed ones: no array made, but where a text in it
    // adds a state of its own (a text on more than one line) into the room it asks for.
    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        if (!lookHeld && !lookForced) return super.onCreateDrawableState(extraSpace)
        val state = when {
            lookForced -> if (isActivated) FORCED_ACTIVATED else FORCED
            else -> if (isActivated) HELD_ACTIVATED else HELD
        }
        return if (extraSpace == 0) state else state.copyOf(state.size + extraSpace)
    }

    /**
     * Draws what the entry holds (its texts, icons, chips) as it looks focused, inverse, without
     * the fill behind them ([drawFocusFill]), into [canvas], a recording or a bitmap's: the
     * hardware one keeps one drawing of each view a frame, its look at rest. Not for a field that
     * zooms: the state would start its zoom.
     */
    @SuppressLint("WrongCall")   // what the entry holds without its background, which draw() would draw
    internal fun drawFocused(canvas: Canvas) {
        lookForced = true
        refreshDrawableState()
        onDraw(canvas)
        dispatchDraw(canvas)
        lookForced = false
        refreshDrawableState()
    }

    // Drawn into a recording or a bitmap (drawFocused), a child cut along its outline (a
    // thumbnail's rounded corners) is cut so there too: those canvases leave outlines to the GPU's
    // drawing of each view. From API 24 on, where an outline's shape can be read.
    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (!lookForced || !child.clipToOutline || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return super.drawChild(canvas, child, drawingTime)
        }
        val provider = child.outlineProvider ?: return super.drawChild(canvas, child, drawingTime)
        outline.setEmpty()
        provider.getOutline(child, outline)
        if (!outline.getRect(outlineRect)) return super.drawChild(canvas, child, drawingTime)
        outlineBounds.set(outlineRect)
        outlineBounds.offset(child.left.toFloat(), child.top.toFloat())
        outlinePath.rewind()
        outlinePath.addRoundRect(outlineBounds, outline.radius, outline.radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(outlinePath)
        val drawn = super.drawChild(canvas, child, drawingTime)
        canvas.restore()
        return drawn
    }

    /** Draws the entry's focused fill, its corners too, over [bounds]. */
    internal fun drawFocusFill(canvas: Canvas, bounds: Rect) {
        val fill = focusFill ?: return
        fill.bounds = bounds
        fill.draw(canvas)
    }

    private companion object {
        const val FILL_NONE = 0
        const val FILL_HIGH = 1

        /** How much of the accent tints the chosen entry. */
        const val CHOSEN = 0.16f

        // For drawChild, on the main thread only: one set for every entry.
        val outline = Outline()
        val outlineRect = Rect()
        val outlineBounds = RectF()
        val outlinePath = Path()

        val HELD = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_window_focused)
        val HELD_ACTIVATED = HELD + android.R.attr.state_activated
        val FORCED = HELD + android.R.attr.state_focused
        val FORCED_ACTIVATED = FORCED + android.R.attr.state_activated
    }
}
