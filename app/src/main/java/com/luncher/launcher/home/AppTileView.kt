package com.luncher.launcher.home

import android.animation.AnimatorInflater
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.apps.bannerFor
import com.luncher.launcher.R

/**
 * One app on the home screen: its banner, and a frame and a slight zoom while it has focus.
 * The image is drawn at the tile's size whenever that size changes (the number of columns, for
 * example), so drawing the tile is a single bitmap copy.
 */
@SuppressLint("ViewConstructor") // Created in code only, never from XML.
class AppTileView(context: Context, val app: InstalledApp, private val banners: BannerImages) : View(context) {

    private var image: Bitmap? = null

    private val framePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.home_tile_frame)
        @Suppress("DEPRECATION") // Context.getColor needs API 23.
        color = resources.getColor(R.color.accent)
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
        image = if (w > 0 && h > 0) banners.render(app, bannerFor(app), w, h) else null
    }

    override fun onDraw(canvas: Canvas) {
        image?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        if (isFocused) {
            val inset = framePaint.strokeWidth / 2
            canvas.drawRect(inset, inset, width - inset, height - inset, framePaint)
        }
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }
}
