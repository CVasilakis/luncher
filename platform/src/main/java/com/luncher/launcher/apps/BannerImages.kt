package com.luncher.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
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
 *
 * A banner that's an image file (PNG, JPEG, WebP) is decoded here rather than by Android, and
 * freed once drawn (platform/README.md#writing-adapters). Decoded without Android's density
 * scaling, the file's pixels are scaled once, to the tile's size, rather than twice. Other banners
 * (a vector, a color, a nine-patch) are drawn as Android loads them.
 */
class BannerImages(context: Context) : AppImages<Bitmap> {

    private val packageManager = context.packageManager

    private val cardColor = context.color(R.color.banner_card)

    /** Decodes a banner file as stored, in full color. */
    private val decodeOptions = BitmapFactory.Options().apply {
        inScaled = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    /** Scales a decoded banner smoothly, as a `BitmapDrawable` does. */
    private val bannerPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    private val bannerBounds = Rect()

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
            Banner.AppBanner -> load { drawBanner(component, canvas, width, height) } ?: false
            Banner.AppIcon -> false
        }
        if (!drawn) drawCard(canvas, component, app.label, width, height)
        return bitmap
    }

    /**
     * The banner [component] declares, or else its application, as Android picks it
     * ([PackageManager.getActivityBanner]). False if there's none.
     */
    private fun drawBanner(component: ComponentName, canvas: Canvas, width: Int, height: Int): Boolean {
        // As Android's own lookup: an app's resources may come from a shared library too.
        val activity = packageManager.getActivityInfo(component, PackageManager.GET_SHARED_LIBRARY_FILES)
        val id = if (activity.banner != 0) activity.banner else activity.applicationInfo.banner
        if (id == 0) return false
        val resources = packageManager.getResourcesForApplication(activity.applicationInfo)
        val decoded = decode(resources, id) ?: return draw(resources.getDrawable(id, null), canvas, 0, 0, width, height)
        bannerBounds.set(0, 0, width, height)
        canvas.drawBitmap(decoded, null, bannerBounds, bannerPaint)
        decoded.recycle()
        return true
    }

    /**
     * The image file behind resource [id], at the file's own size: the file Android would pick for
     * this screen, without its density scaling. Null if [id] isn't an image file (an XML drawable,
     * a color) or is a nine-patch, which only its `Drawable` draws right.
     */
    private fun decode(resources: Resources, id: Int): Bitmap? {
        val value = TypedValue()
        resources.getValue(id, value, true)
        val file = value.string?.toString() ?: return null
        if (file.endsWith(".xml")) return null
        val bitmap = resources.openRawResource(id, value).use { BitmapFactory.decodeStream(it, null, decodeOptions) }
            ?: return null
        if (bitmap.ninePatchChunk != null) {
            bitmap.recycle()
            return null
        }
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
    private inline fun <T : Any> load(loader: () -> T?): T? =
        try {
            loader()
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
