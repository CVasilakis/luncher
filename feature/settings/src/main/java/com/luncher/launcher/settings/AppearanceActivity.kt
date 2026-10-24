package com.luncher.launcher.settings

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.AppearanceTopic
import com.luncher.domain.appearance.OptionContext
import com.luncher.domain.appearance.appearanceOptions
import com.luncher.launcher.ui.Badge
import com.luncher.launcher.ui.DialogButton
import com.luncher.launcher.ui.MessageDialog
import com.luncher.launcher.ui.styleScreen
import com.luncher.launcher.ui.R as UiR

/**
 * The editor of the home screen's appearance and Luncher's theme, full-screen: the groups of
 * options of the domain's [appearanceOptions] at the end (the start in a right-to-left language,
 * mirrored like the other panels), in their container, and at the start the title, a preview of
 * the result, and below it Background and Reset to defaults. OK on a group shows its options below
 * it, and hides those of the group that showed; all are hidden at first. Left and Right on an
 * option step through its values, toward the arrow on that side, around from the last to the
 * first; each change is stored at once, the theme's restyling the editor in place, and the home
 * screen shows it when it comes back. An option that changes nothing with the others as they are
 * is dimmed, and the focus passes it by. Reset to defaults asks, Cancel focused, then puts every
 * option back to its default. Back closes it, as any activity.
 *
 * The group edited apart (the background's) is the Background button: it opens its own screen
 * ([BackgroundActivity]), where the picture and its options are saved together; back here, the
 * editor reads the appearance again.
 *
 * Which options there are, in which groups, their values and what a step does are the domain's;
 * what each one is called is decided in OptionLabels.kt, per option.
 */
class AppearanceActivity : Activity() {

    private val settings by lazy { graph.appearanceSettings }
    private val theme by lazy { ScreenTheme(settings) { checkNotNull(window).styleScreen(it) } }

    private lateinit var appearance: Appearance
    private lateinit var preview: AppearancePreviewView
    private val rows = mutableListOf<OptionRow>()
    private val groups = mutableListOf<Group>()

    /** The group whose options show; at most one, so the list stays short. */
    private var open: Group? = null

    /** The reset question while it shows. */
    private var dialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_appearance_activity)
        preview = findViewById(R.id.settings_appearance_preview)
        // Its width, or on a narrow screen half of it, so the preview keeps the other half.
        findViewById<View>(R.id.settings_appearance_end).layoutParams.width = minOf(
            resources.getDimensionPixelSize(R.dimen.settings_appearance_options_width),
            resources.displayMetrics.widthPixels / 2,
        )
        val options = findViewById<LinearLayout>(R.id.settings_appearance_options)
        for (group in appearanceOptions().groups) {
            if (group.editedApart) continue   // the Background button
            val header = layoutInflater.inflate(R.layout.settings_appearance_group, options, false)
            options.addView(header)
            val rows = group.options.map { option ->
                val view = layoutInflater.inflate(R.layout.settings_appearance_option, options, false)
                options.addView(view)
                // Nothing here depends on a context: the background's options are edited apart.
                OptionRow(option, view, { appearance }, { OptionContext.UNKNOWN }, ::change)
            }
            this.rows += rows
            groups += Group(header, group.topic, rows.map { it.view })
        }
        findViewById<View>(R.id.settings_appearance_background).setOnClickListener {
            startActivity(Intent(this, BackgroundActivity::class.java))
        }
        findViewById<View>(R.id.settings_appearance_reset).setOnClickListener { confirmReset() }

        appearance = settings.read()
        preview.showWallpapers(graph.wallpaper, graph.pictureImages, graph.jobs)
        show()
        window.styleScreen(theme.roles)
        groups.first().header.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        theme.start()
    }

    // Back from the background editor, which may have saved a new background. In onResume: it can
    // close before this screen was ever stopped.
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
        theme.stop()
        preview.stopWallpaper()
    }

    override fun onDestroy() {
        super.onDestroy()
        dialog?.dismiss()
    }

    /** Shows [group]'s options, and hides any other group's; or hides [group]'s if they show. */
    private fun toggle(group: Group) {
        open = if (open == group) null else group
        for (each in groups) each.show(open == each)
    }

    /**
     * Asks before putting every option back to its default, Cancel focused, saying whether the
     * home screen loses its picture; nothing to ask while every option is at its default already.
     */
    private fun confirmReset() {
        if (appearance == Appearance()) return
        val text = if (appearance.background.picture != null) R.string.settings_appearance_reset_text_background else R.string.settings_appearance_reset_text
        dialog?.dismiss()
        dialog = MessageDialog(
            this,
            theme.roles,
            getString(R.string.settings_appearance_reset_question),
            getString(text),
            // Cancel first, so a repeated OK doesn't reset.
            listOf(
                DialogButton(getString(R.string.settings_pictures_cancel), DialogButton.Kind.TONAL) {},
                DialogButton(getString(R.string.settings_appearance_reset_confirm), DialogButton.Kind.DANGER, UiR.drawable.icon_reset) { change(Appearance()) },
            ),
            Badge.Kind.DANGER to UiR.drawable.icon_reset,
        ).apply { show() }
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

    /** A group's entry, which shows or hides its options' [rows] below it. Hidden at first. */
    private inner class Group(val header: View, topic: AppearanceTopic, private val rows: List<View>) {

        private val arrow = header.findViewById<ImageView>(R.id.settings_group_arrow)

        init {
            header.findViewById<TextView>(R.id.settings_group_title).setText(topicTitle(topic))
            header.findViewById<ImageView>(R.id.settings_group_icon).setImageResource(topicIcon(topic))
            header.setOnClickListener { toggle(this) }
            show(false)
        }

        fun show(open: Boolean) {
            for (row in rows) row.visibility = if (open) View.VISIBLE else View.GONE
            // Down at the options to show, up while they show.
            arrow.setImageResource(if (open) UiR.drawable.icon_caret_up else UiR.drawable.icon_caret_down)
        }
    }
}
