package com.luncher.launcher.ui

import android.content.Context

/** A screen's size in px. */
data class ScreenSize(val width: Int, val height: Int)

/**
 * The screen's size in px, as the home screen covers it: what the wallpaper is drawn at, by every
 * screen that draws it, so they draw the same one.
 */
fun Context.screenSize(): ScreenSize = resources.displayMetrics.let { ScreenSize(it.widthPixels, it.heightPixels) }
