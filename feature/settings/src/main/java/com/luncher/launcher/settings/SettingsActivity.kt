package com.luncher.launcher.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.luncher.domain.settings.SettingsEntry
import com.luncher.domain.settings.settingsMenu

/**
 * The settings panel: a floating window over the home screen that lists the entries of
 * [settingsMenu]. Back closes it, as any activity, and the home screen's focus is where it was.
 *
 * What an entry shows and does is decided here, per kind of entry, in [label] and [open]; which
 * entries exist and in which group is the domain's decision.
 */
class SettingsActivity : Activity() {

    private lateinit var entries: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        entries = findViewById(R.id.settings_entries)
        show(settingsMenu())
        // The entries scroll on a screen too short for them all.
        val scroll = findViewById<View>(R.id.settings_entries_scroll)
        scroll.layoutParams.height = 0
        scroll.layoutParams.height = listHeight(findViewById(R.id.settings_panel), entries.childCount)
        entries.getChildAt(0)?.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        showPanel(true)   // back from a panel of its own, such as Hide apps
    }

    private fun show(menu: List<SettingsEntry>) {
        for (entry in menu) {
            val view = layoutInflater.inflate(R.layout.settings_entry, entries, false) as TextView
            view.setText(label(entry))
            view.setOnClickListener { open(entry) }
            entries.addView(view)
        }
    }

    private fun label(entry: SettingsEntry): Int = when (entry) {
        SettingsEntry.HideApps -> R.string.settings_hide_apps
        SettingsEntry.Appearance -> R.string.settings_appearance
        SettingsEntry.SystemSettings -> R.string.settings_system
    }

    private fun open(entry: SettingsEntry) = when (entry) {
        SettingsEntry.HideApps -> openOwnPanel(Intent(this, HideAppsActivity::class.java))
        SettingsEntry.Appearance -> openOwnPanel(Intent(this, AppearanceActivity::class.java))
        SettingsEntry.SystemSettings -> openSystemSettings()
    }

    /**
     * Opens a panel of its own over this one (its theme is `Theme.Luncher.Settings.Panel`), in its
     * place: this panel disappears meanwhile, since the other one may be smaller and would show
     * it around its edges. Its window stays, and with it the dimming of the home screen behind.
     * The appearance editor is full-screen, and covers both.
     */
    private fun openOwnPanel(intent: Intent) {
        startActivity(intent)
        showPanel(false)
    }

    private fun showPanel(shown: Boolean) {
        window.attributes = window.attributes.apply { alpha = if (shown) 1f else 0f }
    }

    /** In a task of its own, like any other app; Back from it returns to this panel. */
    private fun openSystemSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.settings_system_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}
