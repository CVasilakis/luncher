package com.luncher.launcher.ui

/**
 * The rows of a list that doesn't show whole: [shown] of them fill its window, each [extra] px
 * taller than the usual row (shorter, when negative), so the window ends on a whole row, never on
 * part of one; [window] px of rows and the gaps between them.
 */
internal class RowFit(val shown: Int, val extra: Int, val window: Int)

/**
 * How a list's rows fill [room] px (its height less its margin above and below): `null` while
 * all [content] px of them fit. Else the window shows a whole number of rows, the usual row being
 * [row] px with a [gap] after it: the number of rows that need the smallest change, the rows stretched to
 * fill the room or squeezed so one more fits ([rowsShown]); what's left of the room, less than a
 * pixel a row, stays below the window.
 */
internal fun fitRows(room: Int, content: Int, row: Int, gap: Int): RowFit? {
    if (content <= room || row <= 0) return null
    val shown = rowsShown(room + gap, row + gap)
    val pitch = (room + gap) / shown
    return RowFit(shown, pitch - row - gap, shown * pitch - gap)
}

/**
 * How many rows of [pitch] px fill [room] px best: as many as fit whole, stretched, or one more,
 * squeezed, whichever changes them less, as a ratio: stretching n rows by `room / (n × pitch)`
 * against squeezing n + 1 by `(n + 1) × pitch / room`. So the threshold isn't half a row: the fewer
 * the rows, the more a row more would squeeze each.
 */
internal fun rowsShown(room: Int, pitch: Int): Int {
    val whole = room / pitch
    if (whole == 0) return 1
    val squeezedLess = room.toLong() * room >= whole.toLong() * (whole + 1) * pitch * pitch
    return if (squeezedLess) whole + 1 else whole
}

/**
 * How far a list slides under the focus as an arrow moves it to the next row: 0 to move the focus
 * alone, while the row beyond the next one shows whole (the next one itself, when it's the last:
 * the last row goes to the edge); else as far as the next row is from the focused one, so the next
 * row comes to where the focused one is, and the focus stays put. Never past the end of the list
 * ([left], how far it can still slide that way).
 *
 * The distances are along the way the arrow goes, from the window's start: for Down, from its top;
 * for Up, from its bottom, upwards. [beyondEnd] is where the row beyond the next one ends (null:
 * the next row is the last), [nextEnd] where the next one does, [windowEnd] where the window's rows
 * end (its margin left out), and [step] how far the next row is from the focused one.
 */
internal fun slideFor(beyondEnd: Int?, nextEnd: Int, windowEnd: Int, step: Int, left: Int): Int {
    if ((beyondEnd ?: nextEnd) <= windowEnd) return 0
    return step.coerceAtMost(left).coerceAtLeast(0)
}

/**
 * A list whose rows fill its window whole ([fitRows]): [LookAheadListView], [LookAheadScrollView].
 * Its rows ([ListEntry]) ask it how tall to be: [rowHeight], one height for all, so they line up
 * with the window whatever little their own heights differ by (a chip, an icon); a row whose own
 * content needs more than the usual row (a name on two lines) is that much taller ([rowExtra]).
 */
internal interface FittedRows {

    /** How tall a row is; 0 while all rows show, each as tall as it is. */
    val rowHeight: Int

    /** How much taller a row is than the list's usual row, as the list fills its window (shorter, when negative). */
    val rowExtra: Int

    /**
     * How far the fade at an edge reaches ([ScrollFrame]): over the whole row at that edge and the
     * margin beyond it, so the row there looks dimmed, not cut; 0 for the frame's own.
     */
    val fadeLength: Int
}

/**
 * The height most of [count] rows have ([heightOf] the i-th; 0 or less: not a row), the shortest
 * of those that tie; 0 without rows.
 */
internal inline fun usualHeight(count: Int, heightOf: (Int) -> Int): Int {
    var usual = 0
    var most = 0
    for (i in 0 until count) {
        val height = heightOf(i)
        if (height <= 0) continue
        var same = 0
        for (j in 0 until count) if (heightOf(j) == height) same++
        if (same > most || (same == most && height < usual)) {
            usual = height
            most = same
        }
    }
    return usual
}
