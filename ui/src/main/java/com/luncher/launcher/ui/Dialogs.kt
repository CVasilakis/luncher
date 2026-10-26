package com.luncher.launcher.ui

import android.app.Activity
import android.app.Dialog
import android.os.Build
import android.text.InputFilter
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.luncher.domain.appearance.ColorRoles

/**
 * A dialog's button: its [label], its [kind], an [icon] before it (0: none), whether it can be
 * chosen now ([available]; dimmed, and passed by, where it can't), and what OK on it does.
 */
class DialogButton(val label: CharSequence, val kind: Kind, val icon: Int = 0, val available: Boolean = true, val action: () -> Unit) {
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
            addButton(button.label, button.kind, button.icon, button.available) {
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
 * its end (where a TV's keyboard puts it anyway as it starts), the keyboard up, taking at most
 * [maxLength] units (0: any); a [note] below it (null: none); then [cancel], an [extra] button
 * (none for null; one not [DialogButton.available] is dimmed, and the focus passes it by) and
 * [confirm]. [confirm], or the keyboard's Done (or Enter), gives what's typed to [rename], which keeps the
 * name and answers null, closing the dialog, or answers why it can't, shown below the field in
 * place of the note, in the error color, the field outlined in it. [cancel], or Back, closes it;
 * OK on [extra] closes it and does its action. Centered; near the top while the keyboard shows,
 * over a screen that doesn't move for it (`adjustNothing`), so the keyboard covers none of it.
 */
class NameDialog(
    activity: Activity,
    roles: ColorRoles,
    title: CharSequence,
    name: String,
    cancel: CharSequence,
    confirm: CharSequence,
    note: CharSequence? = null,
    extra: DialogButton? = null,
    maxLength: Int = 0,
    rename: (String) -> CharSequence?,
) : Dialog(activity, R.style.Theme_Luncher_Dialog) {

    init {
        setContentView(R.layout.dialog)
        val field = findViewById<NameField>(R.id.dialog_field)
        findViewById<TextView>(R.id.dialog_title).apply {
            text = title
            labelFor = field.id
        }
        val line = findViewById<ThemedText>(R.id.dialog_text)
        line.text = note
        line.visibility = if (note == null) View.GONE else View.VISIBLE
        val done = {
            val refused = rename(field.text.toString())
            if (refused == null) {
                dismiss()
            } else {
                line.text = refused
                line.visibility = View.VISIBLE
                line.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.icon_warning, 0, 0, 0)
                line.compoundDrawablePadding = context.resources.getDimensionPixelSize(R.dimen.icon_gap)
                line.changeRole(ThemedText.ERROR)
                field.refused = true
            }
        }
        field.visibility = View.VISIBLE
        field.setText(name)
        field.setSelection(name.length)
        // After the name is in: a longer one the field holds already stays whole, to be shortened.
        if (maxLength > 0) field.filters = arrayOf(InputFilter.LengthFilter(maxLength))
        field.setOnEditorActionListener { _, action, event ->
            // Done from the keyboard on screen; or Enter as a key (a USB or Bluetooth keyboard's, or
            // passed on by the Leanback IME), which before Android 11 moves the focus on instead:
            // its press and release are both taken, and the release acts, so nothing comes after.
            val enter = action == EditorInfo.IME_NULL &&
                (event?.keyCode == KeyEvent.KEYCODE_ENTER || event?.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
            if (action == EditorInfo.IME_ACTION_DONE || (enter && event.action == KeyEvent.ACTION_UP)) done()
            action == EditorInfo.IME_ACTION_DONE || enter
        }
        addButton(cancel, DialogButton.Kind.TONAL) { dismiss() }
        if (extra != null) {
            addButton(extra.label, extra.kind, extra.icon, extra.available) {
                dismiss()
                extra.action()
            }
        }
        addButton(confirm, DialogButton.Kind.FILLED, R.drawable.icon_check, action = done)
        fitToScreen()
        checkNotNull(window).stylePanel(roles)
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        // Centered while the keyboard is closed; while it shows, near the top, so it covers none of
        // it. The screen behind doesn't move (its activity's adjustNothing), so neither does the
        // dialog of itself from API 30 on, when Android says whether the keyboard shows (how tall
        // it is reads 0 on some versions). Before API 30 Android moves the dialog above the
        // keyboard and back itself. The window changes only when the keyboard comes or goes:
        // changed before it's up, it would keep it down.
        if (Build.VERSION.SDK_INT >= 30) {
            val window = checkNotNull(window)
            val margin = context.resources.getDimensionPixelSize(R.dimen.dialog_margin)
            var raised = false
            // Nor does its window shrink to the room above the keyboard, as API 30 makes a window
            // with a field do: on a short screen (720p at 320 dpi) that cut the field's text in
            // half. Whole, the keyboard covers its buttons only, which its Done stands in for.
            window.attributes = window.attributes.apply { fitInsetsTypes = fitInsetsTypes and WindowInsets.Type.ime().inv() }
            window.decorView.setOnApplyWindowInsetsListener { view, insets ->
                val keyboard = insets.isVisible(WindowInsets.Type.ime())
                if (keyboard != raised) {
                    raised = keyboard
                    window.attributes = window.attributes.apply {
                        gravity = if (keyboard) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.CENTER
                        y = if (keyboard) margin else 0
                    }
                }
                view.onApplyWindowInsets(insets)
            }
        }
        field.requestFocus()
    }
}

/**
 * Adds a button labelled [label], of [kind], [icon] before it, to the dialog's buttons; OK on it
 * does [action]. Not [available]: dimmed, out of the focus's way, and OK does nothing.
 */
private fun Dialog.addButton(label: CharSequence, kind: DialogButton.Kind, icon: Int = 0, available: Boolean = true, action: () -> Unit) {
    val list = findViewById<EntriesRow>(R.id.dialog_entries)
    val button = layoutInflater.inflate(R.layout.dialog_button, list, false) as PillButton
    button.text = label
    button.kind = when (kind) {
        DialogButton.Kind.FILLED -> PillButton.FILLED
        DialogButton.Kind.TONAL -> PillButton.TONAL
        DialogButton.Kind.DANGER -> PillButton.DANGER
    }
    button.setIcon(icon)
    if (available) {
        button.setOnClickListener { action() }
    } else {
        button.alpha = DIMMED
        button.isFocusable = false
        button.isFocusableInTouchMode = false
    }
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

/** A button that can't be chosen now: as the settings' dimmed entries (Delete all with no pictures). */
private const val DIMMED = 0.4f
