package com.luncher.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.widget.ProgressBar
import com.luncher.domain.appearance.ColorRoles

/** The spinning wheel of a wait (WorkingDialog, the share window), in the accent color. */
class ThemedSpinner @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ProgressBar(context, attrs), Themed {

    private var styled: ColorRoles? = null

    override fun style(roles: ColorRoles) {
        if (roles == styled) return
        styled = roles
        indeterminateTintList = ColorStateList.valueOf(roles.primary)
    }
}
