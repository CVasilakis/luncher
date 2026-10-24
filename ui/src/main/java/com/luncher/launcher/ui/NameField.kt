package com.luncher.launcher.ui

import android.content.Context
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.widget.EditText
import com.luncher.domain.appearance.ColorRoles

/**
 * The field of a new name ([NameDialog]): a field's color, outlined in the accent while it has the
 * focus, and in the error color once a name was [refused].
 */
class NameField @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : EditText(context, attrs), Themed {

    var refused = false
        set(value) {
            if (value == field) return
            field = value
            styled?.let { styled = null; style(it) }
        }

    private var styled: ColorRoles? = null

    override fun style(roles: ColorRoles) {
        if (roles == styled) return
        styled = roles
        val radius = resources.getDimension(R.dimen.corner_entry)
        val edge = resources.getDimensionPixelSize(R.dimen.name_field_edge)
        val focusedEdge = if (refused) roles.error else roles.primary
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), roundRect(roles.surfaceHigh, radius, edge, focusedEdge))
            addState(intArrayOf(), roundRect(roles.surfaceHigh, radius, edge, if (refused) roles.error else roles.outlineVariant))
        }
        setTextColor(roles.onSurface)
        highlightColor = withAlpha(roles.primary, SELECTION)
    }

    private companion object {
        const val SELECTION = 0.35f
    }
}
