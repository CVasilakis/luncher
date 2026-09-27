package com.luncher.domain.apps

/**
 * How the user arranged the apps, as stored: the order of the shown apps, and the hidden apps in
 * an order of their own. It may name apps that aren't installed right now, so that an app that
 * comes back keeps its place; [homeApps] matches it to the installed apps.
 */
data class AppArrangement(
    /** The shown apps in the user's order; null until the user first moves one, which means by label. */
    val order: List<LaunchableApp>?,
    /** The hidden apps, most recently hidden first unless the user moved them. */
    val hidden: List<LaunchableApp>,
) {
    companion object {
        /** Nothing arranged yet: every app shown, by label. */
        val NONE = AppArrangement(order = null, hidden = emptyList())
    }
}
