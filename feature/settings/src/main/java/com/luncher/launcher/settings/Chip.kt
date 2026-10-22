package com.luncher.launcher.settings

import android.content.Context
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.widget.LinearLayout
import com.luncher.domain.appearance.ColorRoles
import com.luncher.launcher.ui.Themed
import com.luncher.launcher.ui.ThemedText
import com.luncher.launcher.ui.roundRect
import com.luncher.launcher.ui.withAlpha

/**
 * A small label with round ends in an entry: an app's "Hidden". On the entry's color, and a shade
 * of the inverse text while the entry has the focus (it takes the entry's state,
 * `duplicateParentState`).
 */
internal class Chip @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ThemedText(context, attrs) {

    private var chipStyled: ColorRoles? = null

    override fun style(roles: ColorRoles) {
        super.style(roles)
        if (roles == chipStyled) return
        chipStyled = roles
        background = chipBackground(roles, resources.getDimension(R.dimen.settings_chip_height))
    }
}

/**
 * A chip that holds more than a text: a stepped option's value between its arrows, the accent's
 * swatch beside it (the appearance editor). Its views are themed on their own.
 */
internal class ChipFrame @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs), Themed {

    private var styled: ColorRoles? = null

    override fun style(roles: ColorRoles) {
        if (roles == styled) return
        styled = roles
        background = chipBackground(roles, resources.getDimension(R.dimen.settings_chip_height))
    }
}

private fun chipBackground(roles: ColorRoles, radius: Float): StateListDrawable {
    val focused = roundRect(withAlpha(roles.inverseOnSurface, FOCUSED_SHADE), radius)
    return StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), focused)
        addState(intArrayOf(android.R.attr.state_selected), focused)
        addState(intArrayOf(), roundRect(roles.surfaceHighest, radius))
    }
}

/** How much of the inverse text shades a chip on a focused entry. */
private const val FOCUSED_SHADE = 0.12f
