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
import com.luncher.domain.appearance.FocusStyle
import com.luncher.domain.apps.AppImages
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.apps.bannerFor
import com.luncher.domain.layout.PixelBounds

/**
 * One app on the home screen: its banner, and while it has focus, as [TilePaints.focusStyle] says,
 * a frame and a slight zoom, or no frame, a bigger zoom and a shadow, as if lifted. The image is
 * drawn at the tile's size whenever that size changes (the number of columns, for example), so
 * drawing the tile is a single bitmap copy. With rounded corners ([TilePaints]), the same bitmap
 * fills a rounded rectangle through a shader made with it, and the frame and the shadow follow the
 * corners: a new radius only redraws, with the image as it is.
 *
 * The shadow is Android's own, cast by the tile's outline as it rises (its translationZ, in the
 * state animator), drawn by the GPU: nothing is blurred or allocated for it. With
 * [TilePaints.showsNames], the focused tile's app name shows centered below it, cut to the
 * tile's width, fading in and out with the focus; drawn with the tile, outside its bounds, which
 * [AppTilesView] doesn't clip, so it zooms with it.
 *
 * While the user moves its app, a [held] tile has a white frame and the largest zoom, apart from
 * one that's only focused (lifted, that one is as big: the frame tells them apart).
 */
@SuppressLint("ViewConstructor") // Created in code only, never from XML.
internal class AppTileView(
    context: Context,
    val app: InstalledApp,
    private val images: AppImages<Bitmap>,
    /** Shared by every tile: the frames, the name's paint, the corners and the focus style. */
    private val paints: TilePaints,
) : View(context) {

    private var image: Bitmap? = null

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

    /** The focus style the state animator is for; null before the first. */
    private var focusStyle: FocusStyle? = null

    /** The user holds this tile, to move it. */
    var held = false
        set(value) {
            if (field == value) return
            field = value
            val zoom = when {
                value -> HELD_ZOOM
                isFocused -> focusedZoom()   // as the focus animator leaves it
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
        contentDescription = app.label
        outlineProvider = paints.tileOutline
        restyle()
    }

    /**
     * Takes the style [paints] have now: a new focus style swaps the state animator, which zooms
     * and lifts the tile, and puts it at once where the new one has it; new corners give the
     * shadow a new outline. Then redraws.
     */
    fun restyle() {
        val style = paints.focusStyle
        if (style != focusStyle) {
            focusStyle = style
            translationZ = 0f   // the frame's animator doesn't lift, so it wouldn't bring the tile down
            stateListAnimator = AnimatorInflater.loadStateListAnimator(context, focusAnimator(style))
            jumpDrawablesToCurrentState()
        }
        invalidateOutline()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val image = if (w > 0 && h > 0) images.render(app, bannerFor(app), w, h) else null
        this.image = image
        imagePaint.shader = image?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
        val inset = paints.focusFrame.strokeWidth / 2
        frameBounds.set(inset, inset, w - inset, h - inset)
        name = TextUtils.ellipsize(app.label, paints.namePaint, w.toFloat(), TextUtils.TruncateAt.END).toString()
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
            isFocused && paints.focusStyle == FocusStyle.FRAME -> paints.focusFrame
            else -> null
        }
        if (frame != null) {
            // The line's middle runs half its width inside the edge, its corners as much tighter.
            val frameRadius = (radius - frame.strokeWidth / 2).coerceAtLeast(0f)
            canvas.drawRoundRect(frameBounds, frameRadius, frameRadius, frame)
        }
        if (paints.showsNames) drawName(canvas)
    }

    /** The app's name below the tile, as faded in as the focus has it, with its outline. */
    private fun drawName(canvas: Canvas) {
        val shown = when {
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
        if (paints.showsNames) {
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
        val grownX = (width * (focusedZoom() - 1) / 2).toInt()
        val grownY = (height * (focusedZoom() - 1) / 2).toInt()
        val bottom = if (paints.showsNames) top + height + paints.nameHeight else top + height + grownY
        return PixelBounds(left - grownX, top - grownY, left + width + grownX, bottom)
    }

    private fun focusedZoom() = when (paints.focusStyle) {
        FocusStyle.FRAME -> FRAME_ZOOM
        FocusStyle.LIFT -> LIFT_ZOOM
    }

    private fun focusAnimator(style: FocusStyle) = when (style) {
        FocusStyle.FRAME -> R.animator.home_tile_focus
        FocusStyle.LIFT -> R.animator.home_tile_lift
    }

    companion object {
        /** The focused tile's zoom, framed (home_tile_focus.xml) and lifted (home_tile_lift.xml). */
        const val FRAME_ZOOM = 1.1f
        const val LIFT_ZOOM = 1.15f

        /** A held tile's zoom, while the user arranges apps. */
        const val HELD_ZOOM = 1.15f

        /** The most a tile grows, framed, lifted or held. */
        const val LARGEST_ZOOM = HELD_ZOOM
    }
}
