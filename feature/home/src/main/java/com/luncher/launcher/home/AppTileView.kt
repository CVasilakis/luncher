package com.luncher.launcher.home

import android.animation.AnimatorInflater
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import com.luncher.domain.apps.AppImages
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.apps.bannerFor
import com.luncher.launcher.ui.color
import com.luncher.launcher.ui.R as UiR

/**
 * One app on the home screen: its banner, and a frame and a slight zoom while it has focus.
 * The image is drawn at the tile's size whenever that size changes (the number of columns, for
 * example), so drawing the tile is a single bitmap copy.
 *
 * While the user arranges apps, a [held] tile has a white frame and a bigger zoom, apart from one
 * that's only focused, and a [hidden] app's tile is dimmed.
 */
@SuppressLint("ViewConstructor") // Created in code only, never from XML.
internal class AppTileView(
    context: Context,
    val app: InstalledApp,
    private val images: AppImages<Bitmap>,
) : View(context) {

    private var image: Bitmap? = null

    private val focusColor = context.color(UiR.color.accent)

    private val framePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.home_tile_frame)
        color = focusColor
    }

    /** Draws the image of a hidden app, dimmed; created for the first one. */
    private var dimPaint: Paint? = null

    /** The user holds this tile, to move it. */
    var held = false
        set(value) {
            if (field == value) return
            field = value
            framePaint.color = if (value) context.color(UiR.color.text_primary) else focusColor
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
            if (value && dimPaint == null) dimPaint = Paint().apply { alpha = HIDDEN_ALPHA }
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
        image = if (w > 0 && h > 0) images.render(app, bannerFor(app), w, h) else null
    }

    override fun onDraw(canvas: Canvas) {
        image?.let { canvas.drawBitmap(it, 0f, 0f, if (hidden) dimPaint else null) }
        if (isFocused || held) {
            val inset = framePaint.strokeWidth / 2
            canvas.drawRect(inset, inset, width - inset, height - inset, framePaint)
        }
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    private companion object {
        const val FOCUSED_ZOOM = 1.1f
        const val HELD_ZOOM = 1.15f
        const val HIDDEN_ALPHA = 102   // 40 %
    }
}
