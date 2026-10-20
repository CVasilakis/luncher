package com.luncher.launcher.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.Jobs
import com.luncher.domain.jobs.LatestRequest
import com.luncher.domain.layout.TileLayouts
import com.luncher.domain.layout.TopBarItem
import com.luncher.domain.layout.topBarLayout
import com.luncher.domain.pictures.PictureImages
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
 * right differ, and the first tile focused, so the focus frame shows. The width stays, so a row
 * holds as many tiles on every shape.
 *
 * It's laid out by the same rules as the home screen, [TileLayouts] and [topBarLayout], at its own
 * density (its pixels per sample dp), so every dp value scales with it, and it can't disagree with
 * the home screen. Left to right in every language, like the home screen. What it draws from is
 * made anew only when the part of the appearance it comes from changes, compared with `==`, and
 * drawing allocates nothing. The wallpaper is drawn in the background ([showWallpapers]), from
 * the picture's working copy in milliseconds, the latest request winning, and kept while the
 * background and the size stay the same.
 *
 * What's on the wallpaper has the home screen's edge ([WallpaperOutline]), so the illustration
 * reads on a white picture as the home screen does: the blocks and the tiles' outlines, at the
 * sample's scale, a pixel at least; not the focus frame, which the home screen's has none of.
 */
internal class AppearancePreviewView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

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
            invalidate()
        }

    private var images: PictureImages<Bitmap>? = null
    private var loading: LatestRequest? = null

    /** The background's picture as last drawn, at the screen's size; null for none. */
    private var wallpaper: Bitmap? = null

    /** Draws the background's picture with [images], in [jobs]; until then, none is drawn. */
    fun showWallpapers(images: PictureImages<Bitmap>, jobs: Jobs) {
        this.images = images
        loading = LatestRequest(jobs, JobPriority.PICTURE_SCREENS) { Handler(Looper.getMainLooper()).post(it) }
        loadWallpaper()
    }

    /** Drops the picture being drawn, which [resumeWallpaper] draws again. */
    fun stopWallpaper() {
        loading?.stop()
    }

    fun resumeWallpaper() = loadWallpaper()

    /** The background's picture: the one [images] kept, if they keep it at this size, else drawn in the background. */
    private fun loadWallpaper() {
        val images = images ?: return
        val loading = loading ?: return
        val background: Background = appearance.background
        val picture = background.picture
        val width = screen.width().roundToInt()
        val height = screen.height().roundToInt()
        if (picture == null || width <= 0 || height <= 0) {
            loading.stop()
            wallpaper = null
            return invalidate()
        }
        val kept = images.cached(picture, width, height, background.style)
        if (kept != null) {
            loading.stop()
            wallpaper = kept
            return invalidate()
        }
        loading.load({ stopped -> images.render(picture, width, height, background.style, stopped) }) { image ->
            wallpaper = image
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
    private val time = RectF()
    private val date = RectF()
    private val gear = RectF()

    private val screenPaint = Paint().apply { color = context.color(UiR.color.background) }
    private val wallpaperPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val tileFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.color(R.color.settings_preview_tile) }
    private val tilePaint = stroke(context.color(UiR.color.text_secondary))
    private val focusPaint = stroke(context.color(UiR.color.accent))
    private val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.color(UiR.color.text_primary) }
    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.color(UiR.color.text_secondary) }
    private val outline = WallpaperOutline(context)
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
        screen.set(left, top, left + SCREEN_WIDTH * scale, top + screenHeight * scale)
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
        if (wallpaper != null) canvas.drawBitmap(wallpaper, null, screen, wallpaperPaint) else canvas.drawRect(screen, screenPaint)
        drawBlock(canvas, time, primaryPaint)
        drawBlock(canvas, date, secondaryPaint)
        if (!gear.isEmpty) {
            canvas.drawOval(gear, blockOutlinePaint)
            canvas.drawOval(gear, primaryPaint)
        }
        for (i in tiles.indices) {
            if (i == FOCUSED) continue
            canvas.drawRoundRect(tiles[i], tileRadius, tileRadius, tileOutlinePaint)
            canvas.drawRoundRect(tiles[i], tileRadius, tileRadius, tileFillPaint)
            canvas.drawRoundRect(tiles[i], tileRadius, tileRadius, tilePaint)
        }
        // Zoomed, as the home screen's focused tile.
        val focused = tiles[FOCUSED]
        canvas.save()
        canvas.scale(FOCUSED_ZOOM, FOCUSED_ZOOM, focused.centerX(), focused.centerY())
        canvas.drawRoundRect(focused, tileRadius, tileRadius, tileFillPaint)
        canvas.drawRoundRect(focused, tileRadius, tileRadius, focusPaint)
        canvas.restore()
    }

    /** [block] with its outline. An item left out has an empty block, which stroked would still draw a dot. */
    private fun drawBlock(canvas: Canvas, block: RectF, paint: Paint) {
        if (block.isEmpty) return
        canvas.drawRoundRect(block, BLOCK_RADIUS * scale, BLOCK_RADIUS * scale, blockOutlinePaint)
        canvas.drawRoundRect(block, BLOCK_RADIUS * scale, BLOCK_RADIUS * scale, paint)
    }

    private fun placeTiles() {
        if (scale == 0f) return
        val grid = TileLayouts(appearance.tileGeometry, scale)
            .tiles(TILES, (TILES_WIDTH * scale).roundToInt(), (tilesHeight * scale).roundToInt())
        tileHeight = grid.tileHeight
        val left = screen.left + PADDING_HORIZONTAL * scale
        val top = screen.top + TILES_TOP * scale
        for (i in tiles.indices) {
            val x = left + grid.left(i)
            val y = top + grid.top(i)
            tiles[i].set(x, y, x + grid.tileWidth, y + grid.tileHeight)
        }
    }

    private fun styleTiles() {
        tileRadius = appearance.tileStyle.corners.pixels(tileHeight, scale)
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
        const val FOCUSED_ZOOM = 1.1f
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
