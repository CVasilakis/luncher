package com.luncher.launcher.home

import android.content.Context
import android.graphics.Outline
import android.graphics.Paint
import android.text.TextPaint
import android.view.View
import android.view.ViewOutlineProvider
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.FocusStyle
import com.luncher.domain.appearance.TileStyle
import com.luncher.launcher.ui.WallpaperOutline
import com.luncher.launcher.ui.color
import kotlin.math.ceil
import com.luncher.launcher.ui.R as UiR

/**
 * What every tile draws with besides its own image: the focus frame, the held frame, the dimming
 * of a hidden app, the focused app's name, the corners and the focus style from the home screen's
 * [TileStyle], and the outline a lifted tile's shadow takes.
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

    /** The tiles' corners, from the home screen's style. */
    var corners: Corners = TileStyle().corners

    /** How the focused tile stands out, from the home screen's style. */
    var focusStyle: FocusStyle = TileStyle().focus

    /** Whether the focused tile shows its app's name below it, from the home screen's geometry. */
    var showsNames = false

    /** The app's name below its tile, on the wallpaper: with [nameOutline]'s edge, as the top bar's texts. */
    val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.color(UiR.color.text_primary)
        textSize = context.resources.getDimension(R.dimen.home_tile_name_text)
    }
    val nameOutline = WallpaperOutline(context)

    /** The name's ascent, read once: each read of the font's metrics allocates. */
    val nameAscent: Int

    /** Between a tile's bottom edge and its name's top, before the zoom. */
    val nameGap: Int = context.resources.getDimensionPixelSize(R.dimen.home_tile_name_gap)

    /**
     * How far below a tile its name reaches, the gap included, at the most a tile is zoomed
     * ([AppTileView.LARGEST_ZOOM]): the room the rows leave below each tile while names show
     * ([com.luncher.domain.layout.TileLayouts]). The name is drawn with the tile, so it's zoomed
     * as the tile is, and what the zoom adds above it the gap between the rows already has room for.
     */
    val nameHeight: Int

    init {
        val metrics = namePaint.fontMetricsInt
        nameAscent = metrics.ascent
        nameHeight = ceil(AppTileView.LARGEST_ZOOM * (nameGap + metrics.descent - metrics.ascent)).toInt()
    }

    /**
     * The shape a lifted tile's shadow takes: the tile's, with its corners. Shared by every tile;
     * Android asks it again when a tile's size changes, or the tile asks with a new radius.
     */
    val tileOutline = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, cornerRadius(view.height))
        }
    }

    private val pixelsPerDp = context.resources.displayMetrics.density

    /** The radius of the corners of a tile [tileHeight] pixels high; 0 draws it square. */
    fun cornerRadius(tileHeight: Int): Float = corners.pixels(tileHeight, pixelsPerDp)

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
