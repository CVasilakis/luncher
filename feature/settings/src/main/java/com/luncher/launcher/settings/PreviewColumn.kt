package com.luncher.launcher.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.luncher.launcher.ui.screenSize

/**
 * Add from folder's column beside the list: the preview's label, the preview, of the TV's shape as
 * the wallpaper fills it, as wide as the column, and below it Add all and the note. On a screen too short for them all, the preview gets smaller, so the others still show
 * whole; where it would be shorter than a row of the list (`settings_pictures_preview_least`: a
 * short screen, large text, a long translation), the label and the preview go, and none
 * is drawn ([previewSize] 0 x 0). Where even Add all and the note don't fit, the column scrolls: to
 * its end while Add all has the focus, so the note shows too, and back to its top when the list
 * has it. The preview is drawn at the size it gets here.
 */
internal class PreviewColumn(context: Context, attrs: AttributeSet?) : ScrollView(context, attrs) {

    private val content: ViewGroup get() = getChildAt(0) as ViewGroup
    private val label: View get() = content.findViewById(R.id.settings_pictures_folder_preview_label)
    private val frame: View get() = content.findViewById(R.id.settings_pictures_folder_preview_frame)
    private val least = resources.getDimensionPixelSize(R.dimen.settings_pictures_preview_least)

    /** Whether the preview shows, with its label, as last measured. */
    val showsPreview: Boolean get() = frame.visibility == VISIBLE

    // What the preview would get is worked out with its label shown, whether it shows now or not,
    // so hiding it, which leaves more room, never brings it back.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight - content.paddingLeft - content.paddingRight
        val screen = context.screenSize()
        // What the views other than the preview take, with their gaps.
        var others = 0
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            if (child === frame || (child.visibility == GONE && child !== label)) continue
            val params = child.layoutParams as LinearLayout.LayoutParams
            child.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                if (params.height >= 0) MeasureSpec.makeMeasureSpec(params.height, MeasureSpec.EXACTLY) else MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            others += child.measuredHeight + params.topMargin + params.bottomMargin
        }
        var previewHeight = screen.heightAt(width)
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            previewHeight = previewHeight.coerceAtMost(MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom - others)
        }
        val shown = if (previewHeight >= least) VISIBLE else GONE
        for (view in listOf(label, frame)) if (view.visibility != shown) view.visibility = shown
        frame.layoutParams.width = screen.widthAt(previewHeight.coerceAtLeast(0))
        frame.layoutParams.height = previewHeight.coerceAtLeast(0)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /**
     * Leaves [room] at both sides for a focused view's zoom and shadow: a scrolling view cuts what
     * it holds at the edges of its content, so the content gets it as padding, and the column
     * reaches out as far, so its views stay in line with what's above it.
     */
    fun leaveRoom(room: Int) {
        content.setPadding(room, content.paddingTop, room, content.paddingBottom)
        (layoutParams as MarginLayoutParams).setMargins(-room, 0, -room, 0)
    }

    /** The preview's size as last laid out: what it's drawn at. 0 x 0 before the first layout, and while it doesn't show. */
    val previewSize: Pair<Int, Int> get() = if (showsPreview) frame.width to frame.height else 0 to 0

    /** Called when the preview's size changes, to draw it again at the new size. */
    var onPreviewSized: (() -> Unit)? = null

    private var laidOut = 0 to 0

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (previewSize == laidOut) return
        laidOut = previewSize
        onPreviewSized?.invoke()
    }

    // Add all, the only view here that takes the focus: the end shows, the note below it too.
    override fun requestChildFocus(child: View?, focused: View?) {
        super.requestChildFocus(child, focused)
        scrollTo(0, (content.height - (height - paddingTop - paddingBottom)).coerceAtLeast(0))
    }

    override fun clearChildFocus(child: View?) {
        super.clearChildFocus(child)
        scrollTo(0, 0)
    }
}
