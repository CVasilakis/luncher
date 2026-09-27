package com.luncher.domain.apps

/**
 * The installed apps as the user arranged them ([homeApps]): the ones the home screen shows, in
 * order, and the hidden ones. [hide] and [show] return a changed copy; [arrangement] is what to
 * store.
 */
class ArrangedApps internal constructor(
    val shown: List<InstalledApp>,
    val hidden: List<InstalledApp>,
    /** The stored arrangement matched to the installed apps; it keeps the entries of apps not installed now. */
    private val stored: AppArrangement,
) {

    /**
     * [app] hidden, first among the hidden apps: the most recently hidden is the one most likely
     * to be wanted back. The shown apps keep their order.
     */
    fun hide(app: InstalledApp): ArrangedApps {
        require(app in shown) { "not shown: ${app.launchable}" }
        return ArrangedApps(shown - app, listOf(app) + hidden, stored)
    }

    /**
     * [app] shown again: last in the user's order, or in its place by label while there's no
     * order yet, so showing an app doesn't fix the order.
     */
    fun show(app: InstalledApp): ArrangedApps {
        require(app in hidden) { "not hidden: ${app.launchable}" }
        val shown = if (stored.order == null) (shown + app).sortedWith(BY_LABEL) else shown + app
        return ArrangedApps(shown, hidden - app, stored)
    }

    /**
     * The same apps in the order the user arranged by hand, [shown] and [hidden]: from then on the
     * shown apps keep this order, not the one by label.
     */
    internal fun rearranged(shown: List<InstalledApp>, hidden: List<InstalledApp>): ArrangedApps {
        require(shown.size + hidden.size == this.shown.size + this.hidden.size) { "not the same apps" }
        return ArrangedApps(shown, hidden, stored.copy(order = stored.order ?: emptyList()))
    }

    /** All apps by label, each with whether it's hidden: the list where the user hides and shows them. */
    fun byLabel(): List<AppVisibility> =
        (shown.map { AppVisibility(it, hidden = false) } + hidden.map { AppVisibility(it, hidden = true) })
            .sortedWith(compareBy(BY_LABEL) { it.app })

    /** What to store: this arrangement, plus the entries of apps that aren't installed right now, in place. */
    val arrangement: AppArrangement
        get() {
            val installed = HashSet<LaunchableApp>()
            shown.mapTo(installed) { it.launchable }
            hidden.mapTo(installed) { it.launchable }
            return AppArrangement(
                order = stored.order?.let { keepAbsent(shown.map { app -> app.launchable }, it, installed) },
                hidden = keepAbsent(hidden.map { it.launchable }, stored.hidden, installed),
            )
        }
}

/** An app in the list where the user hides and shows apps. */
data class AppVisibility(val app: InstalledApp, val hidden: Boolean)

/**
 * [current] with the entries of [previous] that aren't [installed] put back: each after the
 * installed entry it followed in [previous] (the nearest one still in [current]), or first.
 */
private fun keepAbsent(
    current: List<LaunchableApp>,
    previous: List<LaunchableApp>,
    installed: Set<LaunchableApp>,
): List<LaunchableApp> {
    val inCurrent = current.toSet()
    val first = ArrayList<LaunchableApp>()
    val after = HashMap<LaunchableApp, MutableList<LaunchableApp>>()
    var anchor: LaunchableApp? = null
    for (entry in previous) {
        when {
            entry !in installed -> (anchor?.let { after.getOrPut(it) { ArrayList() } } ?: first).add(entry)
            entry in inCurrent -> anchor = entry
        }
    }
    if (first.isEmpty() && after.isEmpty()) return current
    return first + current.flatMap { listOf(it) + after[it].orEmpty() }
}
