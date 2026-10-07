package com.luncher.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import com.luncher.domain.apps.AppImages
import com.luncher.domain.apps.Banner
import com.luncher.domain.apps.InstalledApp
import com.luncher.launcher.platform.R
import com.luncher.launcher.ui.color
import com.luncher.launcher.ui.R as UiR

/**
 * [AppImages] on PackageManager: draws the image the domain chose for an app ([Banner]) into a
 * bitmap of the tile's size.
 *
 * The bitmap is exactly the tile's size: a banner resource is usually 640x360 px or larger, so
 * keeping only the scaled copy holds memory to what the screen shows, and drawing a tile is a
 * single bitmap copy.
 */
class BannerImages(context: Context) : AppImages<Bitmap> {

    private val packageManager = context.packageManager

    private val cardColor = context.color(R.color.banner_card)

    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.color(UiR.color.text_primary)
        textAlign = Paint.Align.CENTER
    }

    /** [app]'s [banner], drawn [width] x [height] px. Falls back to the icon card if the banner can't be loaded. */
    override fun render(app: InstalledApp, banner: Banner, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val component = ComponentName(app.launchable.packageName, app.launchable.activityName)
        val drawn = when (banner) {
            Banner.AppBanner -> draw(load { packageManager.getActivityBanner(component) }, canvas, 0, 0, width, height)
            Banner.AppIcon -> false
        }
        if (!drawn) drawCard(canvas, component, app.label, width, height)
        return bitmap
    }

    /** The app's icon above its label, on a plain card. */
    private fun drawCard(canvas: Canvas, component: ComponentName, label: String, width: Int, height: Int) {
        canvas.drawColor(cardColor)
        val icon = load { packageManager.getActivityIcon(component) } ?: packageManager.defaultActivityIcon
        val iconSize = height / 2
        val iconLeft = (width - iconSize) / 2
        val iconTop = height / 8
        draw(icon, canvas, iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)

        labelPaint.textSize = height * LABEL_SIZE
        val padding = height / 10f
        val text = TextUtils.ellipsize(label, labelPaint, width - 2 * padding, TextUtils.TruncateAt.END)
        canvas.drawText(text, 0, text.length, width / 2f, height - padding, labelPaint)
    }

    private fun draw(drawable: Drawable?, canvas: Canvas, left: Int, top: Int, right: Int, bottom: Int): Boolean {
        drawable ?: return false
        drawable.setBounds(left, top, right, bottom)
        drawable.draw(canvas)
        return true
    }

    /**
     * Another app's resources are outside Luncher's control: an app uninstalled a moment ago
     * ([PackageManager.NameNotFoundException]) or a broken resource must not crash the home screen.
     */
    private inline fun load(drawable: () -> Drawable?): Drawable? =
        try {
            drawable()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: RuntimeException) {
            null
        }

    private companion object {
        /** Label text height, as a share of the tile's height. */
        const val LABEL_SIZE = 0.14f
    }
}
