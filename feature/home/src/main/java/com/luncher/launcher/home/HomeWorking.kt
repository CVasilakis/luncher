package com.luncher.launcher.home

import android.app.Activity
import android.graphics.Bitmap
import com.luncher.domain.appearance.ColorRoles
import com.luncher.domain.appearance.ThemeAppearance
import com.luncher.domain.appearance.colorRoles
import com.luncher.domain.apps.PreparedTileImages
import com.luncher.domain.pictures.PreparingCause
import com.luncher.domain.wallpaper.DrawingCause
import com.luncher.domain.wallpaper.Wallpaper
import com.luncher.launcher.ui.WorkingDialog

/**
 * The home screen's one window for work it waits for, followed from [start] to [stop]: while the
 * process's [Wallpaper] is drawn (saved in the background editor; again, as the screen changed size
 * or Luncher was updated), or the tiles' pictures are prepared ([PreparedTileImages]: for the same
 * two causes, or one found damaged). A [WorkingDialog] over the home screen says why, taking every
 * key until both are done: a wait the user is told of, not a screen that seems stuck.
 *
 * When both are due, the wallpaper is drawn first (its job comes first), and the window says so,
 * then what's left: one window, its message changed, never closed and opened again in between.
 * Both go on when the home screen stops, and show when it's back.
 */
internal class HomeWorking(
    private val activity: Activity,
    private val wallpaper: Wallpaper<Bitmap>,
    private val tileImages: PreparedTileImages,
) {

    /** Made the first time there's work while the home screen shows. */
    private var working: WorkingDialog? = null

    /** The theme's colors, for the window; a new one takes effect the next time it shows. */
    var roles: ColorRoles = colorRoles(ThemeAppearance())
        set(value) {
            if (value == field) return
            field = value
            if (working?.isShowing != true) working = null
        }

    private val follow: () -> Unit = { apply() }

    fun start() {
        wallpaper.addListener(follow)
        tileImages.addListener(follow)
        apply()
    }

    fun stop() {
        wallpaper.removeListener(follow)
        tileImages.removeListener(follow)
        working?.dismiss()
    }

    private fun apply() {
        val message = message()
        if (message == null) {
            working?.close()
            return
        }
        val working = working ?: WorkingDialog(activity, roles).also { working = it }
        working.show(activity.getString(message))
    }

    /** Why the home screen waits: the wallpaper first, then the tiles' pictures; null when it doesn't. */
    private fun message(): Int? {
        val drawing = wallpaper.state as? Wallpaper.State.Drawing
        if (drawing != null) {
            return when (drawing.cause) {
                DrawingCause.SAVING -> R.string.home_wallpaper_saving
                DrawingCause.SCREEN_CHANGED -> R.string.home_wallpaper_screen_changed
                DrawingCause.OUTDATED -> R.string.home_wallpaper_outdated
            }
        }
        val preparing = tileImages.state as? PreparedTileImages.State.Preparing ?: return null
        return when (preparing.cause) {
            PreparingCause.SCREEN_CHANGED -> R.string.home_tile_images_screen_changed
            PreparingCause.OUTDATED -> R.string.home_tile_images_outdated
        }
    }
}
