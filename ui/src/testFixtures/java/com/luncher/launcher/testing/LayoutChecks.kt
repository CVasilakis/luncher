package com.luncher.launcher.testing

import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

// Checks of the `*LayoutTest`s, which run on every screen of TV_SCREENS. Their text checks need
// `@GraphicsMode(NATIVE)`: without it Robolectric measures every character as 1 px wide.

/**
 * [view]'s bounds in its window, as zoomed by [zoom] around its centre. From its parent's
 * position: the view's own includes the zoom it has now (a focused tile's).
 */
fun bounds(view: View, zoom: Float = 1f): RectF {
    val parent = view.parent as View
    val location = IntArray(2)
    parent.getLocationInWindow(location)
    val left = location[0] + view.left - parent.scrollX
    val top = location[1] + view.top - parent.scrollY
    val growX = view.width * (zoom - 1) / 2
    val growY = view.height * (zoom - 1) / 2
    return RectF(left - growX, top - growY, left + view.width + growX, top + view.height + growY)
}

fun assertInside(screen: TvScreen, area: RectF, what: String, inner: RectF) {
    assertTrue("$what at $inner, outside $area on $screen", area.contains(inner))
}

/**
 * [view], zoomed by [zoom] around its centre, with [below] more under it (a lifted button's
 * shadow), isn't cut by what holds it. A view that clips its children cuts each at the child's
 * bounds as scaled, so [view]'s own parent never cuts its zoom that way, but an ancestor further up
 * cuts the one it holds, which holds [view], at that one's bounds; and a view that clips to its
 * padding cuts at the padding (a scrolling view's, where it has scrolled to), only where it has
 * some, as Android does; and the screen cuts at its edges.
 */
fun assertZoomShown(screen: TvScreen, what: String, view: View, zoom: Float, below: Float = 0f) {
    val area = bounds(view, zoom).apply { bottom += below }
    var child = view
    var parent = view.parent
    while (parent is ViewGroup && parent.parent is View) {
        if (child !== view && parent.clipChildren) {
            assertInside(screen, bounds(child).apply { inset(-0.5f, -0.5f) }, "$what zoomed, cut by the ${child.javaClass.simpleName} holding it,", area)
        }
        val padded = parent.paddingLeft != 0 || parent.paddingTop != 0 || parent.paddingRight != 0 || parent.paddingBottom != 0
        if (parent.clipToPadding && padded) {
            val box = bounds(parent).apply {
                left += parent.paddingLeft; top += parent.paddingTop; right -= parent.paddingRight; bottom -= parent.paddingBottom
                inset(-0.5f, -0.5f)
            }
            assertInside(screen, box, "$what zoomed, cut by the padding of a ${parent.javaClass.simpleName},", area)
        }
        child = parent
        parent = parent.parent
    }
    val window = view.rootView
    assertInside(screen, RectF(-0.5f, -0.5f, window.width + 0.5f, window.height + 0.5f), "$what zoomed, cut by the screen's edge,", area)
}

/** A float resource's value: a zoom. */
fun View.floatResource(id: Int): Float = TypedValue().also { resources.getValue(id, it, true) }.float

fun assertApart(screen: TvScreen, what: String, a: RectF, b: RectF) {
    assertFalse("$what overlap on $screen: $a and $b", RectF.intersects(a, b))
}

/**
 * [view]'s text shows whole: not ellipsized, and within the room the view leaves it, both ways. A
 * line's width without the space it ends on, where it wraps: that space is never drawn, and may
 * hang past the edge.
 */
fun assertWhole(screen: TvScreen, view: TextView) {
    val layout = view.layout
    val width = view.width - view.totalPaddingLeft - view.totalPaddingRight
    val height = view.height - view.totalPaddingTop - view.totalPaddingBottom
    val cut = layout.height > height ||
        (0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 || layout.getLineMax(it) > width + 0.5f }
    assertFalse("\"${view.text}\" cut on $screen: ${layout.width} x ${layout.height} in $width x $height", cut)
}
