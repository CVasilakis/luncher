package com.luncher.launcher.ui

import android.animation.AnimatorInflater
import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.luncher.domain.appearance.ColorRoles

/**
 * An entry of a panel or a list, Material for TV's list item: nothing behind it (or a field's
 * color, `fill="high"`, an option of the background editor), the inverse color while it has the
 * focus or is a list's selected row, so it stands out from afar, and a tint of the accent while
 * it's chosen (`activated`); a field, outlined in the accent while it's open (`activated`: its
 * values' list shows). Focused, it grows a little ([R.animator.entry_zoom]), with no shadow,
 * so whatever holds it leaves room around it for that (docs: ui/README.md). Its texts and icons
 * are [ThemedText]s and [ThemedIcon]s that take its state (`duplicateParentState`).
 */
open class ListEntry @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs), Themed {

    private val fill: Int
    private var styled: ColorRoles? = null

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ListEntry)
        fill = a.getInt(R.styleable.ListEntry_fill, FILL_NONE)
        a.recycle()
        stateListAnimator = AnimatorInflater.loadStateListAnimator(context, R.animator.entry_zoom)
    }

    override fun style(roles: ColorRoles) {
        if (roles == styled) return
        styled = roles
        val radius = resources.getDimension(R.dimen.corner_entry)
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

    private companion object {
        const val FILL_NONE = 0
        const val FILL_HIGH = 1

        /** How much of the accent tints the chosen entry. */
        const val CHOSEN = 0.16f
    }
}
