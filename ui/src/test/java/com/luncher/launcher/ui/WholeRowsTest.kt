package com.luncher.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WholeRowsTest {

    @Test
    fun `all rows fit, so nothing is fitted`() {
        assertNull(fitRows(room = 500, content = 500, row = 100, gap = 0))
    }

    @Test
    fun `the window ends on a whole row, the rows stretched where few pixels are left`() {
        // 4 rows of 100 and 30 px left: stretched by 1.075, against squeezing 5 by 1.16.
        val fit = fitRows(room = 430, content = 1000, row = 100, gap = 0)!!
        assertEquals(4, fit.shown)
        assertEquals(7, fit.extra)
        assertEquals(428, fit.window)
    }

    @Test
    fun `one more row is squeezed in where most of a row is left`() {
        // 4 rows of 100 and 80 px left: squeezing 5 by 1.04, against stretching 4 by 1.2.
        val fit = fitRows(room = 480, content = 1000, row = 100, gap = 0)!!
        assertEquals(5, fit.shown)
        assertEquals(-4, fit.extra)
        assertEquals(480, fit.window)
    }

    @Test
    fun `the gaps stay as they are, and none after the last row`() {
        // 3 rows of 52 and gaps of 4 in 180: (180 + 4) / 3 = 61 a row and its gap.
        val fit = fitRows(room = 180, content = 1000, row = 52, gap = 4)!!
        assertEquals(3, fit.shown)
        assertEquals(5, fit.extra)
        assertEquals(3 * 61 - 4, fit.window)
    }

    @Test
    fun `the threshold is where stretching and squeezing change the rows alike, not half a row`() {
        // n rows stretched or n + 1 squeezed change alike at room = pitch × √(n × (n + 1)).
        assertEquals(1, rowsShown(room = 141, pitch = 100))   // √2 = 1.414: stretched
        assertEquals(2, rowsShown(room = 142, pitch = 100))   // squeezed
        assertEquals(4, rowsShown(room = 447, pitch = 100))   // √20 = 4.472
        assertEquals(5, rowsShown(room = 448, pitch = 100))
        assertEquals(1, rowsShown(room = 30, pitch = 100))    // less than a row: one, squeezed
    }

    @Test
    fun `the focus moves while the row beyond the next one shows whole`() {
        assertEquals(0, slideFor(beyondEnd = 400, nextEnd = 300, windowEnd = 400, step = 100, left = 500))
    }

    @Test
    fun `the list slides a row under the focus when the row beyond the next one doesn't show whole`() {
        assertEquals(100, slideFor(beyondEnd = 401, nextEnd = 300, windowEnd = 400, step = 100, left = 500))
    }

    @Test
    fun `the last row goes to the edge with the focus alone`() {
        assertEquals(0, slideFor(beyondEnd = null, nextEnd = 400, windowEnd = 400, step = 100, left = 0))
    }

    @Test
    fun `the list never slides past its end`() {
        assertEquals(60, slideFor(beyondEnd = 500, nextEnd = 400, windowEnd = 400, step = 100, left = 60))
        assertEquals(0, slideFor(beyondEnd = 500, nextEnd = 400, windowEnd = 400, step = 100, left = -5))
    }

    @Test
    fun `the usual height is the one most rows have, the shortest of a tie, rows only`() {
        val heights = intArrayOf(104, 106, 106, 0, 106, 104, 104)
        assertEquals(104, usualHeight(heights.size) { heights[it] })
        val most = intArrayOf(106, 106, 104)
        assertEquals(106, usualHeight(most.size) { most[it] })
        assertEquals(0, usualHeight(0) { 0 })
    }
}
