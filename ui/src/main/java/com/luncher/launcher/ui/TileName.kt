package com.luncher.launcher.ui

import android.content.Context
import android.graphics.Paint
import android.text.TextPaint
import kotlin.math.ceil

/**
 * The app's name below the focused tile: its paint, the gap above it, and how far below a tile it
 * reaches. The home screen draws it with this (feature/home's TilePaints), and the appearance
 * editor's preview leaves it the same room, at its own scale, so the two can't drift apart: both
 * measure the text, so the room follows the device's font size and the language's font.
 */
class TileName(context: Context) {

    /** The name's text, in the primary text color. */
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.color(R.color.text_primary)
        textSize = context.resources.getDimension(R.dimen.tile_name_text)
    }

    /** Between a tile's bottom edge and the name's top, before the tile's zoom, in pixels. */
    val gap: Int = context.resources.getDimensionPixelSize(R.dimen.tile_name_gap)

    /** The name's ascent, read once: each read of the font's metrics allocates. */
    val ascent: Int

    private val lineHeight: Int

    private val pixelsPerDp = context.resources.displayMetrics.density

    init {
        val metrics = paint.fontMetricsInt
        ascent = metrics.ascent
        lineHeight = metrics.descent - metrics.ascent
    }

    /**
     * How far below a tile the name reaches, the gap included, with the tile zoomed by [zoom]: the
     * name is drawn with the tile, so it's zoomed as the tile is. In whole pixels.
     */
    fun room(zoom: Float): Int = ceil(zoom * (gap + lineHeight)).toInt()

    /** [room] in dp, for a drawing at a scale of its own (a preview's sample dp). */
    fun roomDp(zoom: Float): Float = room(zoom) / pixelsPerDp

    /** [gap] in dp, for a drawing at a scale of its own. */
    val gapDp: Float = gap / pixelsPerDp
}
