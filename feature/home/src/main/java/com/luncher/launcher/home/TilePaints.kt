package com.luncher.launcher.home

import android.content.Context
import android.graphics.Paint
import com.luncher.launcher.ui.color
import com.luncher.launcher.ui.R as UiR

/**
 * What every tile draws with besides its own image: the focus frame, the held frame, the dimming
 * of a hidden app, and the corner radius from the home screen's [com.luncher.domain.appearance.TileStyle].
 * [AppTilesView] holds the one set its tiles share, so a style change updates it once and redraws,
 * without touching each tile's own state.
 */
internal class TilePaints(context: Context) {

    /** A focused tile's frame. */
    val focusFrame = frame(context.color(UiR.color.accent), context)

    /** A held tile's frame, while the user arranges apps. */
    val heldFrame = frame(context.color(UiR.color.text_primary), context)

    /** Draws a hidden app's image, dimmed. */
    val dim = Paint().apply { alpha = HIDDEN_ALPHA }

    /** The radius of the tiles' corners, in pixels; 0 draws them square. */
    var cornerRadius = 0f

    private fun frame(color: Int, context: Context) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.getDimension(R.dimen.home_tile_frame)
        this.color = color
    }

    companion object {
        const val HIDDEN_ALPHA = 102   // 40 %
        const val OPAQUE = 255
    }
}
