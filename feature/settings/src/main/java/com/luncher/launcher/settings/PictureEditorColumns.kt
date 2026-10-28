package com.luncher.launcher.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/**
 * A picture editor's two columns: at the start its title, preview, options, Save and Delete all
 * ([PictureStartColumn]); at the end the pictures (LibraryList). The pictures' column is
 * settings_appearance_options_width wide, or half the screen on a narrow one, so the preview keeps
 * the other half; wider where the start column, with its title, would run past the bottom: that
 * one is then as narrow as its preview, as wide as it, needs for Save and Delete all to end at the
 * bottom, level with the pictures' container ([PictureStartColumn.widthFor]). Where that's
 * narrower than its least share of the width (a short screen, large text: the preview small
 * already), the title goes, and with it the pictures' hint, and the pictures keep their width.
 * Set here, as the height the columns get is known only as they're measured.
 */
internal class PictureEditorColumns(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    private val listWidth = resources.getDimensionPixelSize(R.dimen.settings_appearance_options_width)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val start = getChildAt(0) as PictureStartColumn
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val widest = width - minOf(listWidth, width / 2)
        val startWidth = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            widest
        } else {
            start.widthFor(MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom, widest, (width * START).toInt())
        }
        val end = getChildAt(1)
        end.layoutParams.width = width - startWidth
        // How to rename or delete a picture only where the editor fits with its title: on a small
        // screen the room goes to the pictures.
        val hint = end.findViewById<View>(R.id.settings_pictures_hint)
        val visibility = if (start.titled) VISIBLE else GONE
        if (hint.visibility != visibility) hint.visibility = visibility
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private companion object {
        /** The start column's least share of the width with its title: narrower, the title goes. */
        const val START = 0.35f
    }
}
