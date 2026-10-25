package com.luncher.domain.apps

import com.luncher.domain.pictures.PictureId

/**
 * The installed apps as the user arranged and set them up ([homeApps]): the ones the home screen
 * shows, in order, and the hidden ones, each with what the user set for it ([HomeApp]). [hide]
 * and [show] return a changed copy; [arrangement] is what to store, and [renamed], [withImage] and
 * [withoutPictures] what to store of what the user set for the apps.
 */
class ArrangedApps internal constructor(
    val shown: List<HomeApp>,
    val hidden: List<HomeApp>,
    /** The stored arrangement matched to the installed apps; it keeps the entries of apps not installed now. */
    private val stored: AppArrangement,
    /**
     * What the user set for the apps, matched to the installed apps ([homeApps]): keyed as the
     * apps are now (an activity an update renamed under its new name), with the entries of apps
     * not installed now.
     */
    private val customizations: Map<LaunchableApp, AppCustomization>,
    /** How apps sort by the name the home screen shows, in the device's language ([homeApps]). */
    private val byName: Comparator<HomeApp>,
    /** How apps sort by the device's label, the real name, in the device's language. */
    private val byLabel: Comparator<HomeApp>,
) {

    /**
     * [app] hidden, first among the hidden apps: the most recently hidden is the one most likely
     * to be wanted back. The shown apps keep their order.
     */
    fun hide(app: HomeApp): ArrangedApps {
        require(app in shown) { "not shown: ${app.launchable}" }
        return ArrangedApps(shown - app, listOf(app) + hidden, stored, customizations, byName, byLabel)
    }

    /**
     * [app] shown again: last in the user's order, or in its place by name while there's no
     * order yet, so showing an app doesn't fix the order.
     */
    fun show(app: HomeApp): ArrangedApps {
        require(app in hidden) { "not hidden: ${app.launchable}" }
        val shown = if (stored.order == null) (shown + app).sortedWith(byName) else shown + app
        return ArrangedApps(shown, hidden - app, stored, customizations, byName, byLabel)
    }

    /**
     * The same apps in the order the user arranged by hand, [shown] and [hidden]: from then on the
     * shown apps keep this order, not the one by name.
     */
    internal fun rearranged(shown: List<HomeApp>, hidden: List<HomeApp>): ArrangedApps {
        require(shown.size + hidden.size == this.shown.size + this.hidden.size) { "not the same apps" }
        return ArrangedApps(shown, hidden, stored.copy(order = stored.order ?: emptyList()), customizations, byName, byLabel)
    }

    /**
     * All apps by the device's label, each with whether it's hidden: the list where the user hides
     * and shows them, which names each app by its real name.
     */
    fun byLabel(): List<AppVisibility> =
        (shown.map { AppVisibility(it, hidden = false) } + hidden.map { AppVisibility(it, hidden = true) })
            .sortedWith(compareBy(byLabel) { it.app })

    /** The pictures of the library the apps' tiles show, the shown apps' first, then the hidden ones': what's prepared for them ([PreparedTileImages]). */
    val tilePictures: Set<PictureId>
        get() = (shown.asSequence() + hidden.asSequence()).mapNotNullTo(LinkedHashSet()) { it.customization.image?.picture }

    /**
     * What to store of what the user set for the apps, with [app] named [name] ([appName]'s), or by
     * its own label again for null; the rest of what's set for it, and for every other app, as it
     * is, the entries of apps not installed now too. An app with nothing set is left out.
     */
    fun renamed(app: HomeApp, name: String?): Map<LaunchableApp, AppCustomization> = changed(app) { it.copy(name = name) }

    /**
     * What to store of what the user set for the apps, with [app]'s tile showing [image], or its
     * own banner again for null or an image without a picture (a draft's "the app's own banner");
     * everything else as [renamed] keeps it.
     */
    fun withImage(app: HomeApp, image: TileImage?): Map<LaunchableApp, AppCustomization> =
        changed(app) { it.copy(image = image?.takeIf { image.picture != null }) }

    /**
     * What to store of what the user set for the apps once the pictures [deleted] are gone: every
     * tile that shows one back to its app's own banner, those of hidden apps and of apps not
     * installed now too, so nothing names a picture that's gone; everything else as it is. Null
     * when no app's tile shows one: nothing to store.
     */
    fun withoutPictures(deleted: Set<PictureId>): Map<LaunchableApp, AppCustomization>? {
        if (customizations.values.none { it.image?.picture in deleted }) return null
        return customizations.mapValues { (_, customization) ->
            if (customization.image?.picture in deleted) customization.copy(image = null) else customization
        }.filterValues { it != AppCustomization.NONE }
    }

    /** What to store with [app]'s customization changed by [change]; an entry left with nothing set is dropped. */
    private inline fun changed(app: HomeApp, change: (AppCustomization) -> AppCustomization): Map<LaunchableApp, AppCustomization> {
        require(shown.any { it.launchable == app.launchable } || hidden.any { it.launchable == app.launchable }) {
            "not installed: ${app.launchable}"
        }
        val customization = change(customizations[app.launchable] ?: AppCustomization.NONE)
        return if (customization == AppCustomization.NONE) customizations - app.launchable else customizations + (app.launchable to customization)
    }

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
data class AppVisibility(val app: HomeApp, val hidden: Boolean)

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
