package com.luncher.launcher.home

import android.animation.AnimatorInflater
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextUtils
import android.view.View
import com.luncher.domain.appearance.AppName
import com.luncher.domain.apps.AppImages
import com.luncher.domain.apps.Banner
import com.luncher.domain.apps.HomeApp
import com.luncher.domain.apps.bannerFor
import com.luncher.domain.layout.PixelBounds
import com.luncher.domain.pictures.PictureId

/**
 * One app on the home screen: its banner, and while it has focus, a zoom with, as
 * [TilePaints.framed] says, a frame inside its edge or a shadow, as if lifted: as big either way,
 * since the frame takes no room outside the tile. The image is drawn at the tile's size whenever
 * that size changes (the number of columns, for example), so drawing the tile is a single bitmap
 * copy. With rounded corners ([TilePaints]), the same bitmap fills a rounded rectangle through a
 * shader made with it, and the frame and the shadow follow the corners: a new radius only redraws,
 * with the image as it is.
 *
 * The shadow is Android's own, cast by the tile's outline as it rises (its translationZ, in the
 * state animator), drawn by the GPU: nothing is blurred or allocated for it. As
 * [TilePaints.names] says, the app's name shows centered below the tile, cut to the tile's width:
 * below the focused one only, fading in and out with the focus, or below every one at all times;
 * drawn with the tile, outside its bounds, which [AppTilesView] doesn't clip, so it zooms with it.
 *
 * While the user moves its app, a [held] tile has a white frame, and the focused one's zoom: the
 * frame tells it apart from one only focused.
 *
 * A tile whose picture of the library isn't prepared for this screen shows its app's own image
 * until it is, then is drawn again with it ([followPreparing]).
 */
