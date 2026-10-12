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
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.Side
import com.luncher.domain.appearance.TilePlacement
import com.luncher.domain.appearance.appearanceOptions
import com.luncher.domain.appearance.step

/**
 * The editor of the home screen's appearance, full-screen: the groups of options of the domain's
 * [appearanceOptions] at the right (the left in a right-to-left language, mirrored like the other
 * panels), and the title and a preview of the result beside them. OK on a group shows its options
 * below it, and hides those of the group that showed; all are hidden at first. Left and Right on
 * an option step through its values, toward the arrow on that side, around from the last to the
 * first; each change is stored at once, and the home screen shows it when it comes back. An
 * option that changes nothing with the others as they are is dimmed, and the focus passes it by.
 * OK on the last entry puts every option back to its default. Back closes it, as any activity.
 *
 * Which options there are, in which groups, their values and what a step does are the domain's;
 * what each one is called is decided here, per option, in [label] and [valueLabel].
 */
class AppearanceActivity : Activity() {

    private val settings by lazy { graph.appearanceSettings }
    private lateinit var appearance: Appearance
    private lateinit var preview: AppearancePreviewView
    private val rows = mutableListOf<Row>()
    private val groups = mutableListOf<Group>()

    /** The group whose options show; at most one, so the list stays short. */
    private var open: Group? = null

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
            val header = layoutInflater.inflate(R.layout.settings_appearance_group, options, false)
            options.addView(header)
            val rows = group.options.map { option ->
                val view = layoutInflater.inflate(R.layout.settings_appearance_option, options, false)
                options.addView(view)
                Row(option, view).also { view.setOnKeyListener(it) }
            }
            this.rows += rows
            groups += Group(header, topic(group.topic), rows)
        }
        val reset = layoutInflater.inflate(R.layout.settings_entry, options, false) as TextView
        reset.setText(R.string.settings_appearance_reset)
        reset.setOnClickListener { change(Appearance()) }
        options.addView(reset)

        appearance = settings.read()
        show()
        groups.first().header.requestFocus()
    }

    /** Shows [group]'s options, and hides any other group's; or hides [group]'s if they show. */
    private fun toggle(group: Group) {
        open = if (open == group) null else group
        for (each in groups) each.show(open == each)
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
        AppearanceOption.CornerRadius -> when (val corners = AppearanceOption.CornerRadius.get(appearance)) {
            is Corners.Radius -> getString(R.string.settings_appearance_radius_dp, corners.dp)
            Corners.Pill -> getString(R.string.settings_appearance_pill)
        }
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

    /** A group's entry, which shows or hides its options' [rows] below it. Hidden at first. */
    private inner class Group(val header: View, title: Int, private val rows: List<Row>) {

        private val arrow = header.findViewById<View>(R.id.settings_group_arrow)

        init {
            header.findViewById<TextView>(R.id.settings_group_title).setText(title)
            header.setOnClickListener { toggle(this) }
            show(false)
        }

        fun show(open: Boolean) {
            for (row in rows) row.view.visibility = if (open) View.VISIBLE else View.GONE
            // The arrow turns to point down, at the options: the other way round when mirrored.
            val down = if (header.layoutDirection == View.LAYOUT_DIRECTION_RTL) -90f else 90f
            arrow.rotation = if (open) down else 0f
        }
    }

    /** An option's row: shows its value, and steps it on Left and Right while it has the focus. */
    private inner class Row(val option: AppearanceOption<*>, val view: View) : View.OnKeyListener {

        private val value = view.findViewById<TextView>(R.id.settings_option_value)

        init {
            view.findViewById<TextView>(R.id.settings_option_label).setText(label(option))
        }

        fun show() {
            val applies = option.appliesTo(appearance)
            value.text = valueLabel(option, appearance)
            view.alpha = if (applies) 1f else DIMMED
            view.isFocusable = applies
            view.isFocusableInTouchMode = applies
        }

        // Toward an arrow, its value: in a mirrored row the next value is to the left. Around at
        // the first and last value.
        override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
            val right = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> false
                KeyEvent.KEYCODE_DPAD_RIGHT -> true
                else -> return false
            }
            val forward = right != (v.layoutDirection == View.LAYOUT_DIRECTION_RTL)
            if (event.action == KeyEvent.ACTION_DOWN) change(option.step(appearance, forward))
            return true
        }
    }

    private companion object {
        const val DIMMED = 0.4f
    }
}
