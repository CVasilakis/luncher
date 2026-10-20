package com.luncher.launcher.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.AppearanceGroup
import com.luncher.domain.appearance.OptionContext
import com.luncher.domain.appearance.appearanceOptions

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
 * A group edited apart (the background's) is one entry, its picture's name beside it: OK opens
 * its own screen ([BackgroundActivity]), where the picture and its options are saved together;
 * back here, the editor reads the appearance again.
 *
 * Which options there are, in which groups, their values and what a step does are the domain's;
 * what each one is called is decided in OptionLabels.kt, per option.
 */
class AppearanceActivity : Activity() {

    private val settings by lazy { graph.appearanceSettings }
    private val pictures by lazy { graph.pictures }

    /** The entries of the groups edited apart, which show the background's picture. */
    private val apartValues = mutableListOf<TextView>()
    private lateinit var appearance: Appearance
    private lateinit var preview: AppearancePreviewView
    private val rows = mutableListOf<OptionRow>()
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
            if (group.editedApart) {
                apartEntry(options, group)
                continue
            }
            val header = layoutInflater.inflate(R.layout.settings_appearance_group, options, false)
            options.addView(header)
            val rows = group.options.map { option ->
                val view = layoutInflater.inflate(R.layout.settings_appearance_option, options, false)
                options.addView(view)
                // Nothing here depends on a context: the background's options are edited apart.
                OptionRow(option, view, { appearance }, { OptionContext.UNKNOWN }, ::change)
            }
            this.rows += rows
            groups += Group(header, topicTitle(group.topic), rows.map { it.view })
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

    // Back from the background editor, which may have saved a new background, or renamed its
    // picture. In onResume: it can close before this screen was ever stopped.
    override fun onResume() {
        super.onResume()
        val stored = settings.read()
        if (stored != appearance) {
            appearance = stored
            show()
        } else {
            showPictureName()
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

    /** [group]'s one entry, named after its topic, its picture's name beside it: OK opens its own screen. */
    private fun apartEntry(options: LinearLayout, group: AppearanceGroup) {
        val view = layoutInflater.inflate(R.layout.settings_appearance_apart, options, false)
        options.addView(view)
        view.findViewById<TextView>(R.id.settings_apart_title).setText(topicTitle(group.topic))
        view.setOnClickListener { startActivity(Intent(this, BackgroundActivity::class.java)) }
        apartValues += view.findViewById<TextView>(R.id.settings_apart_value)
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
        showPictureName()
        preview.appearance = appearance
    }

    /** The background's picture's name, as the library has it now, beside the Background entry. */
    private fun showPictureName() {
        val picture = appearance.background.picture
        val name = picture?.let { id -> pictures.read().firstOrNull { it.id == id } }?.name
        for (row in apartValues) {
            row.text = when {
                picture == null -> getString(R.string.settings_appearance_no_picture)
                name.isNullOrEmpty() -> getString(R.string.settings_appearance_picture)
                else -> name
            }
        }
    }

    /** A group's entry, which shows or hides its options' [rows] below it. Hidden at first. */
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
}
