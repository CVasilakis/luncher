package com.luncher.launcher.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Add from folder's two columns: the list, and at its end the preview's ([PreviewColumn]), a gap
 * between them. The preview's column is as wide as its preview, Add all and the note need to reach
 * the bottom ([PreviewColumn.widthFor]), at most as wide as the list; the list has the rest, and
 * all of it while the preview's column is gone (no pictures, so the message saying so has the
 * screen's width, not a column beside an empty one). Set here, as the height the columns get is known only as they're measured, and nested weights would
 * measure the list twice.
 */
internal class FolderColumns(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    private val gap = resources.getDimensionPixelSize(R.dimen.settings_pictures_side_gap)
    private val listColumn: View get() = findViewById(R.id.settings_pictures_folder_list_column)
    private val sideColumn: View get() = findViewById(R.id.settings_pictures_folder_side_column)
    private val side: PreviewColumn get() = findViewById(R.id.settings_pictures_folder_side)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val shared = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight - gap
        val height = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
        val gone = side.visibility == View.GONE
        val width = if (gone) 0 else side.widthFor(height, shared / 2, (shared * SIDE).toInt())
        sideColumn.layoutParams.width = width
        // Resolved to a side at once: the measure reads the side, resolved only once otherwise.
        (listColumn.layoutParams as ViewGroup.MarginLayoutParams).apply {
            marginEnd = if (gone) 0 else width + gap
            resolveLayoutDirection(layoutDirection)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private companion object {
        /**
         * The preview's column's least share of the width with the note: narrower, the note goes,
         * so the preview gets larger. Its share too where its preview doesn't show.
         */
        const val SIDE = 0.41f
    }
}
