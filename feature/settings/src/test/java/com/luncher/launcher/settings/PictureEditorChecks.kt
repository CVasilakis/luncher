package com.luncher.launcher.settings

import android.app.Activity
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import com.luncher.launcher.testing.TvScreen
import com.luncher.launcher.testing.assertWhole
import com.luncher.launcher.testing.bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.abs

/**
 * In a picture editor whose start column is [column]: the pictures' hint whole where the title
 * shows, else gone; Save and Delete all never below the pictures' container, and level with its
 * bottom where the options scroll above them.
 */
internal fun Activity.assertHintAndButtons(screen: TvScreen, column: Int) {
    val start = findViewById<PictureStartColumn>(column)
    val hint = findViewById<TextView>(R.id.settings_pictures_hint)
    assertEquals("the hint shown on $screen, as the title", start.titled, hint.visibility == View.VISIBLE)
    if (hint.visibility == View.VISIBLE) assertWhole(screen, hint)
    val buttons = bounds(findViewById<View>(R.id.settings_picture_editor_save).parent as View)
    val container = bounds(findViewById<View>(R.id.settings_pictures_list_scroll).parent as View)
    assertTrue("Save and Delete all end at ${buttons.bottom}, the pictures at ${container.bottom} on $screen", buttons.bottom <= container.bottom + 1)
    val scroll = start.getChildAt(1) as ScrollView
    if (scroll.getChildAt(0).height > scroll.height - scroll.paddingTop - scroll.paddingBottom) {
        assertTrue("Save and Delete all end at ${buttons.bottom} as the options scroll, the pictures at ${container.bottom} on $screen", abs(buttons.bottom - container.bottom) <= 2)
    }
}
