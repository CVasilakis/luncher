package com.luncher.launcher.home

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.Gravity
import android.widget.TextView
import com.luncher.launcher.ui.WallpaperOutline

/**
 * A text the home screen shows on the wallpaper, with [WallpaperOutline]'s edge around it. Its padding
 * is the layout's plus the outline's room on every side.
 *
 * Its own text layout drawn once with the outline, then the text as any `TextView` draws it: a
 * `TextView` sets its paint's color as it draws, so it can't draw the outline itself.
 */
internal class OutlinedTextView(context: Context, attrs: AttributeSet?) : TextView(context, attrs) {

    private val outline = WallpaperOutline(context)

    init {
        val room = outline.room
        setPadding(paddingLeft + room, paddingTop + room, paddingRight + room, paddingBottom + room)
    }

    override fun onDraw(canvas: Canvas) {
        val layout = layout
        if (layout != null) {
            canvas.save()
            canvas.translate(compoundPaddingLeft.toFloat(), extendedPaddingTop.toFloat() + verticalOffset())
            outline.under(paint) { layout.draw(canvas) }
            canvas.restore()
        }
        super.onDraw(canvas)
    }

    /** Where `TextView` puts the text in a view taller than it: by the gravity, as `TextView` does. */
    private fun verticalOffset(): Int {
        val room = height - extendedPaddingTop - extendedPaddingBottom - layout.height
        return when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.BOTTOM -> room
            Gravity.CENTER_VERTICAL -> room / 2
            else -> 0
        }.coerceAtLeast(0)
    }
}
