package com.luncher.launcher.settings

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.AppearanceOption
import com.luncher.domain.appearance.OptionContext
import com.luncher.launcher.ui.color
import com.luncher.launcher.ui.R as UiR

/**
 * An option's values in a list over its [row], opened by OK on the row, the value it has focused
 * and lined up with the row, and its end with the row's; moved up or down only to stay inside the
 * TV's overscan margin: the background editor's way to change
 * an option, where Left and Right on the row must move the focus, to the pictures beside it. The
 * values are those [context] offers; the one the option had is in the accent color. Each value
 * the focus comes to goes to [change] at once, so the preview shows it; OK on one keeps it and
 * closes the list, and Back closes it with the value the option had again, as [change] gets it.
 */
internal class OptionList(
    option: AppearanceOption<*>,
    row: View,
    appearance: Appearance,
    context: OptionContext,
    private val change: (Appearance) -> Unit,
) : PopupWindow.OnDismissListener {

    private val before = appearance
    private var kept = false
    private val window: PopupWindow

    init {
        val activity = row.context
        val choices = option.choices(appearance, context)
        val current = option.indexIn(appearance, context).coerceAtLeast(0)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val inflater = LayoutInflater.from(activity)
        for ((index, choice) in choices.withIndex()) {
            val entry = inflater.inflate(R.layout.settings_option_list_entry, list, false) as TextView
            entry.text = activity.optionValue(option, choice)
            if (index == current) entry.setTextColor(activity.color(UiR.color.accent))
            entry.setOnFocusChangeListener { _, focused -> if (focused) change(choice) }
            entry.setOnClickListener {
                kept = true
                window.dismiss()
            }
            list.addView(entry)
        }
        val padding = activity.resources.getDimensionPixelSize(R.dimen.settings_option_list_padding)
        list.setPadding(0, padding, 0, padding)
        list.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val width = maxOf(list.measuredWidth, activity.resources.getDimensionPixelSize(R.dimen.settings_option_list_min_width))
        // Focusable, with a background: it takes the keys, Back closing it, on every API level.
        window = PopupWindow(list, width, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(activity.getDrawable(R.drawable.settings_option_list_background))
            elevation = activity.resources.getDimension(R.dimen.settings_option_list_elevation)
            animationStyle = 0
            setOnDismissListener(this@OptionList)
        }

        showOver(row, list, width, padding, current)
        list.getChildAt(current)?.requestFocus()
    }

    /**
     * Shows [list], [width] wide, its entry [current] over [row] and its end at the row's end, inside
     * the TV's overscan margin, as the editor's content, where there's room for it. LEFT, not START:
     * the place is in pixels from the window's left, worked out for either direction.
     */
    @SuppressLint("RtlHardcoded")
    private fun showOver(row: View, list: View, width: Int, padding: Int, current: Int) {
        val resources = row.resources
        val at = IntArray(2)
        row.getLocationInWindow(at)
        val entryHeight = resources.getDimensionPixelSize(R.dimen.settings_entry_height)
        val x = if (row.layoutDirection == View.LAYOUT_DIRECTION_RTL) at[0] else at[0] + row.width - width
        val y = at[1] + (row.height - entryHeight) / 2 - padding - current * entryHeight
        val margin = resources.getDimensionPixelSize(R.dimen.settings_appearance_margin_vertical)
        val lowest = maxOf(margin, row.rootView.height - margin - list.measuredHeight)
        window.showAtLocation(row, Gravity.LEFT or Gravity.TOP, x, y.coerceIn(margin, lowest))
    }

    /** Closes the list, the option keeping the value it shows: its screen is going. */
    fun close() {
        kept = true
        window.dismiss()
    }

    override fun onDismiss() {
        if (!kept) change(before)
    }
}

/** [appearance] with each value [context] offers, in order. */
private fun <T> AppearanceOption<T>.choices(appearance: Appearance, context: OptionContext): List<Appearance> =
    values(context).map { set(appearance, it) }

/** Where [appearance]'s value is among those [context] offers; -1 if it isn't. */
private fun <T> AppearanceOption<T>.indexIn(appearance: Appearance, context: OptionContext): Int =
    values(context).indexOf(get(appearance))
