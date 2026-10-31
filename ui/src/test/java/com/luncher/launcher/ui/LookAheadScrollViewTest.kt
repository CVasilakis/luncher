package com.luncher.launcher.ui

import android.view.View
import android.widget.LinearLayout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LookAheadScrollViewTest {

    private val context = RuntimeEnvironment.getApplication()

    /**
     * A column of rows [heights] px tall, in a margin of [margin] px, measured [width] px wide in at
     * most [room] px, twice; with [asWideAsItsRows], the column and the list as wide as the widest row
     * (a tile's menu), at most [width]; with [fills], the column fills the room (a picture editor's list).
     */
    private fun measured(heights: List<Int>, margin: Int = 8, room: Int, width: Int = 400, asWideAsItsRows: Boolean = false, fills: Boolean = false): LookAheadScrollView {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, margin, 0, margin)
            minimumWidth = 200
        }
        for (height in heights) {
            column.addView(ListEntry(context).apply { minimumHeight = height }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val scroll = LookAheadScrollView(context).apply { isFillViewport = fills }
        val wrap = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        scroll.addView(column, android.widget.FrameLayout.LayoutParams(if (asWideAsItsRows) wrap else LinearLayout.LayoutParams.MATCH_PARENT, wrap))
        // Twice, as Android often does (a window's layout after a measure of its own): the same.
        repeat(2) {
            scroll.measureIn(room, width, asWideAsItsRows, fills)
        }
        return scroll
    }

    private fun LookAheadScrollView.measureIn(room: Int, width: Int = 400, asWideAsItsRows: Boolean = false, fills: Boolean = false) {
        measure(
            View.MeasureSpec.makeMeasureSpec(width, if (asWideAsItsRows) View.MeasureSpec.AT_MOST else View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(room, if (fills) View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST),
        )
    }

    private val LookAheadScrollView.rows get() = (getChildAt(0) as LinearLayout).let { column -> (0 until column.childCount).map { column.getChildAt(it).measuredHeight } }

    @Test
    fun `rows a little apart in height are one height while they don't all fit, the window whole rows`() {
        // Mostly 104 (an option's chip), some 100 (a group's title), in 486 px less the margin:
        // 5 rows squeezed to 97 (by 1.07) change less than 4 stretched to 121 (by 1.17).
        val scroll = measured(listOf(100, 104, 104, 104, 100, 104, 104, 104, 104, 104), room = 502)

        assertEquals(listOf(scroll.rows[0]), scroll.rows.distinct())
        assertEquals(0, (scroll.measuredHeight - 16) % scroll.rows[0])
    }

    @Test
    fun `a row whose content needs more stays that much taller than the others`() {
        val scroll = measured(listOf(104, 104, 140, 104, 104, 104, 104, 104), room = 502)

        val usual = scroll.rows[0]
        assertEquals(usual + 36, scroll.rows[2])
        assertEquals(listOf(usual), scroll.rows.filterIndexed { i, _ -> i != 2 }.distinct())
    }

    @Test
    fun `rows that all fit keep their own heights`() {
        val scroll = measured(listOf(100, 104, 104), room = 502)

        assertEquals(listOf(100, 104, 104), scroll.rows)
        assertEquals(308 + 16, scroll.measuredHeight)
    }

    // Such a column measures its rows a second time, each as tall as it was: that height isn't the
    // row's own, else the list takes the fitted height for the usual row, and leaves the rows as
    // they were.
    @Test
    fun `rows of a column as wide as its widest row fill the window whole too`() {
        val scroll = measured(listOf(104, 104, 104), room = 244 + 16 - 40, asWideAsItsRows = true)

        assertEquals(listOf(scroll.rows[0]), scroll.rows.distinct())
        assertEquals(0, (scroll.measuredHeight - 16) % scroll.rows[0])
        assertEquals(true, scroll.rows[0] < 104)
    }

    // Seven rows of 123 squeezed to 121 in 850 px: 847 px of rows, and the column, filling the
    // room, would be 3 px taller than the list: that much to scroll, a fade while every row shows.
    @Test
    fun `a column that fills the room is as tall as the window of whole rows, with nothing to scroll`() {
        val scroll = measured(List(7) { 123 }, margin = 20, room = 890, fills = true)

        assertEquals(listOf(121), scroll.rows.distinct())
        assertEquals(887, scroll.measuredHeight)
        assertEquals(887, scroll.getChildAt(0).measuredHeight)
    }

    @Test
    fun `rows that fit again, one taken away, are as tall as they are`() {
        val scroll = measured(List(7) { 123 }, margin = 20, room = 890, fills = true)
        val column = scroll.getChildAt(0) as LinearLayout

        column.removeViewAt(0)
        scroll.measureIn(890, fills = true)

        assertEquals(listOf(123), scroll.rows.distinct())
        assertEquals(890, scroll.measuredHeight)
    }

    @Test
    fun `however little the room, two rows show`() {
        val scroll = measured(List(6) { 100 }, room = 116)

        assertEquals(listOf(50), scroll.rows.distinct())
        assertEquals(116, scroll.measuredHeight)
    }
}
