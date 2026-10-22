package com.luncher.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import com.luncher.domain.appearance.ColorRoles

/**
 * A text in one of the theme's colors (its `colorRole`: onSurface by default, onSurfaceVariant,
 * primary, error); the inverse on a focused entry, or a list's selected row, and the accent on a
 * chosen one: in an entry, with `duplicateParentState`, it takes the entry's state. An icon
 * beside it (`drawableStart`, one of the `icon_…` drawings) takes its colors too.
 */
open class ThemedText @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TextView(context, attrs), Themed {

    /** Which color it takes; set before [style]. */
    var role: Int

    private var styled: ColorRoles? = null

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ThemedText)
        role = a.getInt(R.styleable.ThemedText_colorRole, ROLE_ON_SURFACE)
        a.recycle()
    }

    override fun style(roles: ColorRoles) {
        if (roles == styled) return
        styled = roles
        val colors = stateColors(roles.of(role), roles.inverseOnSurface, activated = roles.primary)
        setTextColor(colors)
        for (icon in compoundDrawablesRelative) icon?.mutate()?.setTintList(colors)
    }

    /** Takes [role] from now on, styled again if it was. */
    fun changeRole(role: Int) {
        if (role == this.role) return
        this.role = role
        styled?.let { styled = null; style(it) }
    }

    companion object {
        const val ON_SURFACE = ROLE_ON_SURFACE
        const val ON_SURFACE_VARIANT = ROLE_ON_SURFACE_VARIANT
        const val PRIMARY = ROLE_PRIMARY
        const val ERROR = ROLE_ERROR
    }
}
