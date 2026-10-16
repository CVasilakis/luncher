package com.luncher.launcher.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.AppearanceOption
import com.luncher.domain.appearance.AppearanceTopic
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.HorizontalAlignment
import com.luncher.domain.appearance.Side
import com.luncher.domain.appearance.VerticalAlignment
import com.luncher.domain.appearance.appearanceOptions
import com.luncher.domain.appearance.step
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.PicturePosition
import com.luncher.launcher.pictures.ChooseWallpaperActivity

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
 * The background's group starts with its picture: its name, and OK on it opens the picture
 * library to choose one ([ChooseWallpaperActivity]), which stores it; back here, the editor reads
 * the appearance again.
 *
 * Which options there are, in which groups, their values and what a step does are the domain's;
 * what each one is called is decided here, per option, in [label] and [valueLabel].
 */
class AppearanceActivity : Activity() {

    private val settings by lazy { graph.appearanceSettings }
    private val pictures by lazy { graph.pictures }
    private val pictureRows = mutableListOf<TextView>()
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
            val picture = if (group.choosesPicture) pictureRow(options) else null
            val rows = group.options.map { option ->
                val view = layoutInflater.inflate(R.layout.settings_appearance_option, options, false)
                options.addView(view)
                Row(option, view).also { view.setOnKeyListener(it) }
            }
            this.rows += rows
            groups += Group(header, topic(group.topic), listOfNotNull(picture) + rows.map { it.view })
        }
        val reset = layoutInflater.inflate(R.layout.settings_entry, options, false) as TextView
        reset.setText(R.string.settings_appearance_reset)
        reset.setOnClickListener { change(Appearance()) }
        options.addView(reset)

        appearance = settings.read()
        preview.showWallpapers(graph.pictureImages, graph.jobs)
        show()
        groups.first().header.requestFocus()
    }

    // Back from choosing the wallpaper, which stored it. In onResume: the chooser can close
    // before this screen was ever stopped.
    override fun onResume() {
        super.onResume()
        val stored = settings.read()
        if (stored != appearance) {
            appearance = stored
            show()
        }
    }

    override fun onRestart() {
        super.onRestart()
        preview.resumeWallpaper()
    }

    override fun onStop() {
        super.onStop()
        preview.stopWallpaper()
    }

    /** The background's picture, a row like an option's: OK opens the picture library to choose it. */
    private fun pictureRow(options: LinearLayout): View {
        val view = layoutInflater.inflate(R.layout.settings_appearance_picture, options, false)
        options.addView(view)
        view.setOnClickListener { startActivity(Intent(this, ChooseWallpaperActivity::class.java)) }
        pictureRows += view.findViewById<TextView>(R.id.settings_picture_value)
        return view
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
        val picture = appearance.background.picture
        val name = picture?.let { id -> pictures.read().firstOrNull { it.id == id } }?.name
        for (row in pictureRows) {
            row.text = when {
                picture == null -> getString(R.string.settings_appearance_no_picture)
                name.isNullOrEmpty() -> getString(R.string.settings_appearance_picture)
                else -> name
            }
        }
        preview.appearance = appearance
    }

    private fun topic(topic: AppearanceTopic): Int = when (topic) {
        AppearanceTopic.TILES -> R.string.settings_appearance_tiles
        AppearanceTopic.TOP_BAR -> R.string.settings_appearance_top_bar
        AppearanceTopic.BACKGROUND -> R.string.settings_appearance_background
    }

    private fun label(option: AppearanceOption<*>): Int = when (option) {
        AppearanceOption.VerticalAlignment -> R.string.settings_appearance_vertical_alignment
        AppearanceOption.HorizontalAlignment -> R.string.settings_appearance_horizontal_alignment
        AppearanceOption.FullBottomRow -> R.string.settings_appearance_full_bottom_row
        AppearanceOption.CornerRadius -> R.string.settings_appearance_corner_radius
        AppearanceOption.ShowTime -> R.string.settings_appearance_time
        AppearanceOption.ShowDate -> R.string.settings_appearance_date
        AppearanceOption.ClockSide -> R.string.settings_appearance_clock_side
        AppearanceOption.TimeSide -> R.string.settings_appearance_time_side
        AppearanceOption.BackgroundFit -> R.string.settings_appearance_fit
        AppearanceOption.BackgroundPosition -> R.string.settings_appearance_position
        AppearanceOption.BackgroundDim -> R.string.settings_appearance_dim
        AppearanceOption.BackgroundBlur -> R.string.settings_appearance_blur
    }

    /** What [option]'s value in [appearance] is called. */
    private fun valueLabel(option: AppearanceOption<*>, appearance: Appearance): String = when (option) {
        AppearanceOption.VerticalAlignment -> getString(vertical(AppearanceOption.VerticalAlignment.get(appearance)))
        AppearanceOption.HorizontalAlignment -> getString(horizontal(AppearanceOption.HorizontalAlignment.get(appearance)))
        AppearanceOption.FullBottomRow -> getString(onOff(AppearanceOption.FullBottomRow.get(appearance)))
        AppearanceOption.CornerRadius -> when (val corners = AppearanceOption.CornerRadius.get(appearance)) {
            is Corners.Radius -> getString(R.string.settings_appearance_radius_dp, corners.dp)
            Corners.Pill -> getString(R.string.settings_appearance_pill)
        }
        AppearanceOption.ShowTime -> getString(shown(AppearanceOption.ShowTime.get(appearance)))
        AppearanceOption.ShowDate -> getString(shown(AppearanceOption.ShowDate.get(appearance)))
        AppearanceOption.ClockSide -> getString(side(AppearanceOption.ClockSide.get(appearance)))
        AppearanceOption.TimeSide -> getString(order(AppearanceOption.TimeSide.get(appearance)))
        AppearanceOption.BackgroundFit -> getString(fit(AppearanceOption.BackgroundFit.get(appearance)))
        AppearanceOption.BackgroundPosition -> getString(position(AppearanceOption.BackgroundPosition.get(appearance)))
        AppearanceOption.BackgroundDim -> getString(R.string.settings_appearance_percent, AppearanceOption.BackgroundDim.get(appearance))
        AppearanceOption.BackgroundBlur -> getString(blur(AppearanceOption.BackgroundBlur.get(appearance)))
    }

    private fun fit(fit: Fit): Int = when (fit) {
        Fit.FILL -> R.string.settings_appearance_fill
        Fit.STRETCH -> R.string.settings_appearance_stretch
    }

    private fun position(position: PicturePosition): Int = when (position) {
        PicturePosition.CENTER -> R.string.settings_appearance_center
        PicturePosition.TOP -> R.string.settings_appearance_top
        PicturePosition.BOTTOM -> R.string.settings_appearance_bottom
        PicturePosition.LEFT -> R.string.settings_appearance_left
        PicturePosition.RIGHT -> R.string.settings_appearance_right
    }

    private fun blur(blur: Blur): Int = when (blur) {
        Blur.OFF -> R.string.settings_appearance_off
        Blur.LIGHT -> R.string.settings_appearance_light
        Blur.STRONG -> R.string.settings_appearance_strong
    }

    private fun vertical(alignment: VerticalAlignment): Int = when (alignment) {
        VerticalAlignment.TOP -> R.string.settings_appearance_top
        VerticalAlignment.CENTER -> R.string.settings_appearance_center
        VerticalAlignment.BOTTOM -> R.string.settings_appearance_bottom
    }

    private fun horizontal(alignment: HorizontalAlignment): Int = when (alignment) {
        HorizontalAlignment.LEFT -> R.string.settings_appearance_left
        HorizontalAlignment.CENTER -> R.string.settings_appearance_center
        HorizontalAlignment.RIGHT -> R.string.settings_appearance_right
    }

    private fun onOff(on: Boolean): Int = if (on) R.string.settings_appearance_on else R.string.settings_appearance_off

    private fun shown(shown: Boolean): Int =
        if (shown) R.string.settings_appearance_shown else R.string.settings_appearance_hidden

    private fun side(side: Side): Int = when (side) {
        Side.LEFT -> R.string.settings_appearance_left
        Side.RIGHT -> R.string.settings_appearance_right
    }

    /** The time's side of the date, as the order the two read in, left to right. */
    private fun order(timeSide: Side): Int = when (timeSide) {
        Side.LEFT -> R.string.settings_appearance_time_first
        Side.RIGHT -> R.string.settings_appearance_date_first
    }

    /** A group's entry, which shows or hides its options' [rows] below it (a picture's too). Hidden at first. */
    private inner class Group(val header: View, title: Int, private val rows: List<View>) {

        private val arrow = header.findViewById<View>(R.id.settings_group_arrow)

        init {
            header.findViewById<TextView>(R.id.settings_group_title).setText(title)
            header.setOnClickListener { toggle(this) }
            show(false)
        }

        fun show(open: Boolean) {
            for (row in rows) row.visibility = if (open) View.VISIBLE else View.GONE
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
