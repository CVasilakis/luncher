package com.luncher.launcher.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/**
 * Buttons side by side, each half the row (a third, for three, …), a gap between them, as long as
 * each one's text fits its share; else one below the other, each the whole row, so no text is cut
 * (a long translation, a narrow column). The editors' Save and Delete all, Background and Reset to
 * defaults.
 */
internal class EntriesRow(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    private val gap = resources.getDimensionPixelSize(R.dimen.settings_buttons_gap)
    private val entryGap = resources.getDimensionPixelSize(R.dimen.settings_entry_gap)

    /** One below the other whatever they take, as a menu's entries are. */
    var stacked = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val count = childCount.coerceAtLeast(1)
        val share = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight - gap * (count - 1)) / count
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        var fits = !stacked
        if (fits) for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(unspecified, unspecified)
            // A button fits with its text whole: its icon gives way where it must.
            val needs = (child as? com.luncher.launcher.ui.PillButton)?.widthWithoutIcon() ?: child.measuredWidth
            if (needs > share) fits = false
        }
        // Set only when it changes: a change asks for a layout.
        val wanted = if (fits) HORIZONTAL else VERTICAL
        if (orientation != wanted) orientation = wanted
        for (i in 0 until childCount) {
            val params = getChildAt(i).layoutParams as LayoutParams
            params.width = if (fits) 0 else LayoutParams.MATCH_PARENT
            params.weight = if (fits) 1f else 0f
            params.marginStart = if (fits && i > 0) gap else 0
            params.topMargin = if (!fits && i > 0) (if (stacked) entryGap else gap) else 0
            // A start margin becomes a left or right one only as the params are resolved, which
            // Android did before this measure: resolved again, or the gap would never show.
            params.resolveLayoutDirection(layoutDirection)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
