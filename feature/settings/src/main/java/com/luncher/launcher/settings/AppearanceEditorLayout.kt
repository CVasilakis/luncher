package com.luncher.launcher.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * The appearance editor's two columns: the title and the preview, centered together in the
 * height, and the options, which start level with the title, the first entry's middle at the
 * title's. Where the title ends up is known only once the columns are laid out, so the options are
 * moved there after, and only when the title moved: a new screen size or language.
 */
internal class AppearanceEditorLayout(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val title = findViewById<View>(R.id.settings_appearance_title)
        val scroll = findViewById<View>(R.id.settings_appearance_scroll)
        val first = findViewById<ViewGroup>(R.id.settings_appearance_options).getChildAt(0) ?: return
        val titleTop = (title.parent as View).top + title.top
        val top = (titleTop + (title.height - first.height) / 2 - scroll.top).coerceAtLeast(0)
        if (scroll.paddingTop == top) return
        scroll.setPaddingRelative(scroll.paddingStart, top, scroll.paddingEnd, scroll.paddingBottom)
        scroll.measure(
            MeasureSpec.makeMeasureSpec(scroll.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(scroll.height, MeasureSpec.EXACTLY),
        )
        scroll.layout(scroll.left, scroll.top, scroll.right, scroll.bottom)
    }
}
