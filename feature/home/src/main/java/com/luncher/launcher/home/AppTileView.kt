package com.luncher.launcher.home

import android.animation.AnimatorInflater
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import com.luncher.domain.apps.AppImages
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.apps.bannerFor

/**
 * One app on the home screen: its banner, and a frame and a slight zoom while it has focus.
 * The image is drawn at the tile's size whenever that size changes (the number of columns, for
 * example), so drawing the tile is a single bitmap copy. With rounded corners ([TilePaints]), the
 * same bitmap fills a rounded rectangle through a shader made with it, and the frame follows the
 * corners: a new radius only redraws, with the image as it is.
 *
 * While the user arranges apps, a [held] tile has a white frame and a bigger zoom, apart from one
 * that's only focused, and a [hidden] app's tile is dimmed.
 */
@SuppressLint("ViewConstructor") // Created in code only, never from XML.
internal class AppTileView(
    context: Context,
    val app: InstalledApp,
    private val images: AppImages<Bitmap>,
    /** Shared by every tile: the frames, the dimming and the corner radius. */
    private val paints: TilePaints,
) : View(context) {

    private var image: Bitmap? = null

    /** Fills the tile's rounded shape with [image]; its shader is made with each new image. */
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** The tile's bounds, and its frame's line, set with each new size. */
    private val bounds = RectF()
    private val frameBounds = RectF()

    /** The user holds this tile, to move it. */
    var held = false
        set(value) {
            if (field == value) return
            field = value
            val zoom = when {
                value -> HELD_ZOOM
                isFocused -> FOCUSED_ZOOM   // as the focus animator leaves it (home_tile_focus.xml)
                else -> 1f
            }
            animate().scaleX(zoom).scaleY(zoom)
            invalidate()
        }

    /** The app is hidden: its tile is on the shelf while the user arranges apps. */
    var hidden = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    init {
        isFocusable = true
        // The OK key opens the app, whether or not Android thinks it's in touch mode.
        isFocusableInTouchMode = true
        isClickable = true
        contentDescription = app.label
        stateListAnimator = AnimatorInflater.loadStateListAnimator(context, R.animator.home_tile_focus)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val image = if (w > 0 && h > 0) images.render(app, bannerFor(app), w, h) else null
        this.image = image
        imagePaint.shader = image?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
        val inset = paints.focusFrame.strokeWidth / 2
        frameBounds.set(inset, inset, w - inset, h - inset)
    }

    override fun onDraw(canvas: Canvas) {
        val radius = paints.cornerRadius(height)
        val image = image
        if (image != null) {
            if (radius == 0f) {
                canvas.drawBitmap(image, 0f, 0f, if (hidden) paints.dim else null)
            } else {
                imagePaint.alpha = if (hidden) TilePaints.HIDDEN_ALPHA else TilePaints.OPAQUE
                canvas.drawRoundRect(bounds, radius, radius, imagePaint)
            }
        }
        if (isFocused || held) {
            val frame = if (held) paints.heldFrame else paints.focusFrame
            // The line's middle runs half its width inside the edge, its corners as much tighter.
            val frameRadius = (radius - frame.strokeWidth / 2).coerceAtLeast(0f)
            canvas.drawRoundRect(frameBounds, frameRadius, frameRadius, frame)
        }
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    private companion object {
        const val FOCUSED_ZOOM = 1.1f
        const val HELD_ZOOM = 1.15f
    }
}
