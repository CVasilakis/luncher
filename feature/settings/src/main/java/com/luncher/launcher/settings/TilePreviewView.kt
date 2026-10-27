package com.luncher.launcher.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.colorRoles
import com.luncher.domain.apps.AppImages
import com.luncher.domain.apps.Banner
import com.luncher.domain.apps.HomeApp
import com.luncher.domain.apps.LaunchableApp
import com.luncher.domain.apps.PreparedTile
import com.luncher.domain.apps.PreparedTiles
import com.luncher.domain.apps.TileDrawing
import com.luncher.domain.apps.TileImage
import com.luncher.domain.apps.TileLook
import com.luncher.domain.apps.ownBanner
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.Jobs
import com.luncher.domain.jobs.LatestRequest
import com.luncher.domain.layout.TilePlace
import com.luncher.domain.layout.TileSize
import com.luncher.domain.wallpaper.Wallpaper
import com.luncher.launcher.ui.FOCUSED_TILE_ZOOM
import com.luncher.launcher.ui.ScreenSize
import com.luncher.launcher.ui.TileFrost
import com.luncher.launcher.ui.TilePainter
import com.luncher.launcher.ui.TileParts
import com.luncher.launcher.ui.color
import com.luncher.launcher.ui.homeTileSize
import com.luncher.launcher.ui.screenSize
import com.luncher.launcher.ui.R as UiR
import kotlin.math.roundToInt

/**
 * The tile image picker's preview: the app's tile as the draft would show it on the home screen,
 * twice, side by side, each in a panel of its own: at rest, and focused, zoomed as the home screen
 * zooms it, framed or lifted as the appearance says. Each panel shows the part of the wallpaper
 * around the tile's place on the home screen ([TilePlace], which the home screen gives), so it's
 * what the home screen shows there: the tile at the home tile's size, the wallpaper at the
 * screen's, where the panels have the room; else both smaller alike ([shape]). Without a
 * wallpaper, the home screen's plain color, inside an edge. Left to right in every language, as
 * the home screen: the resting tile at the left.
 *
 * Both tiles are drawn as the home screen draws its tiles, by :ui's [TilePainter], with the
 * appearance's corners, accent and focus style: the draft's tile is made as the home screen's is
 * prepared, at the tile's size ([PreparedTiles.make]), in the background, the latest request
 * winning, the tile shown before staying meanwhile; kept nowhere, as the file is Save's. The app's
 * own image, for Original banner, or where a picture can't be made (its copy not prepared yet, or
 * gone), is drawn at once, on the main thread, as the home screen draws its own. A new picture,
 * framing or Focus makes the tile again (the picture's shape is made only for Focus Shape); a new
 * backdrop is drawn live, from the tile made. A Frosted tile's backdrop is the wallpaper's frosted
 * copy behind the tile's place; the lifted tile casts Android's shadow from the outline the
 * painter gives, as the home screen's does. Drawing allocates nothing (docs/ARCHITECTURE.md, rule 5).
 */
internal class TilePreviewView(context: Context, attrs: AttributeSet?) : ViewGroup(context, attrs) {

    /** What [tile] was made from: an app's own image, or a picture's tile. */
    private sealed interface Source {
        data class Own(val app: LaunchableApp, val banner: Banner.Own, val size: TileSize) : Source

        data class Picture(val drawing: TileDrawing) : Source
    }

    private val painter = TilePainter(context)
    private val pixelsPerDp = resources.displayMetrics.density
    private val screen: ScreenSize = context.screenSize()

    /** Between the panels, as between the fields below them, so they line up with the fields' two columns. */
    private val gap = resources.getDimensionPixelSize(R.dimen.settings_fields_gap)

    /** Around the focused tile, zoomed, in its panel: room for its frame, its band or its shadow. */
    private val room = resources.getDimensionPixelSize(R.dimen.settings_tile_preview_room)
    private val lift = resources.getDimension(UiR.dimen.tile_lift)
    private val cornerRadius = resources.getDimension(R.dimen.settings_preview_corner)

    private val resting = Panel(focused = false)
    private val focused = Panel(focused = true)

    private var banners: AppImages<Bitmap>? = null
    private var tiles: PreparedTiles<Bitmap>? = null
    private var wallpaper: Wallpaper<Bitmap>? = null
    private var loading: LatestRequest? = null

