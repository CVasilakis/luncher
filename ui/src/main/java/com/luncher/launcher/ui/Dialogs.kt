package com.luncher.launcher.ui

import android.app.Activity
import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.luncher.domain.appearance.ColorRoles

/** A dialog's button: its [label], its [kind], an [icon] before it (0: none), and what OK on it does. */
class DialogButton(val label: CharSequence, val kind: Kind, val icon: Int = 0, val action: () -> Unit) {
    enum class Kind { FILLED, TONAL, DANGER }
}

/** An entry of a menu dialog ([MenuDialog]): its [label], its [icon], in the error color if it [deletes], and what OK on it does. */
class MenuAction(val label: CharSequence, val icon: Int, val deletes: Boolean = false, val action: () -> Unit)

/**
 * A dialog over a screen of the menus and settings (a question, a message), a floating panel like
 * the settings panel, in the theme's colors ([roles]): its [mark] beside its [title] (a
 * [Badge.Kind] and an icon; none for none), a [text] below, starting below the mark, and [buttons]
 * (what OK on one does also closes the dialog), side by side, the first one focused. Back closes
 * it, as any dialog.
 */
class MessageDialog(
    activity: Activity,
    roles: ColorRoles,
    title: CharSequence,
    text: CharSequence?,
    buttons: List<DialogButton>,
    mark: Pair<Badge.Kind, Int>? = null,
) : Dialog(activity, R.style.Theme_Luncher_Dialog) {

    init {
        setContentView(R.layout.dialog)
        findViewById<TextView>(R.id.dialog_title).text = title
        findViewById<TextView>(R.id.dialog_text).apply {
            this.text = text
            visibility = if (text == null) View.GONE else View.VISIBLE
        }
        if (mark != null) {
            findViewById<Badge>(R.id.dialog_mark).apply {
                kind = mark.first
                setImageResource(mark.second)
                visibility = View.VISIBLE
            }
        }
        for (button in buttons) {
            addButton(button.label, button.kind, button.icon) {
                dismiss()
                button.action()
            }
        }
        fitToScreen()
        checkNotNull(window).stylePanel(roles)
        findViewById<View>(R.id.dialog_entries).let { (it as ViewGroup).getChildAt(0)?.requestFocus() }
    }
}

/**
 * A menu of actions on one thing (a picture's), as a dialog: its name as the [title], a line
 * below ([text]), then an entry for each of [actions], one below the other, the first one
 * focused. OK on one closes the menu and does it; Back closes it.
 */
class MenuDialog(
    activity: Activity,
    roles: ColorRoles,
    title: CharSequence,
    text: CharSequence?,
    actions: List<MenuAction>,
) : Dialog(activity, R.style.Theme_Luncher_Dialog) {

    init {
        setContentView(R.layout.dialog)
        findViewById<TextView>(R.id.dialog_title).text = title
        findViewById<TextView>(R.id.dialog_text).apply {
            this.text = text
            visibility = if (text == null) View.GONE else View.VISIBLE
        }
        val list = findViewById<EntriesRow>(R.id.dialog_entries)
        list.stacked = true
        list.layoutParams = (list.layoutParams as ViewGroup.MarginLayoutParams).apply {
            topMargin = context.resources.getDimensionPixelSize(R.dimen.title_gap)
        }
        for (action in actions) {
            val entry = layoutInflater.inflate(R.layout.dialog_entry, list, false) as ThemedText
            entry.text = action.label
            entry.setCompoundDrawablesRelativeWithIntrinsicBounds(action.icon, 0, 0, 0)
            if (action.deletes) entry.changeRole(ThemedText.ERROR)
            entry.setOnClickListener {
                dismiss()
                action.action()
            }
            list.addView(entry)
        }
        fitToScreen()
        checkNotNull(window).stylePanel(roles)
        list.getChildAt(0)?.requestFocus()
    }
}

/**
 * A dialog that asks for a new name: its [title], a field with the [name], focused, the cursor at
 * its end (where a TV's keyboard puts it anyway as it starts), the keyboard up; then [cancel] and
 * [confirm]. [confirm], or the keyboard's Done, gives what's typed to [rename], which keeps the
 * name and answers null, closing the dialog, or answers why it can't, shown below the field in
 * the error color, the field outlined in it. [cancel], or Back, closes it.
 */
class NameDialog(
    activity: Activity,
    roles: ColorRoles,
    title: CharSequence,
    name: String,
    cancel: CharSequence,
    confirm: CharSequence,
    rename: (String) -> CharSequence?,
) : Dialog(activity, R.style.Theme_Luncher_Dialog) {

    init {
        setContentView(R.layout.dialog)
        val field = findViewById<NameField>(R.id.dialog_field)
        findViewById<TextView>(R.id.dialog_title).apply {
            text = title
            labelFor = field.id
        }
        val why = findViewById<ThemedText>(R.id.dialog_text)
        why.visibility = View.GONE
        why.changeRole(ThemedText.ERROR)
        why.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.icon_warning, 0, 0, 0)
        why.compoundDrawablePadding = context.resources.getDimensionPixelSize(R.dimen.icon_gap)
        val done = {
            val refused = rename(field.text.toString())
            if (refused == null) {
                dismiss()
            } else {
                why.text = refused
                why.visibility = View.VISIBLE
                field.refused = true
            }
        }
        field.visibility = View.VISIBLE
        field.setText(name)
        field.setSelection(name.length)
        field.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) done()
            action == EditorInfo.IME_ACTION_DONE
        }
        addButton(cancel, DialogButton.Kind.TONAL) { dismiss() }
        addButton(confirm, DialogButton.Kind.FILLED, R.drawable.icon_check, done)
        fitToScreen()
        checkNotNull(window).stylePanel(roles)
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        field.requestFocus()
    }
}

/** Adds a button labelled [label], of [kind], [icon] before it, to the dialog's buttons; OK on it does [action]. */
private fun Dialog.addButton(label: CharSequence, kind: DialogButton.Kind, icon: Int = 0, action: () -> Unit) {
    val list = findViewById<EntriesRow>(R.id.dialog_entries)
    val button = layoutInflater.inflate(R.layout.dialog_button, list, false) as PillButton
    button.text = label
    button.kind = when (kind) {
        DialogButton.Kind.FILLED -> PillButton.FILLED
        DialogButton.Kind.TONAL -> PillButton.TONAL
        DialogButton.Kind.DANGER -> PillButton.DANGER
    }
    button.setIcon(icon)
    button.setOnClickListener { action() }
    list.addView(button)
}

/** No wider than the screen leaves beside its margins, on a small or low-density one. */
private fun Dialog.fitToScreen() {
    val margin = context.resources.getDimensionPixelSize(R.dimen.dialog_margin)
    val panel = findViewById<View>(R.id.dialog_panel)
    panel.layoutParams.width = minOf(
        context.resources.getDimensionPixelSize(R.dimen.dialog_width),
        context.resources.displayMetrics.widthPixels - 2 * margin,
    )
}
