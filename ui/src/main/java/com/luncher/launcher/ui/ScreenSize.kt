package com.luncher.launcher.ui

import android.content.Context

/** A screen's size in px. */
data class ScreenSize(val width: Int, val height: Int) {

    /** The height of a box of this screen's shape, [width] wide: what a preview of the screen takes. */
    fun heightAt(width: Int): Int = (width.toLong() * height / this.width).toInt()

    /** The width of a box of this screen's shape, [height] tall. */
    fun widthAt(height: Int): Int = (height.toLong() * width / this.height).toInt()
}

/**
 * The screen's size in px, as the home screen covers it: what the wallpaper is drawn at, by every
 * screen that draws it, so they draw the same one, and the shape of every preview of it.
 */
fun Context.screenSize(): ScreenSize = resources.displayMetrics.let { ScreenSize(it.widthPixels, it.heightPixels) }
