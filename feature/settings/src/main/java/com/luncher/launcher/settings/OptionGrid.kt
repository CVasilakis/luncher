package com.luncher.launcher.settings

import android.view.LayoutInflater
import android.widget.LinearLayout
import com.luncher.domain.appearance.ColorRoles
import com.luncher.domain.options.Option
import com.luncher.launcher.ui.EntriesRow

/**
 * A picture editor's options, of any catalog, as a grid of fields in [container], two a row (one
 * below the other where either doesn't fit its half: [EntriesRow]), each its name above its value
 * (settings_option_field). OK on one opens its values in a list beside it ([OptionList]), named as
 * the catalog's [labels] say; each value tried goes to [change], which the screen keeps as its
 * draft. [model] and [context] are what the fields show, and offer, as each one shows or opens.
 */
internal class OptionGrid<M, C, O : Option<M, C, *>>(
    container: LinearLayout,
    options: List<O>,
    labels: OptionLabels<M, O>,
    model: () -> M,
    context: () -> C,
    roles: () -> ColorRoles,
    change: (M) -> Unit,
) {

    private val rows = ArrayList<OptionRow<M, C, O>>(options.size)

    init {
        val inflater = LayoutInflater.from(container.context)
        val gap = container.resources.getDimensionPixelSize(R.dimen.settings_fields_gap)
        var fields: LinearLayout? = null
        for (option in options) {
            val line = fields?.takeIf { it.childCount < 2 } ?: EntriesRow(container.context, null).apply {
                clipChildren = false
                // The gap above as a margin: padding would cut the fields' zoom at the row's sides.
                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                if (container.childCount > 0) params.topMargin = gap
                container.addView(this, params)
                fields = this
            }
            val view = inflater.inflate(R.layout.settings_option_field, line, false)
            line.addView(view)
            rows += OptionRow(option, labels, view, model, context, change) { row ->
                OptionList(option, labels, row.view, container, model(), context(), roles(), change, row::listClosed)
            }
        }
    }

    /** Each field with its value as [model] has it now, dimmed where it changes nothing. */
    fun show() {
        for (row in rows) row.show()
    }

    /** Closes a list that shows, its option keeping the value it shows: the screen is going. */
    fun closeLists() {
        for (row in rows) row.closeList()
    }
}
