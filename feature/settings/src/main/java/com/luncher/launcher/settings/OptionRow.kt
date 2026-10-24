package com.luncher.launcher.settings

import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.AppearanceContext
import com.luncher.domain.appearance.AppearanceOption
import com.luncher.domain.options.Option
import com.luncher.domain.options.step
import com.luncher.launcher.ui.R as UiR

/**
 * An option's row, of any catalog: its name, and its value, as the catalog's [labels] say. It shows
 * [model]'s value, and gives [change] the model a change makes: the appearance editor stores it at
 * once, the background editor keeps it as its draft. In the appearance editor
 * (settings_appearance_option) the value is in a chip between arrows, which Left and Right step
 * through, toward the arrow on that side, around from the last value to the first; the swatch the
 * labels give before it, for the accent color.
 * With [opens], in the background editor (settings_background_option, a field), OK opens the
 * values in a list ([OptionList]) instead, beside the field, the field outlined and its mark
 * pointing there while it shows; Left and Right move the focus, as on any other entry: the
 * pictures are beside the options there. The values offered are those [context] offers (the
 * background's picture's shape, say); an option that changes nothing with the others as they are,
 * or in [context], is dimmed, and the focus passes it by.
 */
internal class OptionRow<M, C, O : Option<M, C, *>>(
    val option: O,
    private val labels: OptionLabels<M, O>,
    val view: View,
    private val model: () -> M,
    private val context: () -> C,
    private val change: (M) -> Unit,
    private val opens: ((OptionRow<M, C, O>) -> OptionList<M, C, O>)? = null,
) : View.OnKeyListener {

    private val value = view.findViewById<TextView>(R.id.settings_option_value)
    private val swatch: View? = view.findViewById(R.id.settings_option_swatch)
    private val mark: ImageView? = view.findViewById(R.id.settings_option_mark)

    /** The values' list while it shows. */
    private var list: OptionList<M, C, O>? = null

    init {
        view.findViewById<TextView>(R.id.settings_option_label).setText(labels.label(option))
        if (opens != null) {
            view.setOnClickListener { open(opens) }
        } else {
            view.setOnKeyListener(this)
        }
    }

    private fun open(opens: (OptionRow<M, C, O>) -> OptionList<M, C, O>) {
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
        val model = model()
        val applies = option.appliesTo(model, context())
        value.text = labels.value(option, model, view.context)
        val color = labels.swatch(option, model)
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
        if (event.action == KeyEvent.ACTION_DOWN) change(option.step(model(), forward, context()))
        return true
    }

    private companion object {
        const val DIMMED = 0.4f
    }
}

/** A row of the appearance's options, in the appearance editor or the background editor. */
internal typealias AppearanceRow = OptionRow<Appearance, AppearanceContext, AppearanceOption<*>>
