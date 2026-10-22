package com.luncher.launcher.settings

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import com.luncher.domain.appearance.ColorRoles
import com.luncher.launcher.ui.PillButton
import com.luncher.launcher.ui.Themed
import com.luncher.launcher.ui.ThemedText
import com.luncher.launcher.ui.roundRect
import com.luncher.launcher.ui.stylePanel
import com.luncher.launcher.ui.R as UiR

/** A dialog's button: its [label], its [kind], an [icon] before it (0: none), and what OK on it does. */
internal class DialogButton(val label: CharSequence, val kind: Kind, val icon: Int = 0, val action: () -> Unit) {
    enum class Kind { FILLED, TONAL, DANGER }
}

/** An entry of a menu dialog ([ActionsDialog]): its [label], its [icon], in the error color if it [deletes], and what OK on it does. */
internal class MenuAction(val label: CharSequence, val icon: Int, val deletes: Boolean = false, val action: () -> Unit)

/**
 * A dialog over a pictures' screen or the appearance editor, a floating panel like the settings
 * panel, in the theme's colors ([roles]): its [mark] beside its [title] (a [Badge.Kind] and an
 * icon; none for none), a [text] below, starting below the mark, and [buttons] (what OK on one
 * does also closes the dialog), side by side, the first one focused. Back closes it, as any
 * dialog.
 */
internal class PicturesDialog(
    activity: Activity,
    roles: ColorRoles,
    title: CharSequence,
    text: CharSequence?,
    buttons: List<DialogButton>,
    mark: Pair<Badge.Kind, Int>? = null,
) : Dialog(activity, R.style.Theme_Luncher_Settings_Pictures_Dialog) {

    init {
        setContentView(R.layout.settings_pictures_dialog)
        findViewById<TextView>(R.id.settings_pictures_dialog_title).text = title
        findViewById<TextView>(R.id.settings_pictures_dialog_text).apply {
            this.text = text
            visibility = if (text == null) View.GONE else View.VISIBLE
        }
        if (mark != null) {
            findViewById<Badge>(R.id.settings_pictures_dialog_mark).apply {
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
        findViewById<View>(R.id.settings_pictures_dialog_entries).let { (it as android.view.ViewGroup).getChildAt(0)?.requestFocus() }
    }
}

/**
 * A picture's actions, a menu over the background editor: the picture's name as its [title], its
 * size below ([text]), then an entry for each of [actions], one below the other, the first one
 * focused. OK on one closes the menu and does it; Back closes it.
 */
internal class ActionsDialog(
    activity: Activity,
    roles: ColorRoles,
    title: CharSequence,
    text: CharSequence?,
    actions: List<MenuAction>,
) : Dialog(activity, R.style.Theme_Luncher_Settings_Pictures_Dialog) {

    init {
        setContentView(R.layout.settings_pictures_dialog)
        findViewById<TextView>(R.id.settings_pictures_dialog_title).text = title
        findViewById<TextView>(R.id.settings_pictures_dialog_text).apply {
            this.text = text
            visibility = if (text == null) View.GONE else View.VISIBLE
        }
        val list = findViewById<EntriesRow>(R.id.settings_pictures_dialog_entries)
        list.stacked = true
        list.layoutParams = (list.layoutParams as android.view.ViewGroup.MarginLayoutParams).apply {
            topMargin = context.resources.getDimensionPixelSize(R.dimen.settings_title_gap)
        }
        for (action in actions) {
            val entry = layoutInflater.inflate(R.layout.settings_pictures_entry, list, false) as ThemedText
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
 * The dialog that renames a picture: a field with its [name], focused, the cursor at its end (where
 * a TV's keyboard puts it anyway as it starts), the keyboard up; then Cancel and Rename. Rename,
 * or the keyboard's Done, gives what's typed to [rename], which renames the picture and answers
 * null, closing the dialog, or answers why it can't, shown below the field in the error color, the
 * field outlined in it. Cancel, or Back, closes it.
 */
internal class RenameDialog(
    activity: Activity,
    roles: ColorRoles,
    name: String,
    rename: (String) -> CharSequence?,
) : Dialog(activity, R.style.Theme_Luncher_Settings_Pictures_Dialog) {

    init {
        setContentView(R.layout.settings_pictures_dialog)
        val field = findViewById<NameField>(R.id.settings_pictures_dialog_field)
        findViewById<TextView>(R.id.settings_pictures_dialog_title).apply {
            setText(R.string.settings_pictures_rename_title)
            labelFor = field.id
        }
        val why = findViewById<ThemedText>(R.id.settings_pictures_dialog_text)
        why.visibility = View.GONE
        why.changeRole(ThemedText.ERROR)
        why.setCompoundDrawablesRelativeWithIntrinsicBounds(UiR.drawable.icon_warning, 0, 0, 0)
        why.compoundDrawablePadding = context.resources.getDimensionPixelSize(UiR.dimen.icon_gap)
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
        addButton(context.getString(R.string.settings_pictures_cancel), DialogButton.Kind.TONAL) { dismiss() }
        addButton(context.getString(R.string.settings_pictures_rename), DialogButton.Kind.FILLED, UiR.drawable.icon_check, done)
        fitToScreen()
        checkNotNull(window).stylePanel(roles)
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        field.requestFocus()
    }
}

/**
 * The field of a new name: a field's color, outlined in the accent while it has the focus, and in
 * the error color once a name was [refused].
 */
internal class NameField @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : EditText(context, attrs), Themed {

    var refused = false
        set(value) {
            if (value == field) return
            field = value
            styled?.let { styled = null; style(it) }
        }

    private var styled: ColorRoles? = null

    override fun style(roles: ColorRoles) {
        if (roles == styled) return
        styled = roles
        val radius = resources.getDimension(UiR.dimen.corner_entry)
        val edge = resources.getDimensionPixelSize(R.dimen.settings_field_edge)
        val focusedEdge = if (refused) roles.error else roles.primary
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), roundRect(roles.surfaceHigh, radius, edge, focusedEdge))
            addState(intArrayOf(), roundRect(roles.surfaceHigh, radius, edge, if (refused) roles.error else roles.outlineVariant))
        }
        setTextColor(roles.onSurface)
        highlightColor = com.luncher.launcher.ui.withAlpha(roles.primary, SELECTION)
    }

    private companion object {
        const val SELECTION = 0.35f
    }
}

/** Adds a button labelled [label], of [kind], [icon] before it, to the dialog's buttons; OK on it does [action]. */
private fun Dialog.addButton(label: CharSequence, kind: DialogButton.Kind, icon: Int = 0, action: () -> Unit) {
    val list = findViewById<EntriesRow>(R.id.settings_pictures_dialog_entries)
    val button = layoutInflater.inflate(R.layout.settings_pictures_button, list, false) as PillButton
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
    val margin = context.resources.getDimensionPixelSize(R.dimen.settings_pictures_dialog_margin)
    val panel = findViewById<View>(R.id.settings_pictures_dialog_panel)
    panel.layoutParams.width = minOf(
        context.resources.getDimensionPixelSize(R.dimen.settings_pictures_dialog_width),
        context.resources.displayMetrics.widthPixels - 2 * margin,
    )
}
