package com.luncher.launcher.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import com.luncher.launcher.ui.ScrollFrame

/**
 * A list's frame ([ScrollFrame]: cut along its rounded corners, fading out while there's more
 * below), which also keeps the focus in it on Down past its last row: Android would move it to
 * the nearest view below anywhere on the screen, an entry of the column beside the list (the
 * background editor's Save or Delete all, Add from folder's Add all). Left and Right still leave
 * it. The background editor's pictures, Add from folder's list.
 */
internal class ListFrame(context: Context, attrs: AttributeSet?) : ScrollFrame(context, attrs) {

    override fun focusSearch(focused: View, direction: Int): View? {
        val found = super.focusSearch(focused, direction)
        return if (direction == FOCUS_DOWN) stayingInside(focused, found) else found
    }
}

/**
 * [found], Android's choice for Down from [focused] in this view, if it's inside this view; else
 * [focused]: Android would move it to the nearest view lower down anywhere on the screen, shown or
 * scrolled away, in the column beside this one.
 */
internal fun ViewGroup.stayingInside(focused: View, found: View?): View {
    var parent = found?.parent
    while (parent != null) {
        if (parent === this) return checkNotNull(found)
        parent = parent.parent
    }
    return focused
}
