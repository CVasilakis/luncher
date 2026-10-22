package com.luncher.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import com.luncher.domain.appearance.ColorRoles

/**
 * An icon (one of this module's `icon_…` line drawings) in one of the theme's colors, as a
 * [ThemedText] is: tinted, so one drawing serves every color.
 */
class ThemedIcon @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ImageView(context, attrs), Themed {

    /** Which color it takes; set before [style]. */
    var role: Int

    private var styled: ColorRoles? = null

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ThemedIcon)
        role = a.getInt(R.styleable.ThemedIcon_colorRole, ROLE_ON_SURFACE)
        a.recycle()
    }

    override fun style(roles: ColorRoles) {
        if (roles == styled) return
        styled = roles
        imageTintList = stateColors(roles.of(role), roles.inverseOnSurface, activated = roles.primary)
    }

    /** Takes [role] from now on, styled again if it was. */
    fun changeRole(role: Int) {
        if (role == this.role) return
        this.role = role
        styled?.let { styled = null; style(it) }
    }
}
