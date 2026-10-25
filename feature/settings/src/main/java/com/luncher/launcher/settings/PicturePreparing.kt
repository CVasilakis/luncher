package com.luncher.launcher.settings

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.luncher.domain.appearance.ColorRoles
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.Jobs
import com.luncher.domain.jobs.LatestRequest
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PreparedPictures
import com.luncher.domain.pictures.isPrepared
import com.luncher.launcher.ui.WorkingDialog

/**
 * A picture editor's preparing, at its start: the library's pictures that aren't prepared for the
 * screen as it is now ([PreparedPictures]: another size, an update, a picture added before), one
 * job each, while a window says which of how many ("Preparing picture 2 of 5…"), so that afterwards
 * nothing the user does waits for a picture to be decoded (docs/ARCHITECTURE.md, Background work
 * point 7). Once all are, [done]; Cancel, or Back, stops the rest and [cancelled]: nothing was
 * chosen yet, so the screen closes. A picture that can't be prepared counts as done: it's drawn
 * from its original then. The screen [stop]s it as it stops, and [start]s it again with what's left
 * when it comes back. In the theme's colors, [roles].
 */
internal class PicturePreparing(
    private val activity: Activity,
    private val prepared: PreparedPictures,
    private val jobs: Jobs,
    private val roles: () -> ColorRoles,
    private val done: () -> Unit,
    private val cancelled: () -> Unit,
) {

    private val main = Handler(Looper.getMainLooper())

    /** The pictures being prepared, a request each, while the window says so. */
    private val preparing = LinkedHashMap<PictureId, LatestRequest>()
    private var total = 0
    private var working: WorkingDialog? = null

    /**
     * Prepares those of [pictures] that aren't, unless it prepares some already; true while it
     * does, so the screen draws nothing from them yet.
     */
    fun start(pictures: List<Picture>): Boolean {
        if (preparing.isNotEmpty()) return true
        val missing = pictures.filter { !prepared.isPrepared(it.id) }
        if (missing.isEmpty()) return false
        total = missing.size
        for (picture in missing) {
            val request = LatestRequest(jobs, JobPriority.PICTURE_SCREENS) { main.post(it) }
            preparing[picture.id] = request
            request.load({ stopped -> prepared.prepare(picture.id, stopped) }) { prepared(picture.id) }
        }
        show()
        return true
    }

    /** As the screen stops: drops what isn't prepared yet, and the window. */
    fun stop() {
        if (preparing.isEmpty()) return
        stopRequests()
        working?.dismiss()
    }

    /** As the screen goes: the window, at once. */
    fun dismiss() {
        working?.dismiss()
    }

    private fun prepared(picture: PictureId) {
        preparing.remove(picture) ?: return
        if (preparing.isNotEmpty()) return show()
        working?.close(done)
    }

    /** The window, saying which picture of how many is prepared; Cancel, or Back, stops and closes. */
    private fun show() {
        val working = working ?: WorkingDialog(activity, roles()).also { working = it }
        working.show(text(), activity.getString(R.string.settings_pictures_cancel)) {
            stopRequests()
            cancelled()
        }
    }

    private fun stopRequests() {
        for (request in preparing.values) request.stop()
        preparing.clear()
    }

    /** "Preparing picture 2 of 5…", or for one, "Preparing the picture…". */
    private fun text(): String =
        if (total == 1) {
            activity.getString(R.string.settings_pictures_preparing)
        } else {
            activity.getString(R.string.settings_pictures_preparing_count, total - preparing.size + 1, total)
        }
}
