package com.luncher.launcher.ui

import android.animation.AnimatorInflater
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.TextView
import com.luncher.domain.appearance.ColorRoles

/**
 * A button, Material for TV's: round ends, its text on one line, an icon before it if it has one
 * (`drawableStart`, one of the `icon_…` drawings, tinted with the text), the two centered together.
 * Its `kind`: filled with the accent (Save, OK), tonal (Cancel, Add all), or the error color's edge
 * (Delete all, Reset to defaults). Focused, every kind turns to the inverse color (a danger one to
 * the error color), and it grows and is lifted ([R.animator.button_zoom]); while OK is held it dips
 * under its resting size and its shadow is set down, by the same animator. Where its text doesn't
 * fit with the icon (a long translation, a narrow screen), the icon gives way, so the text shows
 * whole. It draws the icon itself, so whether it shows never changes the layout.
 */
open class PillButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TextView(context, attrs), Themed {

    /** [FILLED], [TONAL] or [DANGER]; a new one restyles it. */
    var kind: Int = FILLED
        set(value) {
            if (value == field) return
            field = value
            styled?.let { styled = null; style(it) }
        }

    private var styled: ColorRoles? = null
    private var icon: Drawable? = null
    private var colors: ColorStateList? = null

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.PillButton)
        kind = a.getInt(R.styleable.PillButton_kind, FILLED)
        a.recycle()
        gravity = Gravity.CENTER
        // The themes line texts up at their start (textAlignment), which would win over gravity.
        textAlignment = TEXT_ALIGNMENT_CENTER
        stateListAnimator = AnimatorInflater.loadStateListAnimator(context, R.animator.button_zoom)
        // The icon of drawableStart, drawn here beside the text rather than at the button's edge.
        compoundDrawablesRelative[0]?.let { setIcon(it) }
        setCompoundDrawablesRelative(null, null, null, null)
    }

    /** The icon before the text, one of the `icon_…` drawings; null for none. */
    fun setIcon(drawable: Drawable?) {
        icon = drawable?.mutate()?.also { it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight) }
        colors?.let { icon?.setTintList(it) }
        invalidate()
    }

    /** [setIcon] with a drawable resource. */
    fun setIcon(id: Int) = setIcon(if (id == 0) null else context.getDrawable(id))

    /** How wide it is without its icon, its text whole on one line: what it needs at least. */
    fun widthWithoutIcon(): Int = kotlin.math.ceil(paint.measureText(text, 0, text.length)).toInt() + paddingLeft + paddingRight

    override fun style(roles: ColorRoles) {
        if (roles == styled) return
        styled = roles
        // Round ends: a radius as large as the button is high, which the shape keeps to half.
        val radius = resources.getDimension(R.dimen.button_height)
        val outline = resources.getDimensionPixelSize(R.dimen.outline)
        val (rest, text) = when (kind) {
            TONAL -> roundRect(roles.surfaceHighest, radius) to roles.onSurface
            DANGER -> roundRect(0, radius, outline, roles.error) to roles.error
            else -> roundRect(roles.primary, radius) to roles.onPrimary
        }
        val focused = if (kind == DANGER) roundRect(roles.error, radius) else roundRect(roles.inverseSurface, radius)
        val focusedText = if (kind == DANGER) roles.onError else roles.inverseOnSurface
        background = stateBackground(focused, activated = null, rest = rest)
        val colors = stateColors(text, focusedText)
        this.colors = colors
        setTextColor(colors)
        icon?.setTintList(colors)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        icon?.let { if (it.isStateful && it.setState(drawableState)) invalidate() }
    }

    // The icon and the text centered together: the text, centered alone in its layout, moves by
    // half the icon and its gap, the icon goes before it. Nothing is allocated.
    override fun onDraw(canvas: Canvas) {
        val icon = icon
        val textWidth = if (icon == null) 0f else paint.measureText(text, 0, text.length)
        val room = width - paddingLeft - paddingRight
        val gap = compoundDrawablePadding
        if (icon == null || textWidth + gap + icon.intrinsicWidth > room) return super.onDraw(canvas)
        val group = textWidth + gap + icon.intrinsicWidth
        val shift = (icon.intrinsicWidth + gap) / 2f
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val groupStart = paddingLeft + (room - group) / 2f
        val iconLeft = if (rtl) groupStart + textWidth + gap else groupStart
        canvas.save()
        canvas.translate(iconLeft + scrollX, scrollY + (height - icon.intrinsicHeight) / 2f)
        icon.draw(canvas)
        canvas.restore()
        canvas.save()
        canvas.translate(if (rtl) -shift else shift, 0f)
        super.onDraw(canvas)
        canvas.restore()
    }

    companion object {
        const val FILLED = 0
        const val TONAL = 1
        const val DANGER = 2
    }
}
