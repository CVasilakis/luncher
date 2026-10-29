package com.luncher.domain.apps

/**
 * How long OK is held on a tile before its menu opens, in ms, for Android's long-press timeout
 * [system] (`ViewConfiguration.getLongPressTimeout()`): Luncher's own [MENU_HOLD] while [system] is
 * one of Android's defaults ([LONGEST_DEFAULT_LONG_PRESS] at most), so the menu opens sooner; a
 * longer one is the user's, from the accessibility settings, and is kept. Never longer than
 * [system].
 */
fun menuHold(system: Int): Int = if (system > LONGEST_DEFAULT_LONG_PRESS) system else minOf(system, MENU_HOLD)

/**
 * Luncher's own hold, in ms: sooner than Android's defaults, and still well over a short press of
 * OK on a remote (100 to 200 ms), which opens the app.
 */
const val MENU_HOLD = 350

/** Android's longest default long-press timeout, in ms: 500, and 400 from Android 11 on. */
const val LONGEST_DEFAULT_LONG_PRESS = 500
