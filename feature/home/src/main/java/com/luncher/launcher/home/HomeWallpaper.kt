package com.luncher.launcher.home

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import com.luncher.domain.appearance.Background
import com.luncher.domain.wallpaper.Wallpaper
import com.luncher.launcher.ui.TileFrost
import com.luncher.launcher.ui.color
import com.luncher.launcher.ui.screenSize
import com.luncher.launcher.ui.R as UiR

/**
 * The home screen's wallpaper: the process's [Wallpaper], at the screen's size, as [activity]'s
 * window background, followed from [start] to [stop]. The picture once it's ready, the home
 * screen's plain color without one, and while it's drawn (saved in the background editor; again,
 * as the screen changed size or Luncher was updated), when [HomeWorking]'s window says why.
 *
 * A start reads a wallpaper kept on the main thread (in [Wallpaper.show]), so the first frame has
 * it, as the tiles' images. A drawing goes on when the home screen stops, and shows when it's back.
 * [onPicture] is told whether a picture shows, the first time and whenever that changes;
 * [onFrost], the frosted copy of the picture shown, the Frosted tiles' backdrop, or none, whenever
 * it changes.
 */
internal class HomeWallpaper(
    private val activity: Activity,
    private val wallpaper: Wallpaper<Bitmap>,
    private val onPicture: (Boolean) -> Unit,
    private val onFrost: (TileFrost?) -> Unit,
) {

    private val plain = ColorDrawable(activity.color(UiR.color.background))

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
    }

    /** Asks for [background]'s wallpaper, which shows as [Wallpaper] tells, between [start] and [stop]. */
    fun show(background: Background) {
        val screen = activity.screenSize()
        wallpaper.show(background, screen.width, screen.height)
    }

    private fun apply(state: Wallpaper.State<Bitmap>) {
        val ready = state as? Wallpaper.State.Ready
        setImage(ready?.image, ready?.frost)
    }

    private fun setImage(image: Bitmap?, frost: Bitmap?) {
        if (applied && image === shown) return
        val pictureChanged = !applied || (image == null) != (shown == null)
        applied = true
        shown = image
        activity.window.setBackgroundDrawable(if (image == null) plain else BitmapDrawable(activity.resources, image))
        if (pictureChanged) onPicture(image != null)
        // The frost covers what the wallpaper does: the window, which it fills at its size, the screen's.
        onFrost(if (image != null && frost != null) TileFrost(frost, image.width, image.height) else null)
    }
}
