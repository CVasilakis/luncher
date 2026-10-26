package com.luncher.launcher.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.ImageView

/**
 * A picture's thumbnail in a picture editor's list (settings_pictures_row): its image, and over it
 * a [line], a see-through picture's hairline, which takes the row's state (the thumbnail duplicates
 * it), as the row's texts take their colors: so it can read on the row at rest, focused, and on the
 * chosen row's tint. Not the view's overlay, whose drawables never get the view's state.
 */
internal class PictureThumbnail(context: Context, attrs: AttributeSet?) : ImageView(context, attrs) {

    /** Drawn over the image, at the bounds it's given, in the state the row is in; null for none. */
    var line: Drawable? = null
        set(value) {
            if (value === field) return
            field?.callback = null
            field = value
            value?.callback = this
            if (value != null && value.isStateful) value.state = drawableState
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        line?.draw(canvas)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        val line = line ?: return
        if (line.isStateful && line.setState(drawableState)) invalidate()
    }

    override fun verifyDrawable(who: Drawable): Boolean = who === line || super.verifyDrawable(who)
}
