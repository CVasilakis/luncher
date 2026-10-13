package com.luncher.launcher.home

import android.content.Context
import android.util.AttributeSet
import android.view.FocusFinder
import android.view.View
import android.widget.LinearLayout
import com.luncher.domain.appearance.TopBarAppearance
import com.luncher.domain.layout.TopBarItem
import com.luncher.domain.layout.topBarLayout

/**
 * The home screen's top bar: the clock and the settings entry, each at the edge of the bar that
 * the domain's [topBarLayout] gives for the home screen's [appearance], and the time on its side of
 * the date, each of the two only if it shows. Arrange mode's title and key hint stay between them,
 * in the order the layout file gives, since they replace both.
 *
 * Left and Right move the focus only among the bar's own items, in the order the layout gives
 * them, and do nothing at its ends; Up and Down are Android's search, Down to the tiles.
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
        val settingsAtLeft = TopBarItem.SETTINGS in layout.left
        // The clock fills the rest of the bar, even with nothing in it, so the gear stays at its edge.
        clock.arrange(if (settingsAtLeft) layout.right else layout.left, atRight = settingsAtLeft)
        move(if (settingsAtLeft) settings else clock, 0)
        move(if (settingsAtLeft) clock else settings, childCount - 1)
        requestLayout()
        invalidate()
    }

    // Android's own search would go on to the nearest tile that way, below the bar: from the
    // settings entry at the right, the one a pixel left of its right edge on a 1080p TV.
    override fun focusSearch(focused: View, direction: Int): View? {
        if (direction != FOCUS_LEFT && direction != FOCUS_RIGHT) return super.focusSearch(focused, direction)
        return FocusFinder.getInstance().findNextFocus(this, focused, direction) ?: focused
    }

    /** Moves [view] to [index] among the bar's children. Detached, not removed: removing would take the focus away from it. */
    private fun move(view: View, index: Int) {
        val from = indexOfChild(view)
        if (from == index) return
        detachViewFromParent(from)
        attachViewToParent(view, index, view.layoutParams)
    }
}
