package com.luncher.domain.layout

import com.luncher.domain.appearance.Side
import com.luncher.domain.appearance.TopBarAppearance

/** Something the home screen's top bar shows. */
enum class TopBarItem { TIME, DATE, SETTINGS }

/**
 * Where the top bar's items go: those at its left edge and those at its right edge, each in order
 * left to right; the space between the two stretches. An item that's hidden is in neither. The
 * home screen and any preview of it both place the items by this, so they can't disagree.
 */
data class TopBarLayout(val left: List<TopBarItem>, val right: List<TopBarItem>)

/**
 * The top bar for [appearance]: the time and the date side by side, those of the two that show, on
 * one side, the settings entry on the other. With neither, the settings entry is where it is by
 * default, at the right, so the clock's side changes nothing then.
 */
fun topBarLayout(appearance: TopBarAppearance): TopBarLayout {
    val ordered = when (appearance.timeSide) {
        Side.LEFT -> listOf(TopBarItem.TIME, TopBarItem.DATE)
        Side.RIGHT -> listOf(TopBarItem.DATE, TopBarItem.TIME)
    }
    val clock = ordered.filter {
        when (it) {
            TopBarItem.TIME -> appearance.showTime
            TopBarItem.DATE -> appearance.showDate
            TopBarItem.SETTINGS -> false
        }
    }
    val settings = listOf(TopBarItem.SETTINGS)
    if (clock.isEmpty()) return TopBarLayout(left = emptyList(), right = settings)
    return when (appearance.clockSide) {
        Side.LEFT -> TopBarLayout(left = clock, right = settings)
        Side.RIGHT -> TopBarLayout(left = settings, right = clock)
    }
}
