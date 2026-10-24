package com.luncher.domain.apps

import java.text.Collator
import java.util.Locale

/**
 * The apps the home screen shows, in the order it shows them, and the hidden ones: every TV app
 * except the launcher itself ([launcherPackage]), each with what the user set for it
 * ([customizations]), arranged as [arrangement] says.
 *
 * - Shown apps follow the stored order, or are sorted by name (the user's, or else the label)
 *   while there's none, as [locale]'s language sorts words ([NameOrder]). Apps the order doesn't
 *   name yet (installed since) follow it, by name.
 * - An app the arrangement or the customizations name under an activity that no longer exists is
 *   still recognized when its package now has exactly one TV app they don't name: an update
 *   renamed the activity ([StoredMatch]). Other entries of installed packages that match nothing
 *   are dropped.
 * - Entries of packages that aren't installed are kept, so an app that comes back (reinstalled, on
 *   storage that was unplugged) keeps its place, stays hidden, and keeps its name.
 */
fun homeApps(
    installed: List<InstalledApp>,
    launcherPackage: String,
    arrangement: AppArrangement,
    customizations: Map<LaunchableApp, AppCustomization>,
    locale: Locale,
): ArrangedApps {
    val collator = Collator.getInstance(locale)
    val byName = NameOrder(collator) { it.name }
    val byLabel = NameOrder(collator) { it.installed.label }
    val tvApps = installed.filter { it.launchable.packageName != launcherPackage }
    val ids = tvApps.mapTo(HashSet()) { it.launchable }
    val customized = matched(customizations, ids)
    val apps = tvApps.map { HomeApp(it, customized[it.launchable] ?: AppCustomization.NONE) }
    val byId = apps.associateBy { it.launchable }
    val stored = matched(arrangement, ids)

    val hiddenIds = stored.hidden.toSet()
    val hidden = stored.hidden.mapNotNull { byId[it] }
    val unordered = apps.filter { it.launchable !in hiddenIds }
    val shown = when (val order = stored.order) {
        null -> unordered.sortedWith(byName)
        else -> {
            val orderIds = order.toSet()
            order.mapNotNull { byId[it] } + unordered.filter { it.launchable !in orderIds }.sortedWith(byName)
        }
    }
    return ArrangedApps(shown, hidden, stored, customized, byName, byLabel)
}

/**
 * [arrangement] with its entries matched to the [installed] apps ([StoredMatch]). Each app appears
 * once; one that is both hidden and in the order is hidden.
 */
private fun matched(arrangement: AppArrangement, installed: Set<LaunchableApp>): AppArrangement {
    val match = StoredMatch(arrangement.order.orEmpty() + arrangement.hidden, installed)
    val hidden = arrangement.hidden.mapNotNull(match::match).distinct()
    val hiddenIds = hidden.toSet()
    val order = arrangement.order?.mapNotNull(match::match)?.distinct()?.filter { it !in hiddenIds }
    return AppArrangement(order, hidden)
}

/** [customizations] with their apps matched to the [installed] apps ([StoredMatch]). */
private fun matched(
    customizations: Map<LaunchableApp, AppCustomization>,
    installed: Set<LaunchableApp>,
): Map<LaunchableApp, AppCustomization> {
    if (customizations.isEmpty()) return customizations
    val match = StoredMatch(customizations.keys, installed)
    val matched = HashMap<LaunchableApp, AppCustomization>()
    for ((entry, customization) in customizations) {
        match.match(entry)?.let { matched[it] = customization }
    }
    return matched
}

/**
 * The app each entry of a store ([stored]: the arrangement's, the customizations') stands for
 * among the [installed] apps: itself while it's installed; for an entry of an installed package
 * whose activity is gone, the one TV app of that package no entry names, when it's the only such
 * entry of its package (an update renamed the activity), else none; an entry of a package that
 * isn't installed, itself, kept for when the app comes back.
 */
private class StoredMatch(stored: Collection<LaunchableApp>, private val installed: Set<LaunchableApp>) {

    private val installedPackages = installed.mapTo(HashSet()) { it.packageName }

    private val renamed = HashMap<LaunchableApp, LaunchableApp>()

    init {
        val named = stored.toSet()
        named.filter { it !in installed && it.packageName in installedPackages }
            .groupBy { it.packageName }
            .forEach { (packageName, unmatched) ->
                val unclaimed = installed.filter { it.packageName == packageName && it !in named }
                if (unmatched.size == 1 && unclaimed.size == 1) renamed[unmatched[0]] = unclaimed[0]
            }
    }

    fun match(entry: LaunchableApp): LaunchableApp? = when {
        entry in installed -> entry
        entry.packageName in installedPackages -> renamed[entry]
        else -> entry
    }
}

/**
 * Apps by name ([nameOf]: the one the home screen shows, or the device's label) as [collator]'s
 * language sorts words: by letter first, then by accent, then by case, so "Αθήνα", "Άρης",
 * "Βήτα" in Greek, and "Eagle", "Éclair", "Zoom" in English. Apps with the same name keep a fixed
 * order, so the screen doesn't reshuffle them.
 */
internal class NameOrder(
    private val collator: Collator,
    private val nameOf: (HomeApp) -> String,
) : Comparator<HomeApp> {

    override fun compare(a: HomeApp, b: HomeApp): Int {
        val byName = collator.compare(shown(nameOf(a)), shown(nameOf(b)))
        if (byName != 0) return byName
        return compareValuesBy(a, b, { it.launchable.packageName }, { it.launchable.activityName })
    }

    /**
     * [name] without the characters that aren't shown, or [name] itself when it has none. Some
     * system apps' labels carry invisible formatting characters (text direction marks, e.g. dozens
     * of them around "Settings" on Google TV). Android's collation skips them all, but the JDK's,
     * which the tests run on, only some, and sorts the others after every letter.
     */
    private fun shown(name: String): String =
        if (name.none(::isFormat)) name else name.filterNot(::isFormat)

    private fun isFormat(c: Char) = c.category == CharCategory.FORMAT
}
