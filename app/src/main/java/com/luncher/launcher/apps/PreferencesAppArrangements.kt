package com.luncher.launcher.apps

import android.content.SharedPreferences
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.AppArrangements
import com.luncher.domain.apps.LaunchableApp

/**
 * [AppArrangements] in SharedPreferences. Each list is one string, an app per line as
 * `package/activity`: a string set would lose the order. No order key means no order yet.
 *
 * Read from storage once per process, then kept; a save writes in the background (`apply`).
 */
class PreferencesAppArrangements(private val preferences: SharedPreferences) : AppArrangements {

    private var current: AppArrangement? = null

    override fun read(): AppArrangement =
        current ?: AppArrangement(
            order = preferences.getString(ORDER, null)?.let(::decode),
            hidden = decode(preferences.getString(HIDDEN, null).orEmpty()),
        ).also { current = it }

    override fun save(arrangement: AppArrangement) {
        current = arrangement
        val order = arrangement.order
        preferences.edit()
            .apply { if (order == null) remove(ORDER) else putString(ORDER, encode(order)) }
            .putString(HIDDEN, encode(arrangement.hidden))
            .apply()
    }

    private fun encode(apps: List<LaunchableApp>) = apps.joinToString("\n") { "${it.packageName}/${it.activityName}" }

    // Package and class names can't contain '/' or line breaks. A line that isn't an app is skipped.
    private fun decode(stored: String): List<LaunchableApp> =
        stored.lineSequence()
            .mapNotNull { line ->
                val slash = line.indexOf('/')
                if (slash <= 0 || slash == line.lastIndex) null else LaunchableApp(line.substring(0, slash), line.substring(slash + 1))
            }
            .toList()

    private companion object {
        const val ORDER = "order"
        const val HIDDEN = "hidden"
    }
}
