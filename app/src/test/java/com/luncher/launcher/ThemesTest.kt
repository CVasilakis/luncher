package com.luncher.launcher

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Never a white screen: every background Android may show for one of Luncher's screens, while it
 * starts or before it draws, is set by Luncher's own themes (not left to Theme.DeviceDefault,
 * which device makers may restyle) and is dark.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = TV_1080P)
class ThemesTest {

    private val context = RuntimeEnvironment.getApplication()

    private val backgrounds = mapOf(
        "windowBackground" to android.R.attr.windowBackground,
        "colorBackground" to android.R.attr.colorBackground,
        "windowSplashScreenBackground" to android.R.attr.windowSplashScreenBackground,
    )

    @Test
    fun `every screen's backgrounds are Luncher's own, and dark`() {
        val activities = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES).activities!!
        assertTrue(activities.isNotEmpty())
        for (activity in activities) {
            val theme = context.resources.newTheme().apply { applyStyle(activity.themeResource, true) }
            for ((name, attr) in backgrounds) {
                val where = "${activity.name.substringAfterLast('.')}'s $name"
                val values = theme.obtainStyledAttributes(intArrayOf(attr))
                val resource = values.getResourceId(0, 0)
                val drawable = values.getDrawable(0)
                values.recycle()
                assertTrue("$where isn't set", resource != 0 && drawable != null)
                assertEquals("$where comes from elsewhere", context.packageName, context.resources.getResourcePackageName(resource))
                val brightness = averageBrightness { canvas -> drawable!!.setBounds(0, 0, canvas.width, canvas.height); drawable.draw(canvas) }
                assertTrue("$where is too bright: $brightness", brightness < MAX_BRIGHTNESS)
            }
        }
    }

    /** The average brightness, 0 (black) to 1 (white), of a 1080p screen drawn by [draw] over black. */
    private fun averageBrightness(draw: (Canvas) -> Unit): Double {
        val metrics = context.resources.displayMetrics
        val screen = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(screen)
        canvas.drawColor(Color.BLACK)
        draw(canvas)
        val pixels = IntArray(screen.width * screen.height)
        screen.getPixels(pixels, 0, screen.width, 0, 0, screen.width, screen.height)
        return pixels.sumOf { 0.2126 * Color.red(it) + 0.7152 * Color.green(it) + 0.0722 * Color.blue(it) } / pixels.size / 255
    }

    private companion object {
        /** Mid-grey is 0.5. */
        const val MAX_BRIGHTNESS = 0.2
    }
}
