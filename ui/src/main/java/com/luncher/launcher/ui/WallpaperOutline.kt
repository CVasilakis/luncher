package com.luncher.launcher.ui

import android.content.Context
import android.graphics.Paint
import kotlin.math.ceil

/**
 * The dark edge around what's drawn straight on the wallpaper: on the home screen (the top bar's
 * items, arrange mode's texts, the message without apps) and in the previews of it, so it reads on
 * any picture, a white one too. In the plain background's color, so there it doesn't show.
 *
 * It's [width] wide past the shape's edge: a stroke twice as wide, drawn first, which the shape
 * then covers half of, so it never thins the shape. A view drawing with it keeps [room] around
 * the shape, inside its own bounds, which its parent clips it to.
 */
class WallpaperOutline(context: Context) {

    val width = context.resources.getDimension(R.dimen.wallpaper_outline)
    val color = context.color(R.color.wallpaper_outline)

    /** [width] in dp, for a drawing at a scale of its own (a preview's sample dp). */
    val widthDp = width / context.resources.displayMetrics.density

    /** [width] in whole pixels, the room to keep around a shape. */
    val room = ceil(width).toInt()

    /**
     * Calls [draw] with [paint], made for a moment to draw the outline of the filled shapes
     * (text) it draws, then gives [paint] back as it was. The shapes themselves are the caller's
     * to draw next.
     */
    inline fun under(paint: Paint, draw: () -> Unit) {
        val style = paint.style
        val strokeWidth = paint.strokeWidth
        val join = paint.strokeJoin
        val color = paint.color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2 * width
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = this.color
        draw()
        paint.style = style
        paint.strokeWidth = strokeWidth
        paint.strokeJoin = join
        paint.color = color
    }
}
