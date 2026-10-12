package com.luncher.domain.layout

import com.luncher.domain.appearance.Side
import com.luncher.domain.appearance.TopBarAppearance
import com.luncher.domain.layout.TopBarItem.DATE
import com.luncher.domain.layout.TopBarItem.SETTINGS
import com.luncher.domain.layout.TopBarItem.TIME
import org.junit.Assert.assertEquals
import org.junit.Test

class TopBarLayoutTest {

    @Test
    fun `by default, the time and the date at the left, the settings entry at the right`() {
        assertEquals(TopBarLayout(left = listOf(TIME, DATE), right = listOf(SETTINGS)), topBarLayout(TopBarAppearance()))
    }

    @Test
    fun `the clock on the right swaps it with the settings entry`() {
        assertEquals(
            TopBarLayout(left = listOf(SETTINGS), right = listOf(TIME, DATE)),
            topBarLayout(TopBarAppearance(clockSide = Side.RIGHT)),
        )
    }

    @Test
    fun `the time goes on its side of the date, wherever the clock is`() {
        assertEquals(
            TopBarLayout(left = listOf(DATE, TIME), right = listOf(SETTINGS)),
            topBarLayout(TopBarAppearance(timeSide = Side.RIGHT)),
        )
        assertEquals(
            TopBarLayout(left = listOf(SETTINGS), right = listOf(DATE, TIME)),
            topBarLayout(TopBarAppearance(clockSide = Side.RIGHT, timeSide = Side.RIGHT)),
        )
    }

    @Test
    fun `leaves out the time or the date that's hidden`() {
        assertEquals(
            TopBarLayout(left = listOf(DATE), right = listOf(SETTINGS)),
            topBarLayout(TopBarAppearance(showTime = false)),
        )
        assertEquals(
            TopBarLayout(left = listOf(SETTINGS), right = listOf(TIME)),
            topBarLayout(TopBarAppearance(clockSide = Side.RIGHT, timeSide = Side.RIGHT, showDate = false)),
        )
    }

    // So the clock's side changes nothing then, and the editor can dim it.
    @Test
    fun `with neither the time nor the date, the settings entry at the right, wherever the clock was`() {
        for (side in Side.entries) {
            assertEquals(
                TopBarLayout(left = emptyList(), right = listOf(SETTINGS)),
                topBarLayout(TopBarAppearance(clockSide = side, showTime = false, showDate = false)),
            )
        }
    }
}
