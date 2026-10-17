package com.luncher.domain.wallpaper

import com.luncher.domain.appearance.AppearanceSettings
import com.luncher.domain.appearance.Background
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.Jobs
import com.luncher.domain.jobs.LatestRequest
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.pictures.withoutPictures

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
 * A new background is [save]d through it, not written to [settings] directly: the picture is
 * drawn and kept first, and only then is the background stored, so the setting never names a
 * wallpaper that isn't drawn, and the home screen always has one to show.
 *
 * [images] draws a picture at any size and keeps nothing: what's kept is [stored]'s. Used on the
 * main thread only; [onMain] runs a job's result there.
 */
class Wallpaper<Image>(
    private val stored: StoredWallpaper<Image>,
    private val images: PictureImages<Image>,
    private val settings: AppearanceSettings,
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

    /** Whether a [save] is drawing: until it's stored, [show] asks for the background it replaces. */
    private var saving = false

    /**
     * The wallpaper [background] asks for on a screen of [width] x [height] px: the one kept, read
     * at once, if it was drawn from that; else drawn in the background. Nothing changes when it
     * asks for what [state] is already for.
     */
    fun show(background: Background, width: Int, height: Int) {
        if (saving) return
        val wanted = wallpaperDrawing(background, width, height)
        if (wanted == shown) return
        shown = wanted
        drawing.stop()
        if (wanted == null) return clear()
        val kept = kept(wanted)
        if (kept != null) return set(State.Ready(kept))
        set(State.Drawing(whyDraw(stored.drawing(), wanted) ?: DrawingCause.OUTDATED))   // or kept, but its pixels can't be read
        draw(wanted, keepIfGone = false) { image -> set(if (image != null) State.Ready(image) else State.None) }
    }

    /**
     * Makes [background] the home screen's, on a screen of [width] x [height] px: its picture is
     * drawn and kept, [state] saying it's being saved, and only then is [background] stored. Without a
     * picture, it's stored at once, and the one kept is deleted. [done] runs on the main thread
     * once it's stored; if the picture is gone meanwhile, nothing is, and the wallpaper is what the
     * stored background asks for.
     */
    fun save(background: Background, width: Int, height: Int, done: () -> Unit) {
        val wanted = wallpaperDrawing(background, width, height)
        drawing.stop()
        saving = false
        shown = wanted
        val kept = wanted?.let(::kept)
        if (wanted == null || kept != null) {
            store(background)
            if (kept != null) set(State.Ready(kept)) else clear()
            return done()
        }
        saving = true
        set(State.Drawing(DrawingCause.SAVING))
        draw(wanted, keepIfGone = true) { image ->
            saving = false
            if (image != null) {
                store(background)
                set(State.Ready(image))
            } else {
                shown = Unknown
                show(settings.read().background, width, height)
            }
            done()
        }
    }

    /**
     * The [pictures] deleted: if the wallpaper is one of them, there's none any more. The setting
     * says so at once, then the one kept is deleted, before the picture's own file (its job comes
     * first), so nothing names a picture that's gone.
     */
    fun forget(pictures: Set<PictureId>) {
        val appearance = settings.read()
        val without = withoutPictures(appearance, pictures)
        if (without == appearance) return
        settings.write(without)
        drawing.stop()
        saving = false
        shown = null
        clear()
    }

    /** Calls [listener] on the main thread whenever [state] changes. */
    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    /** The image kept, if it was drawn from [wanted]. */
    private fun kept(wanted: WallpaperDrawing): Image? =
        if (whyDraw(stored.drawing(), wanted) == null) stored.read(wanted) else null

    /**
     * Draws [wanted] in the background and keeps it, then [done] with it, or with null if its
     * picture is gone: then the one kept is deleted, unless [keepIfGone] (a save, which changes
     * nothing when it fails).
     */
    private fun draw(wanted: WallpaperDrawing, keepIfGone: Boolean, done: (Image?) -> Unit) {
        drawing.load({
            images.render(wanted.picture, wanted.width, wanted.height, wanted.style).also { image ->
                if (image != null) stored.store(wanted, image) else if (!keepIfGone) stored.delete()
            }
        }, done)
    }

    /** No wallpaper: none kept, deleted after a drawing that's still running, in the order they came. */
    private fun clear() {
        jobs.run(JobPriority.WALLPAPER) { stored.delete() }
        set(State.None)
    }

    /** [background] as the stored appearance's, written only if it isn't already. */
    private fun store(background: Background) {
        val appearance = settings.read()
        if (appearance.background != background) settings.write(appearance.copy(background = background))
    }

    private fun set(state: State<Image>) {
        if (state == this.state) return
        this.state = state
        for (listener in listeners.toList()) listener()
    }

    private object Unknown
}
