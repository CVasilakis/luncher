package com.luncher.domain.wallpaper

import com.luncher.domain.appearance.Background
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.Jobs
import com.luncher.domain.jobs.LatestRequest
import com.luncher.domain.pictures.PictureImages

/**
 * The home screen's wallpaper as it stands: none, ready to show, or being drawn and why. One per
 * process (AppGraph makes it), so every screen that asks sees the same, and a screen follows it
 * while it shows, as a port it can watch
 * (docs/ARCHITECTURE.md#background-work-and-changes-while-shown).
 *
 * The wallpaper is drawn once, at the screen's size, and kept ([StoredWallpaper]), so a start
 * reads it on the main thread and the home screen's first frame has it. When the one kept won't do
 * (another screen size, another version of Luncher, or none kept), it's drawn again in the
 * background, [state] saying why, and replaces the kept one when it's done; the latest request
 * wins. A drawing that has started runs to its end, even if no screen waits for it any more.
 * Without a picture, or with one that's gone, there's no wallpaper, and none is kept.
 *
 * [images] draws a picture at any size and keeps nothing: what's kept is [stored]'s. Used on the
 * main thread only; [onMain] runs a job's result there.
 */
class Wallpaper<Image>(
    private val stored: StoredWallpaper<Image>,
    private val images: PictureImages<Image>,
    private val jobs: Jobs,
    onMain: (() -> Unit) -> Unit,
) {

    /** What the home screen shows: no picture, the picture, or its plain background while the picture is drawn. */
    sealed interface State<out Image> {
        data object None : State<Nothing>

        data class Ready<Image>(val image: Image) : State<Image>

        data class Drawing(val cause: DrawingCause) : State<Nothing>
    }

    var state: State<Image> = State.None
        private set

    private val drawing = LatestRequest(jobs, JobPriority.WALLPAPER, onMain)
    private val listeners = mutableListOf<() -> Unit>()

    /** What [state] is for: a drawing, or null for none; [Unknown] until the first [show]. */
    private var shown: Any? = Unknown

    /**
     * The wallpaper [background] asks for on a screen of [width] x [height] px: the one kept, read
     * at once, if it was drawn from that; else drawn in the background. Nothing changes when it
     * asks for what [state] is already for.
     */
    fun show(background: Background, width: Int, height: Int) {
        val wanted = wallpaperDrawing(background, width, height)
        if (wanted == shown) return
        shown = wanted
        drawing.stop()
        if (wanted == null) {
            // After a drawing that's still running, in the order they came.
            jobs.run(JobPriority.WALLPAPER) { stored.delete() }
            return set(State.None)
        }
        val cause = whyDraw(stored.drawing(), wanted)
        if (cause == null) {
            val image = stored.read(wanted)
            if (image != null) return set(State.Ready(image))
        }
        set(State.Drawing(cause ?: DrawingCause.OUTDATED))   // or kept, but its pixels can't be read
        drawing.load({
            images.render(wanted.picture, wanted.width, wanted.height, wanted.style).also { image ->
                if (image != null) stored.store(wanted, image) else stored.delete()
            }
        }) { image ->
            set(if (image != null) State.Ready(image) else State.None)
        }
    }

    /** Calls [listener] on the main thread whenever [state] changes. */
    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    private fun set(state: State<Image>) {
        if (state == this.state) return
        this.state = state
        for (listener in listeners.toList()) listener()
    }

    private object Unknown
}
