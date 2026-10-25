package com.luncher.launcher.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.apps.AppImages
import com.luncher.domain.apps.HomeApp
import com.luncher.domain.apps.TileImage
import com.luncher.domain.apps.ownBanner
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.Jobs
import com.luncher.domain.jobs.LatestRequest
import com.luncher.domain.layout.TileShape
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.pictures.PictureStyle

/**
 * The tile image picker's preview: an app's tile as the draft would show it, at the view's size (a
 * tile's shape, [TileShape], which the screen gives it), its corners as round for its size as the
 * home screen's tiles' ([style]). The app's own image is drawn at once, on the main thread, as the
 * home screen draws a tile's, by the uncached [AppImages] (the tiles' kept images are at the tile's
 * size); a picture is drawn in the background from its working copy ([PictureImages]), the latest
 * request winning, the image shown before staying meanwhile, and the app's own image in its place
 * where it can't be drawn, as on the tile. Each is drawn again only when what it's drawn from (the
 * draft, the size) changes. Not mirrored in a right-to-left language, as the home screen isn't.
 */
internal class TilePreviewView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    /** What [image] was drawn from: an app's own image ([picture] null), or a picture framed so, at a size. */
    private data class Drawing(val app: HomeApp, val picture: PictureId?, val framing: Framing, val width: Int, val height: Int)

    private var banners: AppImages<Bitmap>? = null
    private var pictures: PictureImages<Bitmap>? = null
    private var loading: LatestRequest? = null

    private var app: HomeApp? = null
    private var choice = TileImage(null)

    private var corners: Corners? = null
    private var geometry = TileGeometry()
    private var radius = 0f

    /** The image shown, drawn at the view's size; null before the first. */
    private var image: Bitmap? = null

    /** What [image] was drawn from, so asking for it again does nothing. */
    private var drawnFrom: Drawing? = null

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val bounds = Rect()

    init {
        // Its corners rounded as a tile's: cut along its outline, by the GPU.
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        clipToOutline = true
        // The home screen's tiles don't mirror.
        layoutDirection = LAYOUT_DIRECTION_LTR
    }

    /** Draws an app's own image with [banners], and a picture with [pictures], in [jobs]. */
    fun drawWith(banners: AppImages<Bitmap>, pictures: PictureImages<Bitmap>, jobs: Jobs) {
        this.banners = banners
        this.pictures = pictures
        loading = LatestRequest(jobs, JobPriority.PICTURE_SCREENS) { Handler(Looper.getMainLooper()).post(it) }
        load()
    }

    /** Shows [app]'s tile with [choice]: its picture so framed, or the app's own image for none. */
    fun show(app: HomeApp, choice: TileImage) {
        if (app == this.app && choice == this.choice) return
        this.app = app
        this.choice = choice
        load()
    }

    /** The corners of the home screen's tiles, [corners], on tiles of [geometry]: as round for the preview's size. */
    fun style(corners: Corners, geometry: TileGeometry) {
        if (corners == this.corners && geometry == this.geometry) return
        this.corners = corners
        this.geometry = geometry
        placeCorners()
    }

    /** Drops the picture being drawn, which [resume] draws again. */
    fun stop() {
        loading?.stop()
    }

    /** Draws the picture [stop] dropped, if one was. */
    fun resume() = load()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        placeCorners()
        load()
    }

    override fun onDraw(canvas: Canvas) {
        val image = image ?: return
        bounds.set(0, 0, width, height)
        canvas.drawBitmap(image, null, bounds, paint)
    }

    private fun placeCorners() {
        radius = corners?.let { TileShape.cornerRadius(it, geometry, height) } ?: 0f
        invalidateOutline()
    }

    /** The draft's image: none to do if it's the one shown; the app's own at once; a picture in the background. */
    private fun load() {
        val app = app ?: return
        val banners = banners ?: return
        val pictures = pictures ?: return
        val loading = loading ?: return
        if (width <= 0 || height <= 0) return
        val picture = choice.picture
        val wanted = Drawing(app, picture, if (picture == null) Framing() else choice.framing, width, height)
        if (wanted == drawnFrom) {
            loading.stop()
            return
        }
        if (picture == null) {
            loading.stop()
            return shown(banners.render(app, ownBanner(app), width, height), wanted)
        }
        val width = width
        val height = height
        loading.load({ stopped -> pictures.render(picture, width, height, PictureStyle(wanted.framing), stopped) }) { drawn ->
            // Not in the library any more, or its working copy unreadable: the app's own, as the tile shows.
            shown(drawn ?: banners.render(app, ownBanner(app), width, height), wanted)
        }
    }

    private fun shown(image: Bitmap, from: Drawing) {
        this.image = image
        drawnFrom = from
        invalidate()
    }
}
