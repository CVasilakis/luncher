package com.luncher.launcher.settings

import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.AppearanceOption
import com.luncher.domain.appearance.OptionContext
import com.luncher.domain.appearance.step

/**
 * An appearance option's row (settings_appearance_option): its name, and its value between
 * arrows, which Left and Right step through, toward the arrow on that side, around from the last
 * value to the first. It shows [appearance]'s value, and gives [change] the appearance a step
 * makes: the appearance editor stores it at once, the background editor keeps it as its draft. The
 * values stepped through are those [context] offers (the background's picture's shape, say); an
 * option that changes nothing with the others as they are, or in [context], is dimmed, and the
 * focus passes it by.
 */
internal class OptionRow(
    val option: AppearanceOption<*>,
    val view: View,
    private val appearance: () -> Appearance,
    private val context: () -> OptionContext,
    private val change: (Appearance) -> Unit,
) : View.OnKeyListener {

    private val value = view.findViewById<TextView>(R.id.settings_option_value)

    init {
        view.findViewById<TextView>(R.id.settings_option_label).setText(optionLabel(option))
        view.setOnKeyListener(this)
    }

    fun show() {
        val appearance = appearance()
        val applies = option.appliesTo(appearance, context())
        value.text = view.context.optionValue(option, appearance)
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
