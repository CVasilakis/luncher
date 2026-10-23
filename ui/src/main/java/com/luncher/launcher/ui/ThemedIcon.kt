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

    /** Which color it takes, from its `colorRole`. */
    private val role: Int = context.obtainStyledAttributes(attrs, R.styleable.ThemedIcon).let { a ->
        a.getInt(R.styleable.ThemedIcon_colorRole, ROLE_ON_SURFACE).also { a.recycle() }
    }

    private var styled: ColorRoles? = null

    override fun style(roles: ColorRoles) {
        if (roles == styled) return
        styled = roles
        imageTintList = stateColors(roles.of(role), roles.inverseOnSurface, activated = roles.primary)
    }
}
