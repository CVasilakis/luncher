package com.luncher.launcher.ui

import android.animation.AnimatorInflater
import android.content.Context
import android.util.AttributeSet
import com.luncher.domain.appearance.ColorRoles

/**
 * An entry of one line, a [ListEntry] made of its text alone, its icon before it if it has one
 * (`drawableStart`, taking the text's colors): a menu's entries (a tile's, a picture's actions).
 * The inverse color while it has the focus, and a little zoom and lift ([R.animator.entry_zoom]).
 */
open class EntryText @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ThemedText(context, attrs) {

    private var entryStyled: ColorRoles? = null

    init {
        stateListAnimator = AnimatorInflater.loadStateListAnimator(context, R.animator.entry_zoom)
    }

    override fun style(roles: ColorRoles) {
        super.style(roles)
        if (roles == entryStyled) return
        entryStyled = roles
        val radius = resources.getDimension(R.dimen.corner_entry)
        background = stateBackground(roundRect(roles.inverseSurface, radius), activated = null, rest = null)
    }
}
