package com.luncher.launcher.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.luncher.domain.settings.SettingsEntry
import com.luncher.domain.settings.settingsMenu
import com.luncher.launcher.ui.stylePanel
import com.luncher.launcher.ui.R as UiR

/**
 * The settings panel: a side sheet over the home screen ([placeAsSheet]) that lists the entries of
 * [settingsMenu], each with its icon, its name, and where it goes. Back closes it, as any
 * activity, and the home screen's focus is where it was.
 *
 * What an entry shows and does is decided here, per kind of entry, in [label], [icon] and [open];
 * which entries exist and in which order is the domain's decision.
 */
class SettingsActivity : Activity() {

    private lateinit var entries: LinearLayout
    private val theme by lazy { ScreenTheme(graph.appearanceSettings) { checkNotNull(window).stylePanel(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        placeAsSheet(checkNotNull(window), graph.appearanceSettings.read().topBar)
        entries = findViewById(R.id.settings_entries)
        show(settingsMenu())
        window.stylePanel(theme.roles)
        entries.getChildAt(0)?.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        theme.start()
    }

    override fun onStop() {
        super.onStop()
        theme.stop()
    }

    override fun onResume() {
        super.onResume()
        showPanel(true)   // back from a panel of its own, such as Hide apps
    }

    private fun show(menu: List<SettingsEntry>) {
        for (entry in menu) {
            val view = layoutInflater.inflate(R.layout.settings_entry, entries, false)
            view.findViewById<TextView>(R.id.settings_entry_title).setText(label(entry))
            view.findViewById<ImageView>(R.id.settings_entry_icon).setImageResource(icon(entry))
            view.findViewById<ImageView>(R.id.settings_entry_end).setImageResource(end(entry))
            view.setOnClickListener { open(entry) }
            entries.addView(view)
        }
    }

    private fun label(entry: SettingsEntry): Int = when (entry) {
        SettingsEntry.HideApps -> R.string.settings_hide_apps
        SettingsEntry.Appearance -> R.string.settings_appearance
        SettingsEntry.SystemSettings -> R.string.settings_system
    }

    private fun icon(entry: SettingsEntry): Int = when (entry) {
        SettingsEntry.HideApps -> UiR.drawable.icon_eye_off
        SettingsEntry.Appearance -> UiR.drawable.icon_palette
        SettingsEntry.SystemSettings -> UiR.drawable.icon_tv
    }

    /** At the entry's end: a screen of Luncher's, or another app. */
    private fun end(entry: SettingsEntry): Int = when (entry) {
        SettingsEntry.HideApps, SettingsEntry.Appearance -> UiR.drawable.icon_chevron
        SettingsEntry.SystemSettings -> UiR.drawable.icon_open
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
