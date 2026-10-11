package com.luncher.launcher.settings

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.AppearanceOption
import com.luncher.domain.appearance.AppearanceTopic
import com.luncher.domain.appearance.Side
import com.luncher.domain.appearance.TilePlacement
import com.luncher.domain.appearance.appearanceOptions
import com.luncher.domain.appearance.canStep
import com.luncher.domain.appearance.step

/**
 * The editor of the home screen's appearance, full-screen: the options of the domain's
 * [appearanceOptions] at the right, each with its value, and a preview of the result at the left.
 * Left and Right on an option step through its values, stopping at the first and the last; each
 * change is stored at once, and the home screen shows it when it comes back. An option that
 * changes nothing with the others as they are is dimmed, and the focus passes it by. OK on the
 * last entry puts every option back to its default. Back closes it, as any activity.
 *
 * Which options there are, their values and what a step does are the domain's; what each one is
 * called is decided here, per option, in [label] and [valueLabel].
 */
class AppearanceActivity : Activity() {

    private val settings by lazy { graph.appearanceSettings }
    private lateinit var appearance: Appearance
    private lateinit var preview: AppearancePreviewView
    private val rows = mutableListOf<Row>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_appearance_activity)
        preview = findViewById(R.id.settings_appearance_preview)
        // Its width, or on a narrow screen half of it, so the preview keeps the other half.
        val scroll = findViewById<View>(R.id.settings_appearance_scroll)
        scroll.layoutParams.width = minOf(
            resources.getDimensionPixelSize(R.dimen.settings_appearance_options_width),
            resources.displayMetrics.widthPixels / 2,
        )
        val options = findViewById<LinearLayout>(R.id.settings_appearance_options)
        for (group in appearanceOptions().groups) {
            val title = layoutInflater.inflate(R.layout.settings_appearance_group, options, false) as TextView
            title.setText(topic(group.topic))
            options.addView(title)
            for (option in group.options) {
                val view = layoutInflater.inflate(R.layout.settings_appearance_option, options, false)
                rows += Row(option, view).also { view.setOnKeyListener(it) }
                options.addView(view)
            }
        }
        val reset = layoutInflater.inflate(R.layout.settings_entry, options, false) as TextView
        reset.setText(R.string.settings_appearance_reset)
        reset.layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        reset.setOnClickListener { change(Appearance()) }
        (reset.layoutParams as LinearLayout.LayoutParams).topMargin = resources.getDimensionPixelSize(R.dimen.settings_group_gap)
        options.addView(reset)

        appearance = settings.read()
        show()
        rows.first().view.requestFocus()
    }

    private fun change(appearance: Appearance) {
        if (appearance == this.appearance) return
        this.appearance = appearance
        settings.write(appearance)
        show()
    }

    private fun show() {
        for (row in rows) row.show()
        preview.appearance = appearance
    }

    private fun topic(topic: AppearanceTopic): Int = when (topic) {
        AppearanceTopic.TILES -> R.string.settings_appearance_tiles
        AppearanceTopic.TOP_BAR -> R.string.settings_appearance_top_bar
    }

    private fun label(option: AppearanceOption<*>): Int = when (option) {
        AppearanceOption.Placement -> R.string.settings_appearance_placement
        AppearanceOption.FullBottomRow -> R.string.settings_appearance_full_bottom_row
        AppearanceOption.CornerRadius -> R.string.settings_appearance_corner_radius
        AppearanceOption.ClockSide -> R.string.settings_appearance_clock_side
        AppearanceOption.TimeSide -> R.string.settings_appearance_time_side
    }

    /** What [option]'s value in [appearance] is called. */
    private fun valueLabel(option: AppearanceOption<*>, appearance: Appearance): String = when (option) {
        AppearanceOption.Placement -> getString(placement(AppearanceOption.Placement.get(appearance)))
        AppearanceOption.FullBottomRow -> getString(onOff(AppearanceOption.FullBottomRow.get(appearance)))
        AppearanceOption.CornerRadius ->
            getString(R.string.settings_appearance_radius_dp, AppearanceOption.CornerRadius.get(appearance))
        AppearanceOption.ClockSide -> getString(side(AppearanceOption.ClockSide.get(appearance)))
        AppearanceOption.TimeSide -> getString(side(AppearanceOption.TimeSide.get(appearance)))
    }

    private fun placement(placement: TilePlacement): Int = when (placement) {
        TilePlacement.TOP_LEFT -> R.string.settings_appearance_top_left
        TilePlacement.TOP_RIGHT -> R.string.settings_appearance_top_right
        TilePlacement.BOTTOM_LEFT -> R.string.settings_appearance_bottom_left
        TilePlacement.BOTTOM_RIGHT -> R.string.settings_appearance_bottom_right
    }

    private fun onOff(on: Boolean): Int = if (on) R.string.settings_appearance_on else R.string.settings_appearance_off

    private fun side(side: Side): Int = when (side) {
        Side.LEFT -> R.string.settings_appearance_left
        Side.RIGHT -> R.string.settings_appearance_right
    }

    /** An option's row: shows its value, and steps it on Left and Right while it has the focus. */
    private inner class Row(val option: AppearanceOption<*>, val view: View) : View.OnKeyListener {

        private val value = view.findViewById<TextView>(R.id.settings_option_value)
        private val previous = view.findViewById<View>(R.id.settings_option_previous)
        private val next = view.findViewById<View>(R.id.settings_option_next)

        init {
            view.findViewById<TextView>(R.id.settings_option_label).setText(label(option))
        }

        fun show() {
            val applies = option.appliesTo(appearance)
            value.text = valueLabel(option, appearance)
            previous.alpha = if (applies && option.canStep(appearance, forward = false)) 1f else DIMMED
            next.alpha = if (applies && option.canStep(appearance, forward = true)) 1f else DIMMED
            view.alpha = if (applies) 1f else DIMMED
            view.isFocusable = applies
            view.isFocusableInTouchMode = applies
        }

        // Left is the previous value and Right the next, in every language, as the editor
        // doesn't mirror. Taken at the first and last value too, so the focus stays here.
        override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
            val forward = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> false
                KeyEvent.KEYCODE_DPAD_RIGHT -> true
                else -> return false
            }
            if (event.action == KeyEvent.ACTION_DOWN) change(option.step(appearance, forward))
            return true
        }
    }

    private companion object {
        const val DIMMED = 0.4f
    }
}
