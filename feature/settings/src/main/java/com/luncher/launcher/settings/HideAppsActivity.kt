package com.luncher.launcher.settings

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import com.luncher.domain.apps.AppVisibility
import com.luncher.domain.apps.ArrangedApps
import com.luncher.domain.apps.homeApps
import com.luncher.launcher.ui.R as UiR
import com.luncher.launcher.ui.color
import java.util.Locale

/**
 * The settings panel's list of every app by name, over the panel: OK on an app hides it from the
 * home screen, or shows it again. Each change is stored at once, and the home screen shows it when
 * it comes back. Where a hidden or shown app goes among the others is the domain's decision
 * ([ArrangedApps.hide], [ArrangedApps.show]).
 */
class HideAppsActivity : Activity() {

    private val arrangements by lazy { graph.appArrangements }
    private lateinit var arranged: ArrangedApps
    private var rows: List<AppVisibility> = emptyList()
    private val adapter = Rows()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_hide_apps_activity)
        arranged = homeApps(graph.installedApps.tvApps(), packageName, arrangements.read(), Locale.getDefault())
        rows = arranged.byLabel()

        val list = findViewById<ListView>(R.id.settings_apps)
        list.layoutParams.height = listHeight(rows.size)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> toggle(rows[position]) }
        list.requestFocus()
        if (rows.isEmpty()) findViewById<View>(R.id.settings_no_apps).visibility = View.VISIBLE
    }

    /**
     * As tall as its rows, up to six and a half of them (a ListView as tall as its content would
     * create a view for every app), and no taller than the screen leaves room for beside the
     * panel's title and margins, e.g. on a 720p screen at high density. When the rows don't fit,
     * the list ends on half a row, which shows there's more.
     */
    private fun listHeight(rowCount: Int): Int {
        val row = resources.getDimensionPixelSize(R.dimen.settings_entry_height)
        val panel = findViewById<View>(R.id.settings_hide_apps_panel)
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        panel.measure(unspecified, unspecified)   // without the list, which is 0 px tall so far
        val margin = resources.getDimensionPixelSize(R.dimen.settings_panel_margin)
        val room = resources.displayMetrics.heightPixels - 2 * margin - panel.measuredHeight
        val max = minOf(resources.getDimensionPixelSize(R.dimen.settings_list_max_height), room)
        if (rowCount * row <= max) return rowCount * row
        return ((max - row / 2) / row).coerceAtLeast(0) * row + row / 2
    }

    private fun toggle(row: AppVisibility) {
        arranged = if (row.hidden) arranged.show(row.app) else arranged.hide(row.app)
        arrangements.save(arranged.arrangement)
        rows = arranged.byLabel()   // by name, so every row stays where it was
        adapter.notifyDataSetChanged()
    }

    /** Draws the rows of [rows], reusing the views of rows scrolled away. */
    private inner class Rows : BaseAdapter() {

        private val shownColor by lazy { color(UiR.color.text_primary) }

        private val hiddenColor by lazy { color(UiR.color.text_secondary) }

        override fun getCount() = rows.size

        override fun getItem(position: Int) = rows[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.settings_app_row, parent, false)
            val row = rows[position]
            view.findViewById<TextView>(R.id.settings_app_label).apply {
                text = row.app.label
                setTextColor(if (row.hidden) hiddenColor else shownColor)
            }
            view.findViewById<View>(R.id.settings_app_hidden).visibility = if (row.hidden) View.VISIBLE else View.GONE
            return view
        }
    }
}
