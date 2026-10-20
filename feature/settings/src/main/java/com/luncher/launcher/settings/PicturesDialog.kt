package com.luncher.launcher.settings

import android.app.Activity
import android.app.Dialog
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.launcher.ui.color
import com.luncher.launcher.ui.R as UiR

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
        for ((label, action) in entries) {
            addEntry(label) {
                dismiss()
                action()
            }
        }
        fitToScreen()
        findViewById<LinearLayout>(R.id.settings_pictures_dialog_entries).getChildAt(0)?.requestFocus()
    }
}

/**
 * The dialog that renames a picture: a field with its [name], focused, the cursor at its end (where
 * a TV's keyboard puts it anyway as it starts), the keyboard up; then Rename and Cancel. Rename,
 * or the keyboard's Done, gives what's typed to [rename], which renames the picture and answers
 * null, closing the dialog, or answers why it can't, shown below the field in the accent color.
 * Cancel, or Back, closes it.
 */
internal class RenameDialog(
    activity: Activity,
    name: String,
    rename: (String) -> CharSequence?,
) : Dialog(activity, R.style.Theme_Luncher_Settings_Pictures_Dialog) {

    init {
        setContentView(R.layout.settings_pictures_dialog)
        val field = findViewById<EditText>(R.id.settings_pictures_dialog_field)
        findViewById<TextView>(R.id.settings_pictures_dialog_title).apply {
            setText(R.string.settings_pictures_rename_title)
            labelFor = field.id
        }
        val why = findViewById<TextView>(R.id.settings_pictures_dialog_text)
        why.visibility = View.GONE
        why.setTextColor(context.color(UiR.color.accent))
        val done = {
            val refused = rename(field.text.toString())
            if (refused == null) {
                dismiss()
            } else {
                why.text = refused
                why.visibility = View.VISIBLE
            }
        }
        field.visibility = View.VISIBLE
        field.setText(name)
        field.setSelection(name.length)
        field.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) done()
            action == EditorInfo.IME_ACTION_DONE
        }
        addEntry(context.getString(R.string.settings_pictures_rename), done)
        addEntry(context.getString(R.string.settings_pictures_cancel)) { dismiss() }
        fitToScreen()
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        field.requestFocus()
    }
}

/** Adds an entry labelled [label] to the dialog's entries; OK on it does [action]. */
private fun Dialog.addEntry(label: CharSequence, action: () -> Unit) {
    val list = findViewById<LinearLayout>(R.id.settings_pictures_dialog_entries)
    val entry = layoutInflater.inflate(R.layout.settings_pictures_entry, list, false) as TextView
    entry.text = label
    entry.setOnClickListener { action() }
    list.addView(entry)
}

/** No wider than the screen leaves beside its margins, on a small or low-density one. */
private fun Dialog.fitToScreen() {
    val margin = context.resources.getDimensionPixelSize(R.dimen.settings_pictures_dialog_margin)
    val panel = findViewById<View>(R.id.settings_pictures_dialog_panel)
    panel.layoutParams.width = minOf(
        context.resources.getDimensionPixelSize(R.dimen.settings_pictures_dialog_width),
        context.resources.displayMetrics.widthPixels - 2 * margin,
    )
}
