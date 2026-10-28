package com.luncher.launcher.settings

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.luncher.launcher.ui.R as UiR
import com.luncher.launcher.ui.screenSize
import kotlin.math.ceil

/**
 * Add from folder's column beside the list: the preview's label, the preview, of the TV's shape as
 * the wallpaper fills it, as wide as the column, and below it Add all and the note. The column is
 * as wide as they need to reach the bottom, at most as wide as the list ([widthFor]). Where that
 * makes it narrower than its least share of the width, the note goes, and the preview fills the
 * room it leaves; where even then the preview would be shorter than a row of the list
 * (`settings_pictures_preview_least`: a short screen, large text, a long translation), the label
 * and the preview go, and none is drawn ([previewSize] 0 x 0), the note shows again, and the
 * column takes its least share. Where even Add all and the note don't fit, the column scrolls: to
 * its end while Add all has the focus, so the note shows too, and back to its top when the list
 * has it. The preview is drawn at the size it gets here.
 */
internal class PreviewColumn(context: Context, attrs: AttributeSet?) : ScrollView(context, attrs) {

    private val content: ViewGroup get() = getChildAt(0) as ViewGroup
    private val label: View get() = content.findViewById(R.id.settings_pictures_folder_preview_label)
    private val frame: View get() = content.findViewById(R.id.settings_pictures_folder_preview_frame)
    private val note: View get() = content.findViewById(R.id.settings_pictures_folder_note)
    private val addAll: View get() = content.findViewById(R.id.settings_pictures_folder_add_all)
    private val zoom = TypedValue().also { resources.getValue(UiR.dimen.button_zoom, it, true) }.float
    private val lift = resources.getDimensionPixelSize(UiR.dimen.button_lift)
    private val least = resources.getDimensionPixelSize(R.dimen.settings_pictures_preview_least)

    /** Whether the preview shows, with its label, as last measured. */
    val showsPreview: Boolean get() = frame.visibility == VISIBLE

    /**
     * The column's width, [height] tall: the widest, up to [widest], at which the preview, with
     * the views below it, the note too, fits, at least [narrowest]; else the widest at which it
     * fits without the note, the preview at least a row tall; else [narrowest], with no preview.
     * Shows and hides the note and the preview to match.
     */
    fun widthFor(height: Int, widest: Int, narrowest: Int): Int {
        // The room above the content's padding: the column reaches out as far (leaveRoom).
        val room = height - paddingTop - paddingBottom
        val withNote = fitted(room, widest, note = true)?.takeIf { it >= narrowest }
        val width = withNote ?: fitted(room, widest, note = false)
        show(note, withNote != null || width == null)
        show(label, width != null)
        show(frame, width != null)
        // Room for Add all's zoom, at the widest the column gets, and its shadow: at both sides,
        // and below, where it's last, without the note.
        val zoomed = { size: Int -> ceil(size * (zoom - 1) / 2).toInt() + lift }
        leaveRoom(zoomed(widest), zoomed(addAll.measuredHeight))
        return width ?: narrowest
    }

    /**
     * The widest column, up to [widest], at which the preview fits [room] with the label and Add
     * all, and the note if [note]; null where the preview would be shorter than [least]. A
     * narrower column wraps the note into more lines, so it's narrowed until the preview fits
     * what they leave.
     */
    private fun fitted(room: Int, widest: Int, note: Boolean): Int? {
        val screen = context.screenSize()
        var width = widest
        while (true) {
            val left = room - others(width, note)
            if (left < least) return null
            val fits = screen.widthAt(left)
            if (fits >= width) return width.takeIf { screen.heightAt(it) >= least }
            width = fits
        }
    }

    /** What the views other than the preview take at [width], with their gaps: the label, Add all, and the note if [note]. */
    private fun others(width: Int, note: Boolean): Int {
        var others = 0
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            if (child === frame || (child === this.note && !note)) continue
            others += measured(child, width)
        }
        return others
    }

    /** [child]'s height at [width], with its margins. */
    private fun measured(child: View, width: Int): Int {
        val params = child.layoutParams as LinearLayout.LayoutParams
        child.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            if (params.height >= 0) MeasureSpec.makeMeasureSpec(params.height, MeasureSpec.EXACTLY) else MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        return child.measuredHeight + params.topMargin + params.bottomMargin
    }

    private fun show(view: View, shown: Boolean) {
        val visibility = if (shown) VISIBLE else GONE
        if (view.visibility != visibility) view.visibility = visibility
    }

    // The preview as wide as the column, as [widthFor] fitted it, or smaller where the column was
    // given less height than it was fitted to.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight - content.paddingLeft - content.paddingRight
        val screen = context.screenSize()
        var previewHeight = screen.heightAt(width)
        if (showsPreview && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            val others = (0 until content.childCount).map { content.getChildAt(it) }
                .filter { it !== frame && it.visibility != GONE }
                .sumOf { measured(it, width) }
            val room = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom - content.paddingTop - content.paddingBottom
            previewHeight = previewHeight.coerceAtMost(room - others)
        }
        frame.layoutParams.width = screen.widthAt(previewHeight.coerceAtLeast(0))
        frame.layoutParams.height = previewHeight.coerceAtLeast(0)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /**
     * Leaves [sides] at both sides and [below] at the bottom for a focused view's zoom and shadow:
     * a scrolling view cuts what it holds at the edges of its content, so the content gets it as
     * padding, and the column reaches out as far, so its views stay in line with what's beside it.
     */
    private fun leaveRoom(sides: Int, below: Int) {
        if (content.paddingLeft != sides || content.paddingRight != sides || content.paddingBottom != below) {
            content.setPadding(sides, content.paddingTop, sides, below)
        }
        (layoutParams as MarginLayoutParams).setMargins(-sides, 0, -sides, -below)
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
