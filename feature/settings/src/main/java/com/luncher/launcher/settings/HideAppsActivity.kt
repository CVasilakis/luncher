package com.luncher.launcher.settings

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import com.luncher.domain.apps.AppVisibility
import com.luncher.domain.apps.ArrangedApps
import com.luncher.domain.apps.homeApps
import com.luncher.launcher.ui.ThemedText
import com.luncher.launcher.ui.styleAll
import com.luncher.launcher.ui.stylePanel
import java.util.Locale

/**
 * The settings panel's list of every app by name, a side sheet in the panel's place: OK on an app
 * hides it from the home screen, or shows it again; a hidden one has a crossed eye and "Hidden". Each change is stored at once, and the home screen shows it when
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
    private val theme by lazy {
        ScreenTheme(graph.appearanceSettings) {
            checkNotNull(window).stylePanel(it)
            list.invalidateViews()
        }
    }

    // Installed, updated or removed while the list shows: in it at once.
    private val showAppChanges: () -> Unit = {
        readApps()
        adapter.notifyDataSetChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_hide_apps_activity)
        placeAsSheet(checkNotNull(window), graph.appearanceSettings.read().topBar)
        list = findViewById(R.id.settings_apps)
        window.stylePanel(theme.roles)
        readApps()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> toggle(rows[position]) }
        list.requestFocus()
    }

    // The apps are watched only while the list shows; back from elsewhere, they're read again.
    override fun onStart() {
        super.onStart()
        installedApps.addListener(showAppChanges)
        theme.start()
    }

    override fun onRestart() {
        super.onRestart()
        showAppChanges()
    }

    override fun onStop() {
        super.onStop()
        installedApps.removeListener(showAppChanges)
        theme.stop()
    }

    /**
     * The rows for the apps installed now, and why there are none if there aren't, over the list:
     * the list stays, with the focus, for an app installed while it shows (an empty view would
     * take the list away, and its focus with it).
     */
    private fun readApps() {
        arranged = homeApps(installedApps.tvApps(), packageName, arrangements.read(), Locale.getDefault())
        rows = arranged.byLabel()
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

        override fun getCount() = rows.size

        override fun getItem(position: Int) = rows[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.settings_app_row, parent, false)
            val row = rows[position]
            view.findViewById<ThemedText>(R.id.settings_app_label).apply {
                text = row.app.label
                changeRole(if (row.hidden) ThemedText.ON_SURFACE_VARIANT else ThemedText.ON_SURFACE)
            }
            view.findViewById<View>(R.id.settings_app_hidden).visibility = if (row.hidden) View.VISIBLE else View.GONE
            view.styleAll(theme.roles)
            return view
        }
    }
}
