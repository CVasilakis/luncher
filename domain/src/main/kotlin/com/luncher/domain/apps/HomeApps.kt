package com.luncher.domain.apps

import java.text.Collator
import java.util.Locale

/**
 * The apps the home screen shows, in the order it shows them, and the hidden ones: every TV app
 * except the launcher itself ([launcherPackage]), arranged as [arrangement] says.
 *
 * - Shown apps follow the stored order, or are sorted by label while there's none, as [locale]'s
 *   language sorts words ([LabelOrder]). Apps the order doesn't name yet (installed since) follow
 *   it, by label.
 * - An app the arrangement names under an activity that no longer exists is still recognized when
 *   its package now has exactly one TV app the arrangement doesn't name: an update renamed the
 *   activity. Other entries of installed packages that match nothing are dropped.
 * - Entries of packages that aren't installed are kept, so an app that comes back (reinstalled, on
 *   storage that was unplugged) keeps its place and stays hidden.
 */
fun homeApps(
    installed: List<InstalledApp>,
    launcherPackage: String,
    arrangement: AppArrangement,
    locale: Locale,
): ArrangedApps {
    val byLabel = LabelOrder(locale)
    val apps = installed.filter { it.launchable.packageName != launcherPackage }
    val byId = apps.associateBy { it.launchable }
    val stored = matched(arrangement, byId.keys)

    val hiddenIds = stored.hidden.toSet()
    val hidden = stored.hidden.mapNotNull { byId[it] }
    val unordered = apps.filter { it.launchable !in hiddenIds }
    val shown = when (val order = stored.order) {
        null -> unordered.sortedWith(byLabel)
        else -> {
            val orderIds = order.toSet()
            order.mapNotNull { byId[it] } + unordered.filter { it.launchable !in orderIds }.sortedWith(byLabel)
        }
    }
    return ArrangedApps(shown, hidden, stored, byLabel)
}

/**
 * [arrangement] with its entries matched to the [installed] apps: renamed activities replaced,
 * entries of installed packages that match nothing dropped, those of other packages kept. Each app
 * appears once; one that is both hidden and in the order is hidden.
 */
private fun matched(arrangement: AppArrangement, installed: Set<LaunchableApp>): AppArrangement {
    val stored = (arrangement.order.orEmpty() + arrangement.hidden).distinct()
    val installedPackages = installed.mapTo(HashSet()) { it.packageName }
    val renamed = HashMap<LaunchableApp, LaunchableApp>()
    stored.filter { it !in installed && it.packageName in installedPackages }
        .groupBy { it.packageName }
        .forEach { (packageName, unmatched) ->
            val unclaimed = installed.filter { it.packageName == packageName && it !in stored }
            if (unmatched.size == 1 && unclaimed.size == 1) renamed[unmatched[0]] = unclaimed[0]
        }

    fun match(entry: LaunchableApp): LaunchableApp? = when {
        entry in installed -> entry
        entry.packageName in installedPackages -> renamed[entry]
        else -> entry
    }

    val hidden = arrangement.hidden.mapNotNull(::match).distinct()
    val hiddenIds = hidden.toSet()
    val order = arrangement.order?.mapNotNull(::match)?.distinct()?.filter { it !in hiddenIds }
    return AppArrangement(order, hidden)
}

/**
 * Apps by label as [locale]'s language sorts words: by letter first, then by accent, then by case,
 * so "Αθήνα", "Άρης", "Βήτα" in Greek, and "Eagle", "Éclair", "Zoom" in English. Apps with the same
 * label keep a fixed order, so the screen doesn't reshuffle them.
 */
internal class LabelOrder(locale: Locale) : Comparator<InstalledApp> {

    private val collator = Collator.getInstance(locale)

    override fun compare(a: InstalledApp, b: InstalledApp): Int {
        val byLabel = collator.compare(shown(a.label), shown(b.label))
        if (byLabel != 0) return byLabel
        return compareValuesBy(a, b, { it.launchable.packageName }, { it.launchable.activityName })
    }

    /**
     * [label] without the characters that aren't shown, or [label] itself when it has none. Some
     * system apps' labels carry invisible formatting characters (text direction marks, e.g. dozens
     * of them around "Settings" on Google TV). Android's collation skips them all, but the JDK's,
     * which the tests run on, only some, and sorts the others after every letter.
     */
    private fun shown(label: String): String =
        if (label.none(::isFormat)) label else label.filterNot(::isFormat)

    private fun isFormat(c: Char) = c.category == CharCategory.FORMAT
}
