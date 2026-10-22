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
import com.luncher.domain.appearance.ColorRoles
import com.luncher.domain.appearance.OptionContext
import com.luncher.launcher.ui.roundRect
import com.luncher.launcher.ui.styleAll

/**
 * An option's values in a list beside its [row], at the row's end edge, opened by OK on it: the
 * value it has focused, checked, and level with the row, moved up or down only to stay inside the
 * TV's overscan margin. So the preview isn't covered while the user tries the values, nor the
 * row: a list of a field at the [options]' start reaches their end edge, covering whole what's
 * beside the field there (the other column's fields, Delete all), not a part of them; a field
 * at their end has its list over the pictures. The background editor's way to change an option, where Left and
 * Right on the row must move the focus, to the pictures or the field beside it. The values are
 * those [context] offers. Each value the focus comes to goes to [change] at once, so the preview
 * shows it; OK on one keeps it and closes the list, and Back closes it with the value the option
 * had again, as [change] gets it. [closed] is told once it's gone. The row's own outline, in the
 * accent, shows it open.
 */
internal class OptionList(
    option: AppearanceOption<*>,
    row: View,
    options: View,
    appearance: Appearance,
    context: OptionContext,
    roles: ColorRoles,
    private val change: (Appearance) -> Unit,
    private val closed: () -> Unit,
) : PopupWindow.OnDismissListener {

    private val before = appearance
    private var kept = false
    private val window: PopupWindow

    init {
        val activity = row.context
        val resources = activity.resources
        val choices = option.choices(appearance, context)
        val current = option.indexIn(appearance, context).coerceAtLeast(0)
        val padding = resources.getDimensionPixelSize(R.dimen.settings_option_list_padding)
        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(padding, padding, padding, padding)
        }
        val inflater = LayoutInflater.from(activity)
        for ((index, choice) in choices.withIndex()) {
            val entry = inflater.inflate(R.layout.settings_option_list_entry, list, false)
            entry.findViewById<TextView>(R.id.settings_option_list_value).text = activity.optionValue(option, choice)
            if (index == current) entry.findViewById<View>(R.id.settings_option_list_check).visibility = View.VISIBLE
            entry.setOnFocusChangeListener { _, focused -> if (focused) change(choice) }
            entry.setOnClickListener {
                kept = true
                window.dismiss()
            }
            list.addView(entry)
        }
        list.styleAll(roles)
        list.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val width = maxOf(list.measuredWidth, resources.getDimensionPixelSize(R.dimen.settings_option_list_min_width), reach(row, options))
        // Focusable, with a background: it takes the keys, Back closing it, on every API level.
        window = PopupWindow(list, width, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(roundRect(roles.surfaceHigh, resources.getDimension(R.dimen.settings_option_list_corner)))
            elevation = resources.getDimension(R.dimen.settings_option_list_elevation)
            animationStyle = 0
            setOnDismissListener(this@OptionList)
        }

        showBeside(row, list, width, padding, current)
        list.getChildAt(current)?.requestFocus()
    }

    /** How wide a list beside [row] must be to reach [options]' end edge: 0 for a row at their end. */
    private fun reach(row: View, options: View): Int {
        val rowAt = IntArray(2).also(row::getLocationInWindow)
        val optionsAt = IntArray(2).also(options::getLocationInWindow)
        val gap = row.resources.getDimensionPixelSize(R.dimen.settings_option_list_gap)
        val reach = if (row.layoutDirection == View.LAYOUT_DIRECTION_RTL) rowAt[0] - gap - optionsAt[0]
        else optionsAt[0] + options.width - (rowAt[0] + row.width + gap)
        return reach.coerceAtLeast(0)
    }

    /**
     * Shows [list], [width] wide, beside [row] at its end edge (the left one in a right-to-left
     * language), a gap from it, its entry [current] level with [row], inside the TV's overscan
     * margin. LEFT, not START: the place is in pixels from the window's left, worked out for
     * either direction.
     */
    @SuppressLint("RtlHardcoded")
    private fun showBeside(row: View, list: View, width: Int, padding: Int, current: Int) {
        val resources = row.resources
        val rowAt = IntArray(2)
        row.getLocationInWindow(rowAt)
        val gap = resources.getDimensionPixelSize(R.dimen.settings_option_list_gap)
        val entryHeight = resources.getDimensionPixelSize(R.dimen.settings_option_list_entry_height)
        val rtl = row.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val x = if (rtl) rowAt[0] - gap - width else rowAt[0] + row.width + gap
        val y = rowAt[1] + (row.height - entryHeight) / 2 - padding - current * entryHeight
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
        closed()
    }
}

/** [appearance] with each value [context] offers, in order. */
private fun <T> AppearanceOption<T>.choices(appearance: Appearance, context: OptionContext): List<Appearance> =
    values(context).map { set(appearance, it) }

/** Where [appearance]'s value is among those [context] offers; -1 if it isn't. */
private fun <T> AppearanceOption<T>.indexIn(appearance: Appearance, context: OptionContext): Int =
    values(context).indexOf(get(appearance))
