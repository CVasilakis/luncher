package com.luncher.launcher.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Picture
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.roundToInt

/**
 * A list's slide under the focus ([LookAheadListView], [LookAheadScrollView]): as an arrow moves
 * the focus to a row that would need the list to scroll, the focus stays where it is and the list
 * slides behind it, bringing the next row there; one thing moves at a time, the focus or the list.
 *
 * The focus can't be a row's own look while the rows move under it: for that time the list draws
 * it, where the focus stays (the bar), the focused fill and, inside it, what the rows passing
 * through it hold, as it looks focused (their texts and icons inverse), cut along the bar's edges;
 * the fill alone has corners, so the bar keeps them as the rows pass. A row can't be drawn two
 * ways in one frame on the GPU, which keeps one drawing of each view a frame, so the two rows'
 * focused looks are drawn once as the slide starts, apart from the rows ([Look]: recorded, or
 * into a bitmap before API 23), and the rows themselves look at rest ([ListEntry.lookHeld]). At
 * the end the row the focus went to looks focused itself, where the bar was. Nothing is made for
 * a key press or a frame: the animator, its listeners and the looks are kept, and dropped with
 * [release].
 *
 * The list scrolls as [scrollTo] says: by how far, from where the slide started (positive: its
 * content goes up, for Down). A key while it slides ends it first ([finish]). The [list] is drawn
 * again as the slide starts and ends, though it hasn't moved: the GPU keeps a view's drawing until
 * the view itself changes, and the bar is in the list's.
 */
internal class FocusSlide(private val list: View, private val scrollTo: (Int) -> Unit) {

    private val fromLook = Look()
    private val toLook = Look()
    private val from = Rect()
    private val to = Rect()
    private val bar = Rect()
    private var target: ListEntry? = null
    private var distance = 0
    private var offset = 0

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = DURATION_MS
        interpolator = DecelerateInterpolator()
        addUpdateListener { slideTo((distance * it.animatedFraction).roundToInt()) }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) = ended()
        })
    }

    /** Whether the list is sliding: it doesn't scroll to show the focus meanwhile, it slides. */
    val running: Boolean get() = target != null

    /**
     * Slides the list [distance] px, the focus going from [fromEntry], now at [fromBounds], to
     * [toEntry], now at [toBounds] (in the list's window, its top at 0), which takes the focus
     * (the list moves it) as this starts.
     */
    fun start(fromEntry: ListEntry, fromBounds: Rect, toEntry: ListEntry, toBounds: Rect, distance: Int) {
        finish()
        fromLook.take(fromEntry)
        toLook.take(toEntry)
        from.set(fromBounds)
        to.set(toBounds)
        this.distance = distance
        offset = 0
        target = toEntry
        toEntry.lookHeld = true
        list.invalidate()   // the bar, from the first frame on, before the list has moved
        animator.start()
    }

    /** Ends the slide at once, the list where it ends. */
    fun finish() {
        if (running) animator.end()
    }

    /** Scrolls the list again to where the slide is, as the list laid itself out where it was. */
    fun reapply() {
        if (running) scrollTo(offset)
    }

    private fun slideTo(offset: Int) {
        if (!running || offset == this.offset) return
        this.offset = offset
        scrollTo(offset)
    }

    private fun ended() {
        val entry = target ?: return
        slideTo(distance)
        target = null
        entry.lookHeld = false
        list.invalidate()
    }

    /**
     * Draws the focus over the list's rows, [top] being where its window starts in [canvas]: the
     * bar, from the focused row's place to the next one's place at the end (the same place, rows
     * of one height), and inside it the two rows as they look focused, where they are now.
     */
    fun draw(canvas: Canvas, top: Int) {
        val entry = target ?: return
        val f = if (distance == 0) 1f else offset.toFloat() / distance
        bar.set(
            lerp(from.left, to.left, f),
            lerp(from.top, to.top - distance, f),
            lerp(from.right, to.right, f),
            lerp(from.bottom, to.bottom - distance, f),
        )
        canvas.save()
        canvas.translate(0f, top.toFloat())
        entry.drawFocusFill(canvas, bar)
        canvas.clipRect(bar)
        fromLook.draw(canvas, from.left, from.top - offset)
        toLook.draw(canvas, to.left, to.top - offset)
        canvas.restore()
    }

    /** Drops the looks' bitmaps, where there are, as the list leaves its window. */
    fun release() {
        finish()
        fromLook.release()
        toLook.release()
    }

    private fun lerp(start: Int, end: Int, f: Float) = start + ((end - start) * f).roundToInt()

    /**
     * What a row holds as it looks focused, drawn once as the slide starts: from API 23 on, as a
     * recording of its drawing (a [Picture]) that the GPU plays where the row is, as it draws the
     * row itself, no pixels sent to it; before, where the GPU can't play one, into a bitmap kept
     * for the next slides (made anew only for a larger row), not RGB_565: its texts' edges stay
     * smooth. Bitmaps cost: on tv_api28, two rows' bitmaps sent to the GPU took a slide's frames
     * from about 15 ms to about 90 ms; recorded, they stay at 15.
     */
    private class Look {
        private val picture = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Picture() else null
        private val canvas = Canvas()
        private var bitmap: Bitmap? = null

        fun take(entry: ListEntry) {
            val width = entry.width.coerceAtLeast(1)
            val height = entry.height.coerceAtLeast(1)
            if (picture != null) {
                val recording = picture.beginRecording(width, height)
                entry.drawFocused(recording)
                picture.endRecording()
                return
            }
            val kept = bitmap
            if (kept == null || kept.width < width || kept.height < height) {
                kept?.recycle()
                bitmap = Bitmap.createBitmap(
                    maxOf(width, kept?.width ?: 0),
                    maxOf(height, kept?.height ?: 0),
                    Bitmap.Config.ARGB_8888,
                ).also(canvas::setBitmap)
            } else {
                kept.eraseColor(0)
            }
            canvas.save()
            canvas.clipRect(0, 0, width, height)
            entry.drawFocused(canvas)
            canvas.restore()
        }

        fun draw(canvas: Canvas, left: Int, top: Int) {
            if (picture != null) {
                canvas.save()
                canvas.translate(left.toFloat(), top.toFloat())
                canvas.drawPicture(picture)
                canvas.restore()
                return
            }
            bitmap?.let { canvas.drawBitmap(it, left.toFloat(), top.toFloat(), null) }
        }

        fun release() {
            canvas.setBitmap(null)
            bitmap?.recycle()
            bitmap = null
        }
    }

    private companion object {
        /** Short: the list follows each key, and a key while it slides ends the slide. */
        const val DURATION_MS = 150L
    }
}
