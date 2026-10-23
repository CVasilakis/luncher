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

    /** A column of rows [heights] px tall, in a margin of [margin] px, measured [width] px wide in at most [room] px. */
    private fun measured(heights: List<Int>, margin: Int = 8, room: Int, width: Int = 400): LookAheadScrollView {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, margin, 0, margin)
        }
        for (height in heights) column.addView(ListEntry(context).apply { minimumHeight = height })
        val scroll = LookAheadScrollView(context)
        scroll.addView(column)
        scroll.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(room, View.MeasureSpec.AT_MOST),
        )
        return scroll
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
}
