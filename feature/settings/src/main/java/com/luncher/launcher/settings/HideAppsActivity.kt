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

    private val installedApps by lazy { graph.installedApps }
    private val arrangements by lazy { graph.appArrangements }
    private lateinit var arranged: ArrangedApps
    private var rows: List<AppVisibility> = emptyList()
    private val adapter = Rows()
    private lateinit var list: ListView

    // Installed, updated or removed while the list shows: in it at once.
    private val showAppChanges: () -> Unit = {
        readApps()
        adapter.notifyDataSetChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_hide_apps_activity)
        list = findViewById(R.id.settings_apps)
        readApps()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> toggle(rows[position]) }
        list.requestFocus()
    }

    // The apps are watched only while the list shows; back from elsewhere, they're read again.
    override fun onStart() {
        super.onStart()
        installedApps.addListener(showAppChanges)
    }

    override fun onRestart() {
        super.onRestart()
        showAppChanges()
    }

    override fun onStop() {
        super.onStop()
        installedApps.removeListener(showAppChanges)
    }

    /** The rows for the apps installed now, the list as tall as they need, and why it's empty if it is. */
    private fun readApps() {
        arranged = homeApps(installedApps.tvApps(), packageName, arrangements.read(), Locale.getDefault())
        rows = arranged.byLabel()
        // At most six and a half rows: a ListView as tall as its content would create a view for every app.
        list.layoutParams.height = listHeight(findViewById(R.id.settings_hide_apps_panel), rows.size, resources.getDimensionPixelSize(R.dimen.settings_list_max_height))
        list.requestLayout()
        findViewById<View>(R.id.settings_no_apps).visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
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
