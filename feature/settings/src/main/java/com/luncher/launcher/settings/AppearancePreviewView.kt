package com.luncher.launcher.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.colorRoles
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.Jobs
import com.luncher.domain.jobs.LatestRequest
import com.luncher.domain.layout.TileLayouts
import com.luncher.domain.layout.TopBarItem
import com.luncher.domain.layout.topBarLayout
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.wallpaper.Wallpaper
import com.luncher.domain.wallpaper.WallpaperDrawing
import com.luncher.domain.wallpaper.wallpaperDrawing
import com.luncher.launcher.ui.TileName
import com.luncher.launcher.ui.WallpaperOutline
import com.luncher.launcher.ui.color
import com.luncher.launcher.ui.screenSize
import com.luncher.launcher.ui.R as UiR
import kotlin.math.roundToInt

/**
 * An illustration of the home screen with an [appearance]: outlined tiles with a light fill, in
 * place of the banners, on its background (the wallpaper's picture, drawn at the sample screen's
 * size, or the plain color), plain blocks for the time, the date and the settings gear; no names,
 * images or text. Not the real home screen: a made-up one, [SCREEN_WIDTH] dp wide and of the
 * TV's shape ([screenSize], as the wallpaper is drawn: 540 dp tall on a 16:9 TV), with [TILES]
 * tiles, chosen so every option's values look different: a full row and a short one, so left and
 * right differ, and the first tile focused, so the focus frame shows: on, the frame inside its
 * edge; off, the tile outlined as the others, lifted with a shadow (a dozen faint round rectangles
 * around it, each a little wider and lower, not a blur); and with the app name shown, a block
 * below it, in the room the rows leave it. The width stays, so a row holds as many tiles on every
 * shape.
 *
 * It's laid out by the same rules as the home screen, [TileLayouts] and [topBarLayout], at its own
 * density (its pixels per sample dp), so every dp value scales with it, and it can't disagree with
 * the home screen. Left to right in every language, like the home screen. What it draws from is
 * made anew only when the part of the appearance it comes from changes, compared with `==`, and
 * drawing allocates nothing. The wallpaper is the home screen's own when it's of this background
 * ([Wallpaper.readyFor]): it's in memory already, drawn and kept, so it's only [shrunk], and
 * shows in the first frame, also the first after the TV started. Else it's drawn in the
 * background ([showWallpapers]), from the picture's working copy, the latest request winning.
 * Either is kept, and not drawn again, while the background and the size stay the same.
 *
 * What's on the wallpaper has the home screen's edge ([WallpaperOutline]), so the illustration
 * reads on a white picture as the home screen does: the blocks (the name's too) and the tiles'
 * outlines, at the sample's scale, a pixel at least; not the focus frame, which the home screen's
 * has none of.
 */
