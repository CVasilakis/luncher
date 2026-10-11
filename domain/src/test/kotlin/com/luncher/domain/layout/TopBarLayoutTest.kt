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
}
