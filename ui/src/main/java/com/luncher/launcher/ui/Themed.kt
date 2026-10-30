package com.luncher.launcher.ui

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import com.luncher.domain.appearance.ColorRoles

/**
 * A view that takes its colors from the theme ([ColorRoles], the domain's for the user's theme):
 * every view of the menus and settings that has a color is one. A screen styles all of them once
 * it's made ([styleAll]), and again when the theme changes while it shows: the views keep the
 * roles they were styled with, and an equal one ([ColorRoles] compared with `==`) makes nothing
 * anew. The colors are set in code, not through the platform themes: a color state list there
 * can't name a theme's attribute before API 23, and the theme changes while a screen shows.
 */
interface Themed {
    fun style(roles: ColorRoles)
}

/** Styles this view, if it's [Themed], and each [Themed] view inside it. */
fun View.styleAll(roles: ColorRoles) {
    if (this is Themed) style(roles)
    if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).styleAll(roles)
}

/** [color] at [alpha] (0 to 1) of its own opacity. */
fun withAlpha(color: Int, alpha: Float): Int = (((color ushr 24) * alpha).toInt() shl 24) or (color and 0xFFFFFF)

/** A color role, as a themed text or icon names it (the `colorRole` attribute). */
internal fun ColorRoles.of(role: Int): Int = when (role) {
    ROLE_ON_SURFACE_VARIANT -> onSurfaceVariant
    ROLE_PRIMARY -> primary
    ROLE_ERROR -> error
    else -> onSurface
}

internal const val ROLE_ON_SURFACE = 0
internal const val ROLE_ON_SURFACE_VARIANT = 1
internal const val ROLE_PRIMARY = 2
internal const val ROLE_ERROR = 3

// Focused while its window has the focus: a dialog or a list over a screen takes the keys, and
// what had the focus there no longer looks focused meanwhile.
private val FOCUSED = intArrayOf(android.R.attr.state_focused, android.R.attr.state_window_focused)
private val PRESSED_FOCUSED = intArrayOf(android.R.attr.state_pressed, android.R.attr.state_focused, android.R.attr.state_window_focused)
private val PRESSED_SELECTED = intArrayOf(android.R.attr.state_pressed, android.R.attr.state_selected)
private val SELECTED = intArrayOf(android.R.attr.state_selected)
private val ACTIVATED = intArrayOf(android.R.attr.state_activated)
private val ANY = intArrayOf()

/**
 * A text's or an icon's colors: [rest], the inverse on a focused entry or a list's selected row
 * ([focused]), and the accent on the entry chosen ([activated], the background's picture, say).
 */
fun stateColors(rest: Int, focused: Int, activated: Int = rest): ColorStateList =
    ColorStateList(arrayOf(FOCUSED, SELECTED, ACTIVATED, ANY), intArrayOf(focused, focused, activated, rest))

/** A rounded rectangle of [color], [radius] px at its corners, with an edge of [stroke] px in [strokeColor]. */
fun roundRect(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (stroke > 0) setStroke(stroke, strokeColor)
    }

/**
 * What's behind an entry: [focused] while it has the focus or is a list's selected row, [pressed]
 * while OK is held on it then (null: [focused] stays), [activated] while it's chosen, else [rest]
 * (null: nothing). Only a state change picks among them: nothing costs while a list scrolls.
 */
internal fun stateBackground(focused: Drawable, activated: Drawable?, rest: Drawable?, pressed: Drawable? = null): StateListDrawable =
    StateListDrawable().apply {
        if (pressed != null) {
            addState(PRESSED_FOCUSED, pressed)
            addState(PRESSED_SELECTED, pressed)
        }
        addState(FOCUSED, focused)
        addState(SELECTED, focused)
        if (activated != null) addState(ACTIVATED, activated)
        addState(ANY, rest)
    }
