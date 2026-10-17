package com.luncher.launcher.settings

import android.app.Activity
import android.app.Dialog
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A dialog over a pictures' screen, a floating panel like the settings panel: a [title], a [text],
 * and [entries] (a label each, and what OK on it does, which also closes the dialog), the first
 * one focused. Back closes it, as any dialog.
 */
internal class PicturesDialog(
    activity: Activity,
    title: CharSequence,
    text: CharSequence?,
    entries: List<Pair<CharSequence, () -> Unit>>,
) : Dialog(activity, R.style.Theme_Luncher_Settings_Pictures_Dialog) {

    init {
        setContentView(R.layout.settings_pictures_dialog)
        findViewById<TextView>(R.id.settings_pictures_dialog_title).text = title
        findViewById<TextView>(R.id.settings_pictures_dialog_text).apply {
            this.text = text
            visibility = if (text == null) View.GONE else View.VISIBLE
        }
        val list = findViewById<LinearLayout>(R.id.settings_pictures_dialog_entries)
        for ((label, action) in entries) {
            val entry = layoutInflater.inflate(R.layout.settings_pictures_entry, list, false) as TextView
            entry.text = label
            entry.setOnClickListener {
                dismiss()
                action()
            }
            list.addView(entry)
        }
        // No wider than the screen leaves beside its margins, on a small or low-density one.
        val margin = context.resources.getDimensionPixelSize(R.dimen.settings_pictures_dialog_margin)
        val panel = findViewById<View>(R.id.settings_pictures_dialog_panel)
        panel.layoutParams.width = minOf(
            context.resources.getDimensionPixelSize(R.dimen.settings_pictures_dialog_width),
            context.resources.displayMetrics.widthPixels - 2 * margin,
        )
        list.getChildAt(0)?.requestFocus()
    }
}