@SuppressLint("ViewConstructor") // Created in code only, never from XML.
internal class AppTileView(
    context: Context,
    val app: HomeApp,
    private val images: AppImages<Bitmap>,
    /** Shared by every tile: the frames, the name's paint, the corners and whether the focused tile is framed. */
    private val paints: TilePaints,
) : View(context) {

    private var image: Bitmap? = null

    /** The picture of the library the user chose for the tile, if any. */
    private val picture: PictureId? = (bannerFor(app) as? Banner.Picture)?.picture

    /** Whether [picture] was being prepared, the tile showing its app's own image meanwhile. */
    private var waitsForPicture = false

    /** Fills the tile's rounded shape with [image]; its shader is made with each new image. */
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** The tile's bounds, and its frame's line, set with each new size. */
    private val bounds = RectF()
    private val frameBounds = RectF()

    /** The app's name as it fits the tile's width, and where it starts: set with each new size. */
    private var name = ""
    private var nameLeft = 0f

    /**
     * The name's fade, with the focus: forward as it comes, back as it goes. Made once, so a key
     * press only starts it.
     */
    private val nameFade = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
        addUpdateListener { invalidate() }
    }

    /** Whether the state animator is the framed tile's; null before the first. */
    private var framed: Boolean? = null

    /** The user holds this tile, to move it. */
    var held = false
        set(value) {
            if (field == value) return
            field = value
            val zoom = when {
                value -> HELD_ZOOM
                isFocused -> FOCUSED_ZOOM   // as the focus animator leaves it
                else -> 1f
            }
            animate().scaleX(zoom).scaleY(zoom)
            invalidate()
        }

    init {
        isFocusable = true
        // The OK key opens the app, whether or not Android thinks it's in touch mode.
        isFocusableInTouchMode = true
        isClickable = true
        contentDescription = app.name
        outlineProvider = paints.tileOutline
        restyle()
    }

    /**
     * Takes the style [paints] have now: the frame turned on or off swaps the state animator,
     * which zooms the tile, and lifts it without the frame, and puts it at once where the new one
     * has it; new corners give the shadow a new outline. Then redraws.
     */
    fun restyle() {
        val frame = paints.framed
        if (frame != framed) {
            framed = frame
            translationZ = 0f   // the frame's animator doesn't lift, so it wouldn't bring the tile down
            stateListAnimator = AnimatorInflater.loadStateListAnimator(
                context,
                if (frame) R.animator.home_tile_focus else R.animator.home_tile_lift,
            )
            jumpDrawablesToCurrentState()
        }
        invalidateOutline()
        invalidate()
    }

    /**
     * Follows its picture's preparing, as [preparing] says it is now: once it's done, the tile is
     * drawn again, with its picture, nothing else laid out. A tile without a picture does nothing.
     */
    fun followPreparing(preparing: (PictureId) -> Boolean) {
        val picture = picture ?: return
        if (preparing(picture)) {
            waitsForPicture = true
        } else if (waitsForPicture) {
            waitsForPicture = false
            render(width, height)
            invalidate()
        }
    }

    /** The image drawn at [w] x [h] px, its shader with it; none while the tile has no size. */
    private fun render(w: Int, h: Int) {
        val image = if (w > 0 && h > 0) images.render(app, bannerFor(app), w, h) else null
        this.image = image
        imagePaint.shader = image?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        render(w, h)
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
        val inset = paints.focusFrame.strokeWidth / 2
        frameBounds.set(inset, inset, w - inset, h - inset)
        name = TextUtils.ellipsize(app.name, paints.namePaint, w.toFloat(), TextUtils.TruncateAt.END).toString()
        nameLeft = (w - paints.namePaint.measureText(name)) / 2
        invalidateOutline()
    }

    override fun onDraw(canvas: Canvas) {
        val radius = paints.cornerRadius(height)
        val image = image
        if (image != null) {
            if (radius == 0f) {
                canvas.drawBitmap(image, 0f, 0f, null)
            } else {
                canvas.drawRoundRect(bounds, radius, radius, imagePaint)
            }
        }
        val frame = when {
            held -> paints.heldFrame
            isFocused && paints.framed -> paints.focusFrame
            else -> null
        }
        if (frame != null) {
            // The line's middle runs half its width inside the edge, its corners as much tighter.
            val frameRadius = (radius - frame.strokeWidth / 2).coerceAtLeast(0f)
            canvas.drawRoundRect(frameBounds, frameRadius, frameRadius, frame)
        }
        if (paints.showsNames) drawName(canvas)
    }

    /** The app's name below the tile, whole, or as faded in as the focus has it, with its outline. */
    private fun drawName(canvas: Canvas) {
        val shown = when {
            paints.names == AppName.ALWAYS -> 1f
            nameFade.isRunning -> nameFade.animatedFraction
            isFocused -> 1f
            else -> 0f
        }
        if (shown <= 0f) return
        val paint = paints.namePaint
        val outline = paints.nameOutline
        val alpha = (shown * TilePaints.OPAQUE).toInt()
        val baseline = (height + paints.nameGap - paints.nameAscent).toFloat()
        outline.under(paint) {
            paint.alpha = Color.alpha(outline.color) * alpha / TilePaints.OPAQUE
            canvas.drawText(name, nameLeft, baseline, paint)
        }
        paint.alpha = alpha
        canvas.drawText(name, nameLeft, baseline, paint)
        paint.alpha = TilePaints.OPAQUE
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (paints.names == AppName.WHEN_FOCUSED) {
            // Back the way it came if it's still fading, else in from nothing or out from whole.
            if (nameFade.isRunning || !gainFocus) nameFade.reverse() else nameFade.start()
        }
        invalidate()
    }

    /**
     * Where the tile shows on the screen, in pixels, as it's drawn while it has focus: zoomed, and
     * with its name below it while names show. Not as it's drawn now, which the zoom's animation
     * may be changing.
     */
    fun focusedBounds(): PixelBounds {
        val parent = parent as View
        val location = IntArray(2)
        parent.getLocationOnScreen(location)
        val left = location[0] + this.left - parent.scrollX
        val top = location[1] + this.top - parent.scrollY
        // Zoomed around its middle, where the state animator's zoom leaves the pivot.
        val grownX = (width * (FOCUSED_ZOOM - 1) / 2).toInt()
        val grownY = (height * (FOCUSED_ZOOM - 1) / 2).toInt()
        val bottom = if (paints.showsNames) top + height + paints.nameHeight else top + height + grownY
        return PixelBounds(left - grownX, top - grownY, left + width + grownX, bottom)
    }

    companion object {
        /** The focused tile's zoom, framed (home_tile_focus.xml) or lifted (home_tile_lift.xml). */
        const val FOCUSED_ZOOM = 1.15f

        /** A held tile's zoom, while the user moves its app: the focused one's. */
        const val HELD_ZOOM = FOCUSED_ZOOM

        /** The most a tile grows, focused or held. */
        const val LARGEST_ZOOM = FOCUSED_ZOOM
    }
}
