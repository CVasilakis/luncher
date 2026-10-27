package com.luncher.launcher.home

import android.content.Context
import android.graphics.Outline
import android.text.TextPaint
import android.view.View
import android.view.ViewOutlineProvider
import com.luncher.domain.appearance.AppName
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.TileStyle
import com.luncher.launcher.ui.TileName
import com.luncher.launcher.ui.TilePainter
import com.luncher.launcher.ui.WallpaperOutline

/**
 * What every tile draws with besides its own parts: the painter (:ui's [TilePainter], its frames
 * in the theme's accent, [AppTilesView.accent]), the focused app's name, the corners and whether
 * the focused tile is framed, from the home screen's [TileStyle], and the outline a lifted tile's
 * shadow takes. [AppTilesView] holds the one set its tiles share, so a style change updates it
 * once and redraws, without touching each tile's own state.
 */
internal class TilePaints(context: Context) {

    /** Draws each tile, its image and its look, the focus frame and the held frame. */
    val painter = TilePainter(context)

    /** The tiles' corners, from the home screen's style. */
    var corners: Corners = TileStyle().corners

    /** Whether the focused tile is framed, else lifted, from the home screen's style. */
    var framed: Boolean = TileStyle().focusFrame

    /**
     * Which tiles show their app's name below them, from the home screen's geometry: none, the
     * focused one, or every one. It starts as the default geometry has it, as [AppTilesView]'s
     * does: the view sets it only when its geometry changes, so a different start would leave the
     * rows room for names never drawn.
     */
    var names: AppName = TileGeometry().appName

    /** Whether any tile shows its name ([names]): the rows leave room for it then. */
    val showsNames: Boolean get() = names != AppName.OFF

    /**
     * The app's name below its tile, as :ui's [TileName] has it, so the appearance editor's preview
     * leaves it the same room; on the wallpaper, with [nameOutline]'s edge, as the top bar's texts.
     */
    private val name = TileName(context)
    val namePaint: TextPaint = name.paint
    val nameOutline = WallpaperOutline(context)

    /** The name's ascent, and the gap between a tile's bottom edge and the name's top, before the zoom. */
    val nameAscent: Int = name.ascent
    val nameGap: Int = name.gap

    /**
     * How far below a tile its name reaches, the gap included, at the most a tile is zoomed
     * ([AppTileView.LARGEST_ZOOM]): the room the rows leave below each tile while names show
     * ([com.luncher.domain.layout.TileLayouts]). What the zoom adds above the name, the gap
     * between the rows already has room for.
     */
    val nameHeight: Int = name.room(AppTileView.LARGEST_ZOOM)

    /**
     * The shape a lifted tile's shadow takes: the tile's, with its corners, as the painter gives it
     * ([TilePainter.outline]: a pixel inside its edge, as strong as the tile's parts say on this
     * screen, lighter under a see-through picture's glass, whole under a Frosted tile's frost).
     * Shared by every tile; Android asks it again when a tile's size changes, or the tile asks with
     * a new radius, new parts or, Frosted, a new frost.
     */
    val tileOutline = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            painter.outline(outline, view.width, view.height, cornerRadius(view.height), (view as? AppTileView)?.parts)
        }
    }

    private val pixelsPerDp = context.resources.displayMetrics.density

    /** The radius of the corners of a tile [tileHeight] pixels high; 0 draws it square. */
    fun cornerRadius(tileHeight: Int): Float = corners.pixels(tileHeight, pixelsPerDp)

    companion object {
        const val OPAQUE = 255
    }
}
