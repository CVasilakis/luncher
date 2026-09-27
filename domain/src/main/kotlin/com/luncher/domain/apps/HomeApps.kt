package com.luncher.domain.apps

/**
 * The apps the home screen shows, in the order it shows them, and the hidden ones: every TV app
 * except the launcher itself ([launcherPackage]), arranged as [arrangement] says.
 *
 * - Shown apps follow the stored order, or are sorted by label while there's none. Apps the order
 *   doesn't name yet (installed since) follow it, by label.
 * - An app the arrangement names under an activity that no longer exists is still recognized when
 *   its package now has exactly one TV app the arrangement doesn't name: an update renamed the
 *   activity. Other entries of installed packages that match nothing are dropped.
 * - Entries of packages that aren't installed are kept, so an app that comes back (reinstalled, on
 *   storage that was unplugged) keeps its place and stays hidden.
 */
fun homeApps(installed: List<InstalledApp>, launcherPackage: String, arrangement: AppArrangement): ArrangedApps {
    val apps = installed.filter { it.launchable.packageName != launcherPackage }
    val byId = apps.associateBy { it.launchable }
    val stored = matched(arrangement, byId.keys)

    val hiddenIds = stored.hidden.toSet()
    val hidden = stored.hidden.mapNotNull { byId[it] }
    val unordered = apps.filter { it.launchable !in hiddenIds }
    val shown = when (val order = stored.order) {
        null -> unordered.sortedWith(BY_LABEL)
        else -> {
            val orderIds = order.toSet()
            order.mapNotNull { byId[it] } + unordered.filter { it.launchable !in orderIds }.sortedWith(BY_LABEL)
        }
    }
    return ArrangedApps(shown, hidden, stored)
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

/** By label regardless of case; apps with the same label keep a fixed order, so the screen doesn't reshuffle them. */
internal val BY_LABEL: Comparator<InstalledApp> =
    compareBy<InstalledApp, String>(LabelOrder) { it.label }
        .thenBy { it.launchable.packageName }
        .thenBy { it.launchable.activityName }

/**
 * Labels as the user reads them: regardless of case, and skipping characters that aren't shown.
 * Some system apps' labels carry invisible formatting characters (text direction marks, e.g. dozens
 * of them around "Settings" on Google TV), which would otherwise sort them after every letter.
 * Compares in place, without building a cleaned-up copy of each label.
 */
private object LabelOrder : Comparator<String> {

    override fun compare(a: String, b: String): Int {
        var i = next(a, 0)
        var j = next(b, 0)
        while (i < a.length && j < b.length) {
            val x = a[i].uppercaseChar().lowercaseChar()
            val y = b[j].uppercaseChar().lowercaseChar()
            if (x != y) return x.compareTo(y)
            i = next(a, i + 1)
            j = next(b, j + 1)
        }
        return (if (i < a.length) 1 else 0) - (if (j < b.length) 1 else 0)
    }

    /** The index of the first character from [from] on that's shown. */
    private fun next(s: String, from: Int): Int {
        var i = from
        while (i < s.length && s[i].category == CharCategory.FORMAT) i++
        return i
    }
}