    private var app: HomeApp? = null
    private var choice = TileImage(null)

    /** The home screen's appearance, once [style] gave it: the tiles' size and look, and the wallpaper's background. */
    private var appearance: Appearance? = null
    private var tileSize = TileSize(0, 0)
    private var radius = 0f
    private var framed = true

    /** Where the home screen showed the tile, if it said; and where it's shown, on this screen. */
    private var found: TilePlace? = null
    private var place = TilePlace(0, 0, screen.width, screen.height)

    /** The tile made for the draft, the parts it's drawn with, and what each was made from. */
    private var tile: PreparedTile<Bitmap>? = null
    private var madeFrom: Source? = null
    private var parts: TileParts? = null
    private var partsLook: TileLook? = null

    /** What's being made in the background, so asking for it again doesn't start again. */
    private var making: TileDrawing? = null

    /** The home screen's wallpaper while it's of the appearance's background, at the screen's size; null for the plain color. */
    private var image: Bitmap? = null

    /** The wallpaper's part each panel shows, in the screen's pixels; and how much smaller than there the panels show it. */
    private val crop = Rect()
    private var scale = 1f

    private val wallpaperPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val plainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.color(UiR.color.background) }

    /** The plain color's edge, in the theme's: on the screen's dark, nothing else shows where it ends. */
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(UiR.dimen.outline)
    }

    private val follow: () -> Unit = { showWallpaper() }

    init {
        // The home screen's tiles don't mirror.
        layoutDirection = LAYOUT_DIRECTION_LTR
        clipChildren = false
        addView(resting)
        addView(focused)
    }

    /**
     * Draws an app's own image with [banners], and a picture's tile with [tiles], in [jobs], over
     * [wallpaper]; the tile at [found], where the home screen showed it, if it said.
     */
    fun drawWith(banners: AppImages<Bitmap>, tiles: PreparedTiles<Bitmap>, wallpaper: Wallpaper<Bitmap>, jobs: Jobs, found: TilePlace?) {
        this.banners = banners
        this.tiles = tiles
        this.wallpaper = wallpaper
        this.found = found
        loading = LatestRequest(jobs, JobPriority.PICTURE_SCREENS) { Handler(Looper.getMainLooper()).post(it) }
        load()
    }

    /** Shows [app]'s tile with [choice]: its picture so framed, with its look, or the app's own image for none. */
    fun show(app: HomeApp, choice: TileImage) {
        this.app = app
        this.choice = choice
        load()
    }

    /** The home screen's [appearance]: its tiles' size, corners, accent and focus style, and its wallpaper's background. */
    fun style(appearance: Appearance) {
        val old = this.appearance
        if (appearance == old) return
        this.appearance = appearance
        val roles = colorRoles(appearance.theme)
        painter.accent = roles.homeAccent
        edgePaint.color = roles.outlineVariant
        framed = appearance.tileStyle.focusFrame
        tileSize = context.homeTileSize(appearance.tileGeometry)
        radius = appearance.tileStyle.corners.pixels(tileSize.height, pixelsPerDp)
        place = TilePlace.on(found, tileSize, screen.width, screen.height)
        if (old == null || appearance.background != old.background) showWallpaper()
        requestLayout()
        restyleTiles()
        load()
    }

    /** Follows the wallpaper, and draws the tile that wasn't made, if one wasn't: as the screen starts. */
    fun start() {
        wallpaper?.addListener(follow)
        showWallpaper()
        load()
    }

    /** Stops following the wallpaper, and drops the tile being made, which [start] makes again. */
    fun stop() {
        wallpaper?.removeListener(follow)
        loading?.stop()
        making = null
    }

    /**
     * The preview's shape: two panels side by side, each the focused tile, zoomed, with [room]
     * around it, at the home tile's size, and [gap] between them.
     */
    fun shape(): ScreenSize {
        val size = context.homeTileSize(appearance?.tileGeometry ?: return ScreenSize(2, 1))
        val panelWidth = (size.width * FOCUSED_TILE_ZOOM).roundToInt() + 2 * room
        val panelHeight = (size.height * FOCUSED_TILE_ZOOM).roundToInt() + 2 * room
        return ScreenSize(2 * panelWidth + gap, panelHeight)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val panel = MeasureSpec.makeMeasureSpec(panelWidth(width), MeasureSpec.EXACTLY)
        val panelHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        resting.measure(panel, panelHeight)
        focused.measure(panel, panelHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val panelWidth = panelWidth(width)
        placeCrop(panelWidth, height)
        resting.layout(0, 0, panelWidth, height)
        focused.layout(width - panelWidth, 0, width, height)
        // A new place in a panel of the same size doesn't lay the panels out again.
        resting.placeTile()
        focused.placeTile()
        resting.invalidate()
        focused.invalidate()
    }

    private fun panelWidth(width: Int) = ((width - gap) / 2).coerceAtLeast(0)

    /**
     * The part of the screen each panel shows, around the tile's place, at the home screen's scale
     * where the zoomed tile and its room fit, else smaller; kept on the screen, so near its edge
     * the tile is off the panel's middle, where it is against the wallpaper.
     */
    private fun placeCrop(panelWidth: Int, panelHeight: Int) {
        val zoomedWidth = tileSize.width * FOCUSED_TILE_ZOOM
        val zoomedHeight = tileSize.height * FOCUSED_TILE_ZOOM
        scale = if (zoomedWidth <= 0f || panelWidth <= 0 || panelHeight <= 0) {
            1f
        } else {
            minOf(1f, (panelWidth - 2 * room) / zoomedWidth, (panelHeight - 2 * room) / zoomedHeight).coerceAtLeast(MIN_SCALE)
        }
        val cropWidth = (panelWidth / scale).roundToInt()
        val cropHeight = (panelHeight / scale).roundToInt()
        val left = (place.x + (tileSize.width - cropWidth) / 2f).roundToInt().coerceIn(0, (screen.width - cropWidth).coerceAtLeast(0))
        val top = (place.y + (tileSize.height - cropHeight) / 2f).roundToInt().coerceIn(0, (screen.height - cropHeight).coerceAtLeast(0))
        crop.set(left, top, left + cropWidth, top + cropHeight)
    }

    /** The wallpaper the home screen shows, and its frost, the Frosted tiles' backdrop: none while it isn't of this background. */
    private fun showWallpaper() {
        val wallpaper = wallpaper ?: return
        val background = appearance?.background ?: return
        val image = wallpaper.readyFor(background)?.takeIf { it.width == screen.width && it.height == screen.height }
        val frost = (wallpaper.state as? Wallpaper.State.Ready)?.frost?.takeIf { image != null }
        if (image === this.image && frost === painter.frost?.image) return
        this.image = image
        // Its space is the screen's, the home screen's window, as there.
        painter.frost = if (image != null && frost != null) TileFrost(frost, image.width, image.height) else null
        resting.invalidate()
        focused.invalidate()
        restyleTiles()
    }

    /** The draft's tile: none to make if it's the one shown, or being made; the app's own at once; a picture's in the background. */
    private fun load() {
        val app = app ?: return
        val banners = banners ?: return
        val tiles = tiles ?: return
        val loading = loading ?: return
        if (appearance == null) return
        val picture = choice.picture
        if (picture == null) {
            loading.stop()
            making = null
            return showOwn(app, banners)
        }
        val drawing = TileDrawing(picture, choice.framing, choice.look.focus, tileSize)
        if (serves(drawing)) {
            loading.stop()
            making = null
            return showParts()
        }
        if (drawing == making) return
        making = drawing
        loading.load({ stopped -> tiles.make(drawing, stopped) }) { made ->
            making = null
            // Its copy not prepared yet, or the picture gone: the app's own, as the tile shows; made
            // again at the next change, the copy prepared meanwhile.
            if (made == null) showOwn(app, banners) else shown(made, Source.Picture(drawing))
        }
    }

    /**
     * Whether the tile shown serves [drawing]: made for it, or for it with another Focus where the
     * Focus needs nothing more (an opaque picture's, or a see-through one's with its shape made).
     */
    private fun serves(drawing: TileDrawing): Boolean {
        val made = (madeFrom as? Source.Picture)?.drawing ?: return false
        if (made == drawing) return true
        val tile = tile ?: return false
        return made.copy(focus = drawing.focus) == drawing && (tile.shape != null || !drawing.withShape(tile.seeThrough))
    }

    private fun showOwn(app: HomeApp, banners: AppImages<Bitmap>) {
        val source = Source.Own(app.launchable, ownBanner(app), tileSize)
        if (source == madeFrom) return showParts()
        shown(PreparedTile(banners.render(app, source.banner, tileSize.width, tileSize.height), seeThrough = false, shape = null), source)
    }

    private fun shown(tile: PreparedTile<Bitmap>, from: Source) {
        this.tile = tile
        madeFrom = from
        parts = null
        showParts()
    }

    /** The parts the tile shown is drawn with, with the draft's look: made again for a new tile or look. */
    private fun showParts() {
        val tile = tile ?: return
        val look = choice.look
        if (parts != null && look == partsLook) return
        parts = painter.parts(tile.image, tile.seeThrough, tile.shape, look)
        partsLook = look
        restyleTiles()
    }

    private fun restyleTiles() {
        resting.restyle()
        focused.restyle()
    }

    /** A panel: the wallpaper's part around the tile, or the plain color, and the tile; its corners rounded. */
    private inner class Panel(focused: Boolean) : ViewGroup(context) {

        private val tile = Tile(focused)
        private val bounds = RectF()
        private val edge = RectF()

        init {
            setWillNotDraw(false)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                }
            }
            // Cuts the tile's zoom, band and shadow at its edge, as the screen's edge would.
            clipToOutline = true
            addView(tile)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
            tile.measure(
                MeasureSpec.makeMeasureSpec(tileSize.width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(tileSize.height, MeasureSpec.EXACTLY),
            )
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) = placeTile()

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            bounds.set(0f, 0f, w.toFloat(), h.toFloat())
            edge.set(bounds)
            edge.inset(edgePaint.strokeWidth / 2, edgePaint.strokeWidth / 2)
        }

        /**
         * The tile at its place against the wallpaper's part shown, laid out at its size and zoomed
         * around its middle, as the home screen zooms it: [scale], and the focus's zoom too.
         */
        fun placeTile() {
            val width = tileSize.width
            val height = tileSize.height
            val middleX = (place.x - crop.left + width / 2f) * scale
            val middleY = (place.y - crop.top + height / 2f) * scale
            val left = (middleX - width / 2f).roundToInt()
            val top = (middleY - height / 2f).roundToInt()
            tile.layout(left, top, left + width, top + height)
            tile.scaleX = scale * tile.zoom
            tile.scaleY = scale * tile.zoom
            // Its lift too, at the panels' scale.
            tile.restyle()
        }

        fun restyle() = tile.restyle()

        override fun onDraw(canvas: Canvas) {
            val image = image
            if (image != null) {
                canvas.drawBitmap(image, crop, bounds, wallpaperPaint)
            } else {
                // Its corners round the same centers as the outline's, so the line follows the cut.
                val radius = cornerRadius - edgePaint.strokeWidth / 2
                canvas.drawRoundRect(edge, radius, radius, plainPaint)
                canvas.drawRoundRect(edge, radius, radius, edgePaint)
            }
        }
    }

    /** The tile in a panel: at rest, or [focused], all the way, as the home screen's focused tile shows. */
    private inner class Tile(private val focused: Boolean) : View(context) {

        /** Its zoom on the home screen. */
        val zoom = if (focused) FOCUSED_TILE_ZOOM else 1f

        init {
            // The lifted tile's shadow, from the outline the home screen's tiles cast it from.
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) = painter.outline(outline, view.width, view.height, radius, parts)
            }
        }

        /** Lifted while focused and not framed, where its parts cast the outline's shadow, as the home screen's tile. */
        fun restyle() {
            val lifted = focused && !framed && parts?.castsOutlineShadow == true
            translationZ = if (lifted) lift * scale else 0f
            invalidateOutline()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val parts = parts ?: return
            // The tile's place on the screen, before its zoom: where a Frosted tile's frost is.
            painter.draw(canvas, parts, radius, if (focused) 1f else 0f, focused, framed, held = false, place.x.toFloat(), place.y.toFloat(), zoom)
        }
    }

    private companion object {
        /** The least the panels shrink the home screen, on the smallest screens. */
        const val MIN_SCALE = 0.1f
    }
}
