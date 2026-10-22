package com.luncher.launcher.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/**
 * The appearance editor's two columns: the title, the preview and the buttons, centered together
 * in the height, and the groups' container, whose top is level with the title's, and which grows
 * downward as a group opens, scrolling once it reaches the bottom margin. Where the title ends up
 * is known only once the columns are laid out, so the container is moved there after, measured
 * for the room below it.
 */
internal class AppearanceEditorLayout(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val title = findViewById<View>(R.id.settings_appearance_title)
        val end = findViewById<View>(R.id.settings_appearance_end)
        val frame = findViewById<View>(R.id.settings_appearance_frame)
        val titleTop = (title.parent as View).top + title.top
        val top = (titleTop - end.top).coerceAtLeast(end.paddingTop)
        val room = end.height - top - end.paddingBottom
        if (frame.top == top && frame.height <= room) return
        frame.measure(
            MeasureSpec.makeMeasureSpec(frame.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(room.coerceAtLeast(0), MeasureSpec.AT_MOST),
        )
        frame.layout(frame.left, top, frame.right, top + frame.measuredHeight)
    }
}
