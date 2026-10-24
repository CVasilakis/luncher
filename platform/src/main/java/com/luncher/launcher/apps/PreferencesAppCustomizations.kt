package com.luncher.launcher.apps

import android.content.Context
import com.luncher.domain.apps.AppCustomization
import com.luncher.domain.apps.AppCustomizations
import com.luncher.domain.apps.LaunchableApp
import org.json.JSONException
import org.json.JSONObject

/**
 * [AppCustomizations] in SharedPreferences, a file of its own: a key per app, `package/activity`,
 * whose value is what the user set for it as a JSON object, `{"name":"Headlines"}`, a field per
 * value set. JSON keeps any character a name may hold (a line break, a `/`), and has room for the
 * values to come. An app with nothing set has no key. A key or a value it can't read is skipped,
 * and so is a field it doesn't know (written by a newer version).
 *
 * Read from storage once per process, then kept; a save writes in the background (`apply`) and
 * tells the listeners at once. Every screen gets this one instance from AppGraph, so a save made
 * on one screen reaches the others.
 */
class PreferencesAppCustomizations(context: Context) : AppCustomizations {

    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val listeners = mutableListOf<() -> Unit>()

    private var current: Map<LaunchableApp, AppCustomization>? = null

    override fun read(): Map<LaunchableApp, AppCustomization> =
        current ?: decode(preferences.all).also { current = it }

    override fun save(customizations: Map<LaunchableApp, AppCustomization>) {
        val kept = customizations.filterValues { it != AppCustomization.NONE }
        current = kept
        val editor = preferences.edit().clear()
        for ((app, customization) in kept) editor.putString("${app.packageName}/${app.activityName}", encode(customization))
        editor.apply()
        listeners.toList().forEach { it() }
    }

    override fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    override fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    private fun encode(customization: AppCustomization): String =
        JSONObject().apply { customization.name?.let { put(NAME, it) } }.toString()

    private fun decode(stored: Map<String, *>): Map<LaunchableApp, AppCustomization> {
        val customizations = HashMap<LaunchableApp, AppCustomization>()
        for ((key, value) in stored) {
            val app = app(key) ?: continue
            val customization = (value as? String)?.let(::customization) ?: continue
            if (customization != AppCustomization.NONE) customizations[app] = customization
        }
        return customizations
    }

    // Package and class names can't contain '/'.
    private fun app(key: String): LaunchableApp? {
        val slash = key.indexOf('/')
        return if (slash <= 0 || slash == key.lastIndex) null else LaunchableApp(key.substring(0, slash), key.substring(slash + 1))
    }

    private fun customization(value: String): AppCustomization? =
        try {
            val json = JSONObject(value)
            AppCustomization(name = json.opt(NAME) as? String)
        } catch (e: JSONException) {
            null
        }

    private companion object {
        const val FILE = "app_customizations"
        const val NAME = "name"
    }
}
