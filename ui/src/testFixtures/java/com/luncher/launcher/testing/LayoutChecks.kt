package com.luncher.launcher.testing

import android.graphics.RectF
import android.view.View
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

fun assertApart(screen: TvScreen, what: String, a: RectF, b: RectF) {
    assertFalse("$what overlap on $screen: $a and $b", RectF.intersects(a, b))
}

/** [view]'s text shows whole: not ellipsized, and within the room the view leaves it, both ways. */
fun assertWhole(screen: TvScreen, view: TextView) {
    val layout = view.layout
    val width = view.width - view.totalPaddingLeft - view.totalPaddingRight
    val height = view.height - view.totalPaddingTop - view.totalPaddingBottom
    val cut = layout.height > height ||
        (0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 || layout.getLineWidth(it) > width + 0.5f }
    assertFalse("\"${view.text}\" cut on $screen: ${layout.width} x ${layout.height} in $width x $height", cut)
}
