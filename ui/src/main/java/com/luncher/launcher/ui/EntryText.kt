package com.luncher.launcher.ui

import android.content.Context
import android.util.AttributeSet
import com.luncher.domain.appearance.ColorRoles

/**
 * An entry of one line, a [ListEntry] made of its text alone, its icon before it if it has one
 * (`drawableStart`, taking the text's colors): a menu's entries (a tile's, a picture's actions).
 * The inverse color while it has the focus; no zoom, as a list's rows. Its corners are
 * `corner_entry`'s, or `entryCorner`'s in a container, concentric with the container's.
 */
open class EntryText @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ThemedText(context, attrs) {

    private var entryStyled: ColorRoles? = null

    /** Its corners' radius, in px: `entryCorner`'s, or `corner_entry`'s; set, it's drawn with it from then on. */
    var corner: Float = context.obtainStyledAttributes(attrs, R.styleable.EntryText).let { a ->
        a.getDimension(R.styleable.EntryText_entryCorner, resources.getDimension(R.dimen.corner_entry)).also { a.recycle() }
    }
        set(value) {
            if (value == field) return
            field = value
            entryStyled?.let { entryStyled = null; style(it) }
        }

    override fun style(roles: ColorRoles) {
        super.style(roles)
        if (roles == entryStyled) return
        entryStyled = roles
        background = stateBackground(roundRect(roles.inverseSurface, corner), activated = null, rest = null)
    }
}
