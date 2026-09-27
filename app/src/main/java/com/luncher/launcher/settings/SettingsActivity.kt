package com.luncher.launcher.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.luncher.domain.settings.SettingsEntry
import com.luncher.domain.settings.SettingsTab
import com.luncher.domain.settings.settingsMenu
import com.luncher.launcher.R

/**
 * The settings panel: a floating window over the home screen that lists the entries of
 * [settingsMenu]. Back closes it, as any activity, and the home screen's focus is where it was.
 *
 * What an entry shows and does is decided here, per kind of entry, in [label] and [open]; which
 * entries exist and in which tab and group is the domain's decision.
 */
class SettingsActivity : Activity() {

    private lateinit var entries: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        entries = findViewById(R.id.settings_entries)
        // One tab shows at a time: the first, until there are more and a tab strip to pick one.
        show(settingsMenu().tabs.first())
        entries.getChildAt(0)?.requestFocus()
    }

    private fun show(tab: SettingsTab) {
        val groupGap = resources.getDimensionPixelSize(R.dimen.settings_group_gap)
        tab.groups.forEachIndexed { groupIndex, group ->
            group.entries.forEachIndexed { entryIndex, entry ->
                val view = layoutInflater.inflate(R.layout.settings_entry, entries, false) as TextView
                view.setText(label(entry))
                view.setOnClickListener { open(entry) }
                if (groupIndex > 0 && entryIndex == 0) {
                    (view.layoutParams as ViewGroup.MarginLayoutParams).topMargin = groupGap
                }
                entries.addView(view)
            }
        }
    }

    private fun label(entry: SettingsEntry): Int = when (entry) {
        SettingsEntry.SystemSettings -> R.string.settings_system
    }

    private fun open(entry: SettingsEntry) = when (entry) {
        SettingsEntry.SystemSettings -> openSystemSettings()
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
