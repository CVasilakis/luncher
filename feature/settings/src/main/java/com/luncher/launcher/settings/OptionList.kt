package com.luncher.launcher.settings

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
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
import com.luncher.launcher.ui.R as UiR

/**
 * An option's values in a list beside the [options], at their end edge, over the pictures, opened
 * by OK on its [row]: the value it has focused, checked, and level with the row, moved up or down
 * only to stay inside the TV's overscan margin. So nothing of the options, nor of the preview, is
 * covered while the user tries the values: the background editor's way to change an option, where
 * Left and Right on the row must move the focus, to the pictures beside it. The values are those
 * [context] offers. Each value the focus comes to goes to [change] at once, so the preview shows
 * it; OK on one keeps it and closes the list, and Back closes it with the value the option had
 * again, as [change] gets it. [closed] is told once it's gone. A short line in the accent joins
 * the list to the gap's side of the options, level with the row, even where the list moved up or
 * down: the row's own outline shows it open.
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
    private val link: ColorDrawable
    private val screen = row.rootView

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
        val width = maxOf(list.measuredWidth, resources.getDimensionPixelSize(R.dimen.settings_option_list_min_width))
        // Focusable, with a background: it takes the keys, Back closing it, on every API level.
        window = PopupWindow(list, width, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(roundRect(roles.surfaceHigh, resources.getDimension(R.dimen.settings_option_list_corner)))
            elevation = resources.getDimension(R.dimen.settings_option_list_elevation)
            animationStyle = 0
            setOnDismissListener(this@OptionList)
        }
        link = ColorDrawable(roles.primary)

        showBeside(row, options, list, width, padding, current)
        list.getChildAt(current)?.requestFocus()
    }

    /**
     * Shows [list], [width] wide, beside [options] at their end edge (the left one in a
     * right-to-left language), a gap from them, its entry [current] level with [row], inside the
     * TV's overscan margin. LEFT, not START: the place is in pixels from the window's left, worked
     * out for either direction.
     */
    @SuppressLint("RtlHardcoded")
    private fun showBeside(row: View, options: View, list: View, width: Int, padding: Int, current: Int) {
        val resources = row.resources
        val rowAt = IntArray(2)
        row.getLocationInWindow(rowAt)
        val optionsAt = IntArray(2)
        options.getLocationInWindow(optionsAt)
        val gap = resources.getDimensionPixelSize(R.dimen.settings_option_list_gap)
        val entryHeight = resources.getDimensionPixelSize(R.dimen.settings_option_list_entry_height)
        val rtl = row.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val x = if (rtl) optionsAt[0] - gap - width else optionsAt[0] + options.width + gap
        // The link across the gap, in the screen's overlay (the window's coordinates), under the list's window.
        val thickness = resources.getDimensionPixelSize(UiR.dimen.open_field_edge)
        val middle = rowAt[1] + row.height / 2 - thickness / 2
        if (rtl) link.setBounds(x + width, middle, optionsAt[0], middle + thickness)
        else link.setBounds(optionsAt[0] + options.width, middle, x, middle + thickness)
        screen.overlay.add(link)
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
        screen.overlay.remove(link)
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
