package com.luncher.domain.apps

import java.text.BreakIterator

/**
 * The name an app the user renamed to [typed] keeps, as [HomeApp.name] shows it: a label only, so
 * without control characters, trimmed, and at most [MAX_APP_NAME] UTF-16 units, cut at the end
 * where a character ends, so no emoji or accented letter is split. Null, the app's own name
 * again, for nothing typed, or for its own [label] (no name of the user's is kept that the
 * device's would show anyway). Nothing is refused: two apps may have one name.
 */
fun appName(typed: String, label: String): String? {
    val name = typed.filterNot { it.isISOControl() }.trim()
    if (name.isEmpty() || name == label.filterNot { it.isISOControl() }.trim()) return null
    if (name.length <= MAX_APP_NAME) return name
    val characters = BreakIterator.getCharacterInstance().apply { setText(name) }
    val end = if (characters.isBoundary(MAX_APP_NAME)) MAX_APP_NAME else characters.preceding(MAX_APP_NAME)
    return name.substring(0, end).trimEnd()
}

/**
 * The longest name of the user's an app keeps, in UTF-16 units. A tile shows about 20 characters
 * of it, the Hide apps list about 40, so a longer one would be cut on every screen. The rename
 * field takes no more.
 */
const val MAX_APP_NAME = 50
