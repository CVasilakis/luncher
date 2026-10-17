package com.luncher.launcher.home

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import com.luncher.domain.appearance.Background
import com.luncher.domain.wallpaper.DrawingCause
import com.luncher.domain.wallpaper.Wallpaper
import com.luncher.launcher.ui.WorkingDialog
import com.luncher.launcher.ui.color
import com.luncher.launcher.ui.screenSize
import com.luncher.launcher.ui.R as UiR

/**
 * The home screen's wallpaper: the process's [Wallpaper], at the screen's size, as [activity]'s
 * window background, followed from [start] to [stop]. The picture once it's ready, the home
 * screen's plain color without one. While it's drawn (saved in the background editor; again, as
 * the screen changed size or Luncher was updated), the plain color, and a [WorkingDialog] over the home screen says why, taking every key
 * until the picture shows: a wait the user is told of, not a screen that seems stuck.
 *
 * A start reads a wallpaper kept on the main thread (in [Wallpaper.show]), so the first frame has
 * it, as the tiles' images. A drawing goes on when the home screen stops, and shows when it's back.
 */
internal class HomeWallpaper(private val activity: Activity, private val wallpaper: Wallpaper<Bitmap>) {

    private val plain = ColorDrawable(activity.color(UiR.color.background))

    /** Made the first time the wallpaper is drawn while the home screen shows. */
    private var working: WorkingDialog? = null

    /** The image the window shows, null for the plain color, once [applied]. */
    private var shown: Bitmap? = null
    private var applied = false

    private val follow: () -> Unit = { apply(wallpaper.state) }

    fun start() {
        wallpaper.addListener(follow)
        apply(wallpaper.state)
    }

    fun stop() {
        wallpaper.removeListener(follow)
        working?.dismiss()
    }

    /** Asks for [background]'s wallpaper, which shows as [Wallpaper] tells, between [start] and [stop]. */
    fun show(background: Background) {
        val screen = activity.screenSize()
        wallpaper.show(background, screen.width, screen.height)
    }

    private fun apply(state: Wallpaper.State<Bitmap>) {
        when (state) {
            is Wallpaper.State.Ready -> {
                setImage(state.image)
                working?.close()
            }
            Wallpaper.State.None -> {
                setImage(null)
                working?.close()
            }
            is Wallpaper.State.Drawing -> {
                setImage(null)
                val working = working ?: WorkingDialog(activity).also { working = it }
                working.show(activity.getString(message(state.cause)))
            }
        }
    }

    private fun message(cause: DrawingCause): Int = when (cause) {
        DrawingCause.SAVING -> R.string.home_wallpaper_saving
        DrawingCause.SCREEN_CHANGED -> R.string.home_wallpaper_screen_changed
        DrawingCause.OUTDATED -> R.string.home_wallpaper_outdated
    }

    private fun setImage(image: Bitmap?) {
        if (applied && image === shown) return
        applied = true
        shown = image
        activity.window.setBackgroundDrawable(if (image == null) plain else BitmapDrawable(activity.resources, image))
    }
}
