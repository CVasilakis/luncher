package com.luncher.launcher.home

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Window
import com.luncher.domain.appearance.Background
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.Jobs
import com.luncher.domain.jobs.LatestRequest
import com.luncher.domain.pictures.PictureImages
import com.luncher.launcher.ui.color
import com.luncher.launcher.ui.R as UiR

/**
 * The home screen's wallpaper: the [Background]'s picture as [window]'s background, drawn at the
 * screen's size in the background's style, or the home screen's plain color without one. What it
 * shows follows the part with `==`, so an unchanged background costs nothing.
 *
 * A picture is read from what an earlier start drew, on the main thread, so a cold start's first
 * frame has it, as the tiles' images are; one not drawn yet (a new picture, style or screen size)
 * is drawn in the background, the plain color showing meanwhile, the latest request winning. A
 * picture that's gone (deleted, its file lost) is no wallpaper.
 */
internal class HomeWallpaper(private val window: Window, private val images: PictureImages<Bitmap>, jobs: Jobs) {

    private val resources = window.context.resources
    private val plain = ColorDrawable(window.context.color(UiR.color.background))
    private val loading = LatestRequest(jobs, JobPriority.WALLPAPER) { Handler(Looper.getMainLooper()).post(it) }

    /** What the window shows; null until the first [show], and after [stop] dropped a picture being drawn. */
    private var shown: Background? = null

    fun show(background: Background) {
        if (background == shown) return
        shown = background
        loading.stop()
        val picture = background.picture ?: return setPlain()
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        val kept = images.cached(picture, width, height, background.style)
        if (kept != null) return setPicture(kept)
        setPlain()
        loading.load({ images.render(picture, width, height, background.style) }) { image ->
            if (image != null) setPicture(image)
        }
    }

    /** Drops a picture being drawn, which the next [show] draws again. */
    fun stop() {
        if (loading.stop()) shown = null
    }

    private fun setPlain() = window.setBackgroundDrawable(plain)

    private fun setPicture(image: Bitmap) = window.setBackgroundDrawable(BitmapDrawable(resources, image))
}
