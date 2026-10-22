package com.luncher.launcher.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.widget.ImageView
import com.luncher.domain.appearance.ColorRoles
import com.luncher.launcher.ui.Themed

/**
 * A dialog's mark, beside its title: an icon in a tinted circle, which says at a glance how it
 * went or what's asked. [Kind.DONE] in the accent's container (a picture added), [Kind.DANGER] in
 * the error's (deleting, a picture not added), [Kind.INFO] neutral (already there, nothing to add).
 */
internal class Badge @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ImageView(context, attrs), Themed {

    enum class Kind { DONE, DANGER, INFO }

    var kind: Kind = Kind.INFO
        set(value) {
            field = value
            styled?.let { styled = null; style(it) }
        }

    private var styled: ColorRoles? = null

    override fun style(roles: ColorRoles) {
        if (roles == styled) return
        styled = roles
        val (fill, icon) = when (kind) {
            Kind.DONE -> roles.primaryContainer to roles.onPrimaryContainer
            Kind.DANGER -> roles.errorContainer to roles.onErrorContainer
            Kind.INFO -> roles.surfaceHighest to roles.onSurface
        }
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
        }
        imageTintList = ColorStateList.valueOf(icon)
    }
}