internal class AppearancePreviewView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    init {
        // Its corners rounded, the drawn screen's: cut along its outline, by the GPU.
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(screen.left.roundToInt(), screen.top.roundToInt(), screen.right.roundToInt(), screen.bottom.roundToInt(), cornerRadius)
            }
        }
        clipToOutline = true
    }

    private val cornerRadius = resources.getDimension(R.dimen.settings_preview_corner)

    var appearance: Appearance = Appearance()
        set(value) {
            val old = field
            if (old == value) return
            field = value
            if (value.tileGeometry != old.tileGeometry) placeTiles()
            // The radius depends on the tiles' height too, at most half of it.
            if (value.tileStyle != old.tileStyle || value.tileGeometry != old.tileGeometry) styleTiles()
            if (value.topBar != old.topBar) placeBar()
            if (value.background != old.background) loadWallpaper()
            // The accent's, as the home screen frames its focused tile; the mode isn't the home screen's.
            if (value.theme.accent != old.theme.accent) focusPaint.color = colorRoles(value.theme).homeAccent
            if (value.theme != old.theme) edgePaint.color = colorRoles(value.theme).outlineVariant
            invalidate()
        }

    private var images: PictureImages<Bitmap>? = null
    private var home: Wallpaper<Bitmap>? = null
    private var loading: LatestRequest? = null

    /** The background's picture as last drawn, at the screen's size; null for none. */
    private var wallpaper: Bitmap? = null

    /** What [wallpaper] was drawn from, so asking for it again does nothing; null while it's none. */
    private var drawnFrom: WallpaperDrawing? = null

    /**
     * Draws the background's picture: [home]'s image when it's of that background, else with
     * [images], in [jobs]; until then, none is drawn.
     */
    fun showWallpapers(home: Wallpaper<Bitmap>, images: PictureImages<Bitmap>, jobs: Jobs) {
        this.home = home
        this.images = images
        loading = LatestRequest(jobs, JobPriority.PICTURE_SCREENS) { Handler(Looper.getMainLooper()).post(it) }
        loadWallpaper()
    }

    /** Drops the picture being drawn, which [resumeWallpaper] draws again. */
    fun stopWallpaper() {
        loading?.stop()
    }

    /**
     * Draws the picture [stopWallpaper] dropped, if one was; nothing when the one shown is the
     * background's: back from the background editor, it may have saved a new background, which
     * the editor sets right after, and the one it replaced isn't drawn meanwhile.
     */
    fun resumeWallpaper() = loadWallpaper()

    /**
     * The background's picture: none to do if it's the one shown; else the home screen's, if it's
     * of this background; else the one [images] kept, if they keep it at this size; else drawn in
     * the background.
     */
    private fun loadWallpaper() {
        val images = images ?: return
        val loading = loading ?: return
        val background: Background = appearance.background
        val width = screen.width().roundToInt()
        val height = screen.height().roundToInt()
        val wanted = wallpaperDrawing(background, width, height)
        if (wanted == null || width <= 0 || height <= 0) {
            loading.stop()
            wallpaper = null
            drawnFrom = null
            return invalidate()
        }
        if (wanted == drawnFrom) {
            loading.stop()
            return
        }
        val home = home?.readyFor(background)
        val kept = if (home != null) shrunk(home, width, height) else images.cached(wanted.picture, width, height, wanted.style)
        if (kept != null) {
            loading.stop()
            wallpaper = kept
            drawnFrom = wanted
            return invalidate()
        }
        loading.load({ stopped -> images.render(wanted.picture, width, height, wanted.style, stopped) }) { image ->
            wallpaper = image
            drawnFrom = if (image != null) wanted else null
            invalidate()
        }
    }

    /** The sample screen's height in dp, for its width: the TV's shape. */
    private val screenHeight = context.screenSize().heightAt(SCREEN_WIDTH)
    private val tilesHeight = screenHeight - TILES_TOP - PADDING_BOTTOM

    /** Pixels per dp of the sample screen. */
    private var scale = 0f
    private val screen = RectF()
    private val tiles = Array(TILES) { RectF() }
    private var tileHeight = 0
    private var tileRadius = 0f

    /** Whether the focused tile is lifted, from the style; else framed. */
    private var lifted = false

    /** The focused tile's frame's line, inside the tile's edge, and its corners' radius. */
    private val frame = RectF()
    private var frameRadius = 0f

    /**
     * A lifted tile's shadow, layer by layer, each wider and lower, less the tile: the tiles' fill
     * is see-through, and the shadow under it would darken the tile it lifts. Made anew with the
     * tiles' places and corners, in place.
     */
    private val shadows = Array(SHADOW_LAYERS) { Path().apply { fillType = Path.FillType.EVEN_ODD } }
    private val shadow = RectF()

    /** The app name's block, below the focused tile. */
    private val name = RectF()
    private val time = RectF()
    private val date = RectF()
    private val gear = RectF()

    private val screenPaint = Paint().apply { color = context.color(UiR.color.background) }
    private val wallpaperPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val tileFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.color(R.color.settings_preview_tile) }
    private val tilePaint = stroke(context.color(UiR.color.text_secondary))
    private val focusPaint = stroke(colorRoles(appearance.theme).homeAccent)
    /** The plain background's edge, in the theme's: on the screen's dark, nothing else shows where it ends. */
    private val edgePaint = stroke(colorRoles(appearance.theme).outlineVariant).apply {
        strokeWidth = resources.getDimension(UiR.dimen.outline)
    }
    private val edge = RectF()
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(SHADOW_ALPHA, 0, 0, 0) }
    private val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.color(UiR.color.text_primary) }
    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.color(UiR.color.text_secondary) }
    /** The date over a picture, opaque, as the home screen's. */
    private val secondaryOnPicturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.color(UiR.color.text_secondary_on_picture) }
    private val outline = WallpaperOutline(context)

    /**
     * The room below each row for the focused tile's name, and the gap above its block, in dp:
     * measured as the home screen measures them (:ui's [TileName]), at the largest zoom the sample
     * draws, the home screen's largest too. So the rows here are as far apart as there, with the
     * device's font size and the language's font.
     */
    private val nameRoom: Float
    private val nameGap: Float

    init {
        val tileName = TileName(context)
        nameRoom = tileName.roomDp(FOCUS_ZOOM)
        nameGap = tileName.gapDp
    }
    private val blockOutlinePaint = stroke(outline.color).apply { strokeJoin = Paint.Join.ROUND }
    private val tileOutlinePaint = stroke(outline.color)

    private fun stroke(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.color = color
    }

    /** As tall as its screen at its width, if there's the room. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, resolveSize(width * screenHeight / SCREEN_WIDTH, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        scale = minOf(w / SCREEN_WIDTH.toFloat(), h / screenHeight.toFloat())
        val left = (w - SCREEN_WIDTH * scale) / 2
        val top = (h - screenHeight * scale) / 2
        // On whole pixels, as the outline that cuts it is.
        screen.set(left.roundToInt().toFloat(), top.roundToInt().toFloat(), (left + SCREEN_WIDTH * scale).roundToInt().toFloat(), (top + screenHeight * scale).roundToInt().toFloat())
        edge.set(screen)
        edge.inset(edgePaint.strokeWidth / 2, edgePaint.strokeWidth / 2)
        invalidateOutline()
        tilePaint.strokeWidth = maxOf(1f, OUTLINE * scale)
        focusPaint.strokeWidth = maxOf(2f, FRAME * scale)
        val edge = maxOf(1f, outline.widthDp * scale)
        blockOutlinePaint.strokeWidth = 2 * edge
        tileOutlinePaint.strokeWidth = tilePaint.strokeWidth + 2 * edge
        placeTiles()
        styleTiles()
        placeBar()
        loadWallpaper()
    }

    override fun onDraw(canvas: Canvas) {
        val wallpaper = wallpaper
        if (wallpaper != null) {
            canvas.drawBitmap(wallpaper, null, screen, wallpaperPaint)
        } else {
            // Its corners round the same centers as the outline's, so the line follows the cut; the
            // dark ends under the line's middle, so none of it shows past the line at the corners.
            val radius = cornerRadius - edgePaint.strokeWidth / 2
            canvas.drawRoundRect(edge, radius, radius, screenPaint)
            canvas.drawRoundRect(edge, radius, radius, edgePaint)
        }
        drawBlock(canvas, time, primaryPaint)
        drawBlock(canvas, date, if (wallpaper != null) secondaryOnPicturePaint else secondaryPaint)
        if (!gear.isEmpty) {
            canvas.drawOval(gear, blockOutlinePaint)
            canvas.drawOval(gear, primaryPaint)
        }
        for (i in tiles.indices) {
            if (i != FOCUSED) drawTile(canvas, tiles[i])
        }
        // Zoomed, as the home screen's focused tile, its name with it.
        val focused = tiles[FOCUSED]
        canvas.save()
        canvas.scale(FOCUS_ZOOM, FOCUS_ZOOM, focused.centerX(), focused.centerY())
        if (lifted) {
            for (layer in shadows) canvas.drawPath(layer, shadowPaint)
            drawTile(canvas, focused)
        } else {
            canvas.drawRoundRect(focused, tileRadius, tileRadius, tileFillPaint)
            canvas.drawRoundRect(frame, frameRadius, frameRadius, focusPaint)
        }
        drawBlock(canvas, name, primaryPaint)
        canvas.restore()
    }

    /** A tile as every one but a framed focused tile is drawn: filled, with its outline, and the edge over a picture. */
    private fun drawTile(canvas: Canvas, tile: RectF) {
        canvas.drawRoundRect(tile, tileRadius, tileRadius, tileOutlinePaint)
        canvas.drawRoundRect(tile, tileRadius, tileRadius, tileFillPaint)
        canvas.drawRoundRect(tile, tileRadius, tileRadius, tilePaint)
    }

    /** [block] with its outline. An item left out has an empty block, which stroked would still draw a dot. */
    private fun drawBlock(canvas: Canvas, block: RectF, paint: Paint) {
        if (block.isEmpty) return
        canvas.drawRoundRect(block, BLOCK_RADIUS * scale, BLOCK_RADIUS * scale, blockOutlinePaint)
        canvas.drawRoundRect(block, BLOCK_RADIUS * scale, BLOCK_RADIUS * scale, paint)
    }

    private fun placeTiles() {
        if (scale == 0f) return
        val layouts = TileLayouts(appearance.tileGeometry, scale, nameHeight = (nameRoom * scale).roundToInt())
        val grid = layouts.tiles(TILES, (TILES_WIDTH * scale).roundToInt(), (tilesHeight * scale).roundToInt())
        tileHeight = grid.tileHeight
        val left = screen.left + PADDING_HORIZONTAL * scale
        val top = screen.top + TILES_TOP * scale
        for (i in tiles.indices) {
            val x = left + grid.left(i)
            val y = top + grid.top(i)
            tiles[i].set(x, y, x + grid.tileWidth, y + grid.tileHeight)
        }
        val focused = tiles[FOCUSED]
        if (layouts.nameRoom > 0) {
            val nameLeft = focused.centerX() - NAME_WIDTH * scale / 2
            val nameTop = focused.bottom + nameGap * scale
            name.set(nameLeft, nameTop, nameLeft + NAME_WIDTH * scale, nameTop + NAME_HEIGHT * scale)
        } else {
            name.setEmpty()
        }
    }

    private fun styleTiles() {
        val style = appearance.tileStyle
        tileRadius = style.corners.pixels(tileHeight, scale)
        lifted = !style.focusFrame
        val focused = tiles[FOCUSED]
        // As the home screen's: the line's middle half its width inside the edge, its corners as much tighter.
        val inset = focusPaint.strokeWidth / 2
        frame.set(focused)
        frame.inset(inset, inset)
        frameRadius = (tileRadius - inset).coerceAtLeast(0f)
        for (i in shadows.indices) {
            val spread = (i + 1) * SHADOW_SPREAD * scale
            shadow.set(focused)
            shadow.inset(-spread, -spread)
            shadow.offset(0f, (i + 1) * SHADOW_DROP * scale)
            shadows[i].reset()
            shadows[i].addRoundRect(shadow, tileRadius + spread, tileRadius + spread, Path.Direction.CW)
            shadows[i].addRoundRect(focused, tileRadius, tileRadius, Path.Direction.CW)
        }
    }

    /**
     * Each item of the top bar as a block of its size, at the edge the domain's layout puts it; an
     * item it leaves out is empty, which draws nothing.
     */
    private fun placeBar() {
        time.setEmpty()
        date.setEmpty()
        gear.setEmpty()
        val layout = topBarLayout(appearance.topBar)
        var x = PADDING_HORIZONTAL.toFloat()
        for (item in layout.left) {
            place(item, x)
            x += width(item) + ITEM_GAP
        }
        x = (SCREEN_WIDTH - PADDING_HORIZONTAL).toFloat()
        for (i in layout.right.indices.reversed()) {
            val item = layout.right[i]
            x -= width(item)
            place(item, x)
            x -= ITEM_GAP
        }
    }

    /** Places [item]'s block at [x] sample dp from the screen's left. */
    private fun place(item: TopBarItem, x: Float) {
        val baseline = (PADDING_TOP + BASELINE).toFloat()
        when (item) {
            TopBarItem.TIME -> block(time, x, baseline - TIME_HEIGHT, TIME_WIDTH, TIME_HEIGHT)
            TopBarItem.DATE -> block(date, x, baseline - DATE_HEIGHT, DATE_WIDTH, DATE_HEIGHT)
            TopBarItem.SETTINGS -> {
                val inset = (BAR_HEIGHT - GEAR) / 2f
                block(gear, x + inset, PADDING_TOP + inset, GEAR, GEAR)
            }
        }
    }

    private fun block(rect: RectF, x: Float, y: Float, width: Int, height: Int) {
        val left = screen.left + x * scale
        val top = screen.top + y * scale
        rect.set(left, top, left + width * scale, top + height * scale)
    }

    private fun width(item: TopBarItem): Int = when (item) {
        TopBarItem.TIME -> TIME_WIDTH
        TopBarItem.DATE -> DATE_WIDTH
        TopBarItem.SETTINGS -> BAR_HEIGHT
    }

    // The sample screen, in dp: about the home screen's paddings and sizes (feature/home's dimens).
    private companion object {
        /**
         * [image], the screen's wallpaper, shrunk to [width] x [height] px without its thin lines
         * turning to moiré: halved once, and again while it's still twice as large or more, each
         * half averaging 2 x 2 pixels (a filtered scale by exactly one half), then scaled the rest
         * of the way. As the preview drawn from a working copy, which covers half the screen, so
         * the two look alike. Scaled at once by nearly 2 or more, as drawing it does, each pixel
         * would be read from 4 of many, the rest skipped. It takes milliseconds, and the first
         * frame less time than drawing the image whole, which has over 4 times the pixels to
         * upload.
         */
        fun shrunk(image: Bitmap, width: Int, height: Int): Bitmap {
            var half = Bitmap.createScaledBitmap(image, image.width / 2, image.height / 2, true)
            while (half.width / 2 >= width && half.height / 2 >= height) {
                val next = Bitmap.createScaledBitmap(half, half.width / 2, half.height / 2, true)
                half.recycle()
                half = next
            }
            val shrunk = Bitmap.createScaledBitmap(half, width, height, true)
            if (shrunk !== half) half.recycle()
            return shrunk
        }

        const val SCREEN_WIDTH = 960
        const val PADDING_HORIZONTAL = 48
        const val PADDING_TOP = 32
        const val PADDING_BOTTOM = 32
        /** The settings entry's size, which the bar's height is. */
        const val BAR_HEIGHT = 40
        const val TILES_TOP = PADDING_TOP + BAR_HEIGHT + 24
        const val TILES_WIDTH = SCREEN_WIDTH - 2 * PADDING_HORIZONTAL
        const val TILES = 7
        const val FOCUSED = 0
        /** The focused tile's zoom, framed or lifted (feature/home's AppTileView), the largest. */
        const val FOCUS_ZOOM = 1.15f
        /**
         * A lifted tile's shadow: its layers, how much wider and lower each is than the last, in dp,
         * and each one's darkness. They overlap, so it's darkest at the tile, about 40 %, and fades
         * in small steps to its outer edge, 6 dp out and 9 dp down.
         */
        const val SHADOW_LAYERS = 12
        const val SHADOW_SPREAD = 0.5f
        const val SHADOW_DROP = 0.75f
        const val SHADOW_ALPHA = 11
        /** The name's block, as wide as a short name. */
        const val NAME_WIDTH = 64
        const val NAME_HEIGHT = 12
        /** The tiles' outline, and the focused one's frame. */
        const val OUTLINE = 2
        const val FRAME = 3
        /** Where the time's and the date's blocks end, below the bar's top: their shared baseline. */
        const val BASELINE = 32
        const val TIME_WIDTH = 80
        const val TIME_HEIGHT = 26
        const val DATE_WIDTH = 200
        const val DATE_HEIGHT = 14
        const val BLOCK_RADIUS = 3
        const val GEAR = 24
        /** Between the time and the date. */
        const val ITEM_GAP = 16
    }
}
