package com.luncher.domain.layout

import com.luncher.domain.appearance.Side
import com.luncher.domain.appearance.TopBarAppearance

/** Something the home screen's top bar shows. */
enum class TopBarItem { TIME, DATE, SETTINGS }

/**
 * Where the top bar's items go: those at its left edge and those at its right edge, each in order
 * left to right; the space between the two stretches. The home screen and any preview of it both
 * place the items by this, so they can't disagree.
 */
data class TopBarLayout(val left: List<TopBarItem>, val right: List<TopBarItem>)

/** The top bar for [appearance]: the clock, time and date side by side, on one side, the settings entry on the other. */
fun topBarLayout(appearance: TopBarAppearance): TopBarLayout {
    val clock = when (appearance.timeSide) {
        Side.LEFT -> listOf(TopBarItem.TIME, TopBarItem.DATE)
        Side.RIGHT -> listOf(TopBarItem.DATE, TopBarItem.TIME)
    }
    val settings = listOf(TopBarItem.SETTINGS)
    return when (appearance.clockSide) {
        Side.LEFT -> TopBarLayout(left = clock, right = settings)
        Side.RIGHT -> TopBarLayout(left = settings, right = clock)
    }
}
