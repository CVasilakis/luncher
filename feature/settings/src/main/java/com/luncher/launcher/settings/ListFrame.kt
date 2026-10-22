package com.luncher.launcher.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
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
        if (direction == FOCUS_DOWN && (found == null || !isInside(found))) return focused
        return found
    }

    private fun isInside(view: View): Boolean {
        var parent = view.parent
        while (parent != null) {
            if (parent === this) return true
            parent = parent.parent
        }
        return false
    }
}
