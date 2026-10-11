package com.luncher.launcher.home

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.luncher.domain.appearance.TopBarAppearance
import com.luncher.domain.layout.TopBarItem
import com.luncher.domain.layout.topBarLayout

/**
 * The home screen's top bar: the clock and the settings entry, each at the edge of the bar that
 * the domain's [topBarLayout] gives for the home screen's [appearance], and the time on its side of
 * the date. Arrange mode's title and key hint stay between them, in the order the layout file
 * gives, since they replace both.
 */
internal class TopBarView(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    private lateinit var clock: ClockView
    private lateinit var settings: View

    /** From the home screen's appearance; a different one moves the items, compared with `==`. */
    var appearance: TopBarAppearance = TopBarAppearance()
        set(value) {
            if (field == value) return
            field = value
            arrange()
        }

    override fun onFinishInflate() {
        super.onFinishInflate()
        clock = findViewById(R.id.home_clock)
        settings = findViewById(R.id.home_settings)
        arrange()
    }

    private fun arrange() {
        val layout = topBarLayout(appearance)
        val clockItems = if (TopBarItem.TIME in layout.left) layout.left else layout.right
        clock.arrange(
            timeFirst = clockItems.indexOf(TopBarItem.TIME) < clockItems.indexOf(TopBarItem.DATE),
            atRight = clockItems === layout.right,
        )
        val settingsAtLeft = TopBarItem.SETTINGS in layout.left
        move(if (settingsAtLeft) settings else clock, 0)
        move(if (settingsAtLeft) clock else settings, childCount - 1)
        requestLayout()
        invalidate()
    }

    /** Moves [view] to [index] among the bar's children. Detached, not removed: removing would take the focus away from it. */
    private fun move(view: View, index: Int) {
        val from = indexOfChild(view)
        if (from == index) return
        detachViewFromParent(from)
        attachViewToParent(view, index, view.layoutParams)
    }
}
