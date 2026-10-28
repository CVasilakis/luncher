package com.luncher.launcher.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.luncher.launcher.ui.ScreenSize

/**
 * A picture editor's start: on the line of the pictures' label, its title; below it what scrolls
 * ([PictureEditorScroll]): its preview, the app's name where the screen gives one, and the
 * options; then Save with Delete all. The column is as narrow as the preview, with the title,
 * needs for Save and Delete all to end at the bottom ([widthFor]); where that's too narrow, the
 * title goes. The preview, which the screen gives with its shape ([hold]: the background editor's,
 * the home screen at the TV's shape), gets the height the others leave, at most its height at the
 * column's width, at least a quarter of that. It keeps its shape, centered, so its rounded corners
 * are the picture's. A screen may give it a least height instead (the tile banner picker's, as
 * Add from folder's preview): shorter than that, too small to show anything, it goes, and the app's
 * name takes the title's line, so the options start level with the pictures' container. Where
 * even then they don't fit, they scroll, and Save and Delete all stay at the bottom, level with the
 * container's; else they're right below the options.
 */
internal class PictureStartColumn(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    private val title: TextView get() = getChildAt(0) as TextView
    private val scroll: ScrollView get() = getChildAt(1) as ScrollView
    private val content: ViewGroup get() = scroll.getChildAt(0) as ViewGroup
    private val buttons: View get() = getChildAt(2)
    private lateinit var preview: View
    private lateinit var shape: () -> ScreenSize
    private var least: Int? = null
    private var name: TextView? = null
    private lateinit var titleText: CharSequence

    /** Each of the content's views' top margin, as the layout gives it: the first one shown has none. */
    private val margins = HashMap<View, Int>()

    /** Whether the title shows, as [widthFor] last decided. */
    var titled = true
        private set

    /**
     * Sizes [preview], one of the column's content, to [shape]; before the column is first
     * measured. Where it would be shorter than [least] px, if given, it goes, and [name], the
     * app's below it, if given, goes to the title's line.
     */
    fun hold(preview: View, least: Int? = null, name: TextView? = null, shape: () -> ScreenSize) {
        this.preview = preview
        this.least = least
        this.name = name
        this.shape = shape
        titleText = title.text
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            margins[child] = (child.layoutParams as MarginLayoutParams).topMargin
        }
    }

    /**
     * The column's width, [height] tall: the widest, up to [widest], at which the preview, as wide
     * as the column, leaves the title and the views below it room to end at the bottom, at least
     * [narrowest]; else [widest] without the title, the preview as tall as the views below it
     * leave it ([onMeasure]). Sets [titled] to match.
     */
    fun widthFor(height: Int, widest: Int, narrowest: Int): Int {
        val width = fitted(height - paddingTop - paddingBottom, widest, narrowest)
        titled = width != null
        return width ?: widest
    }

    /**
     * The widest column, from [widest] down to [narrowest], at which the preview fits [room] at
     * its height at the column's width with the others, the title among them; null where none
     * does, or the preview would be shorter than [least]. A narrower column may wrap the others
     * into more lines, so it's narrowed until the preview fits what they leave.
     */
    private fun fitted(room: Int, widest: Int, narrowest: Int): Int? {
        val shape = shape()
        val sides = paddingLeft + paddingRight + content.paddingLeft + content.paddingRight
        var width = widest
        while (width >= narrowest) {
            val left = room - others(width - paddingLeft - paddingRight, titled = true)
            if (left < (least ?: 1)) return null
            val fits = shape.widthAt(left) + sides
            if (fits >= width) return width
            width = fits
        }
        return null
    }

    /**
     * What the column takes but the preview, [width] wide inside its padding, with the title if
     * [titled]: the views as they are with the preview shown, the app's name below it, whether it
     * shows now or not, so its going, which leaves more room, never brings it back.
     */
    private fun others(width: Int, titled: Boolean): Int {
        var others = measured(buttons, width)
        if (titled) others += measured(title, width)
        val inside = width - content.paddingLeft - content.paddingRight
        val params = scroll.layoutParams as MarginLayoutParams
        others += params.topMargin + params.bottomMargin + content.paddingTop + content.paddingBottom
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            if (child === preview) continue
            val params = child.layoutParams as MarginLayoutParams
            others += measured(child, inside) - params.topMargin + (margins[child] ?: 0)
        }
        return others
    }

    /** [child]'s height at [width], with its margins. */
    private fun measured(child: View, width: Int): Int {
        val params = child.layoutParams as MarginLayoutParams
        child.measure(
            MeasureSpec.makeMeasureSpec(width - params.leftMargin - params.rightMargin, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        return child.measuredHeight + params.topMargin + params.bottomMargin
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val shape = shape()
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val previewWidth = width - content.paddingLeft - content.paddingRight
        val natural = shape.heightAt(previewWidth)
        var height = natural
        var shown = true
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            val room = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
            val beyond = others(width, titled) + natural - room
            val least = least
            when {
                beyond <= 0 -> Unit
                least != null && natural - beyond < least -> shown = false
                else -> height = (natural - beyond).coerceAtLeast(natural / 4)
            }
        }
        preview.layoutParams.height = height
        preview.layoutParams.width = if (height == natural) previewWidth else shape.widthAt(height)
        show(preview, shown)
        name?.let { show(it, shown) }
        // The title's line: the title, else the app's name where the preview is gone, else nothing.
        val heading = when {
            titled -> titleText
            !shown -> name?.text
            else -> null
        }
        if (heading != null && title.text.toString() != heading.toString()) title.text = heading
        show(title, heading != null)
        firstWithoutMargin()
        // Where it doesn't all fit, what scrolls takes the room above Save and Delete all, which
        // then end at the bottom.
        var scrolled = ViewGroup.LayoutParams.WRAP_CONTENT
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            val params = scroll.layoutParams as MarginLayoutParams
            var room = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom - measured(buttons, width) - params.topMargin - params.bottomMargin
            if (title.visibility != GONE) room -= measured(title, width)
            if (measured(content, width) > room) scrolled = room.coerceAtLeast(0)
        }
        scroll.layoutParams.height = scrolled
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** The content's first view shown without its top margin, the others with theirs. */
    private fun firstWithoutMargin() {
        var first = true
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            if (child.visibility == GONE) continue
            val params = child.layoutParams as MarginLayoutParams
            val margin = if (first) 0 else margins[child] ?: 0
            if (params.topMargin != margin) params.topMargin = margin
            first = false
        }
    }

    /** Shows [view], or not; only when it changes, as a change asks for a layout. */
    private fun show(view: View, shown: Boolean) {
        val visibility = if (shown) VISIBLE else GONE
        if (view.visibility != visibility) view.visibility = visibility
    }
}
