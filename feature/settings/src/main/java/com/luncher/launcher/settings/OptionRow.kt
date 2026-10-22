package com.luncher.launcher.settings

import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.AppearanceOption
import com.luncher.domain.appearance.OptionContext
import com.luncher.domain.appearance.step
import com.luncher.launcher.ui.R as UiR

/**
 * An appearance option's row: its name, and its value. It shows [appearance]'s value, and gives
 * [change] the appearance a change makes: the appearance editor stores it at once, the background
 * editor keeps it as its draft. In the appearance editor (settings_appearance_option) the value is
 * in a chip between arrows, which Left and Right step through, toward the arrow on that side,
 * around from the last value to the first; the accent's swatch before it, for the accent color.
 * With [opens], in the background editor (settings_background_option, a field), OK opens the
 * values in a list ([OptionList]) instead, beside the options, the field outlined and its mark
 * pointing there while it shows; Left and Right move the focus, as on any other entry: the
 * pictures are beside the options there. The values offered are those [context] offers (the
 * background's picture's shape, say); an option that changes nothing with the others as they are,
 * or in [context], is dimmed, and the focus passes it by.
 */
internal class OptionRow(
    val option: AppearanceOption<*>,
    val view: View,
    private val appearance: () -> Appearance,
    private val context: () -> OptionContext,
    private val change: (Appearance) -> Unit,
    private val opens: ((OptionRow) -> OptionList)? = null,
) : View.OnKeyListener {

    private val value = view.findViewById<TextView>(R.id.settings_option_value)
    private val swatch: View? = view.findViewById(R.id.settings_option_swatch)
    private val mark: ImageView? = view.findViewById(R.id.settings_option_mark)

    /** The values' list while it shows. */
    private var list: OptionList? = null

    init {
        view.findViewById<TextView>(R.id.settings_option_label).setText(optionLabel(option))
        if (opens != null) {
            view.setOnClickListener { open(opens) }
        } else {
            view.setOnKeyListener(this)
        }
    }

    private fun open(opens: (OptionRow) -> OptionList) {
        list = opens(this)
        view.isActivated = true
        mark?.setImageResource(UiR.drawable.icon_chevron)
    }

    /** The list closed: the field as it is when closed. */
    fun listClosed() {
        list = null
        view.isActivated = false
        mark?.setImageResource(UiR.drawable.icon_caret_down)
    }

    /** Closes the values' list if it shows, the option keeping the value it shows: the screen is going. */
    fun closeList() {
        list?.close()
        list = null
    }

    fun show() {
        val appearance = appearance()
        val applies = option.appliesTo(appearance, context())
        value.text = view.context.optionValue(option, appearance)
        val color = optionSwatch(option, appearance)
        swatch?.visibility = if (color != null) View.VISIBLE else View.GONE
        if (swatch != null && color != null) {
            swatch.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }
        view.alpha = if (applies) 1f else DIMMED
        view.isFocusable = applies
        view.isFocusableInTouchMode = applies
    }

    // Toward an arrow, its value: in a mirrored row the next value is to the left. Around at the
    // first and last value.
    override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
        val right = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> false
            KeyEvent.KEYCODE_DPAD_RIGHT -> true
            else -> return false
        }
        val forward = right != (v.layoutDirection == View.LAYOUT_DIRECTION_RTL)
        if (event.action == KeyEvent.ACTION_DOWN) change(option.step(appearance(), forward, context()))
        return true
    }

    private companion object {
        const val DIMMED = 0.4f
    }
}
