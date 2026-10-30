package com.luncher.launcher.home

import android.animation.AnimatorInflater
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import com.luncher.domain.appearance.AppName
import com.luncher.domain.apps.AppImages
import com.luncher.domain.apps.Banner
import com.luncher.domain.apps.HomeApp
import com.luncher.domain.apps.PreparedTileImages
import com.luncher.domain.apps.bannerFor
import com.luncher.domain.layout.PixelBounds
import com.luncher.domain.layout.TileSize
import com.luncher.launcher.ui.FOCUSED_TILE_ZOOM
import com.luncher.launcher.ui.OkHold
import com.luncher.launcher.ui.TileParts
import com.luncher.launcher.ui.TilePainter

/**
 * One app on the home screen: its banner, and while it has focus, a zoom with, as
 * [TilePaints.framed] says, a frame inside its edge or a shadow, as if lifted: as big either way,
 * since the frame takes no room outside the tile. The image is drawn at the tile's size whenever
 * that size changes (the number of columns, for example), so drawing the tile is a single bitmap
 * copy. :ui's [TilePainter] draws it, from the tile's own [TileParts], made with each image: with
 * rounded corners ([TilePaints]), the same bitmap fills a rounded rectangle through a shader made
 * with it, and the frame and the shadow follow the corners: a new radius only redraws, with the
 * image as it is.
 *
 * The shadow is Android's own, cast by the tile's outline as it rises (its translationZ, in the
 * state animator), drawn by the GPU: nothing is blurred or allocated for it. A see-through picture
 * has its look ([TileLook][com.luncher.domain.apps.TileLook]): its backdrop, and its focus, which
 * follows the tile's shape, as every tile's, or the picture's own. Where it follows the picture's,
 * the tile isn't lifted: its zoom alone, and the picture's own shadow, the painter's
 * ([TileParts.castsOutlineShadow]). The backdrop, and the band or shadow around the picture's
 * shape, follow the focus in the zoom's time ([focusFade]). As
 * [TilePaints.names] says, the app's name shows centered below the tile, cut to the tile's width:
 * below the focused one only, fading in and out with the focus, or below every one at all times;
 * drawn with the tile, outside its bounds, which [AppTilesView] doesn't clip, so it zooms with it.
 *
 * While OK is held on the focused tile, it dips a little (its state animator's pressed item), so the
 * press shows at once, before the app or the menu opens. The menu opens after a hold of Luncher's
 * own ([OkHold], 350 ms), shorter than Android's long press.
 *
 * While the user moves its app, a [held] tile has a white frame, and the focused one's zoom: the
 * frame tells it apart from one only focused.
 *
 * A tile's picture of the library is prepared ahead, and only read here ([PreparedTileImages]): one
 * not prepared for this tile shows its app's own image until it is, then is drawn again with it
 * ([followPreparing]).
 *
 * A Frosted tile ([TileParts.frosted]) shows the wallpaper behind it, blurred, from where it is in
 * the window and its zoom, as it's drawn. Android doesn't draw a view again when it only moves or
 * zooms (scrolling, the focus's zoom, the held tile's, an app moved), so the tile draws itself
 * again then ([placeChanged], [setScaleX], its zoom's animation, [onLayout]); the others don't.
 */
@SuppressLint("ViewConstructor") // Created in code only, never from XML.
internal class AppTileView(
    context: Context,
    val app: HomeApp,
    private val images: AppImages<Bitmap>,
    /** The tiles' pictures, read as prepared. */
    private val tiles: PreparedTileImages<Bitmap>,
    /** Shared by every tile: the painter, the name's paint, the corners and whether the focused tile is framed. */
    private val paints: TilePaints,
) : View(context) {

    /**
     * What the tile is drawn with: its image, its picture's look; none while it has no size. Its
     * outline ([TilePaints.tileOutline]) is as strong as they say.
     */
    var parts: TileParts? = null
        private set

    private val banner = bannerFor(app)

    /** The picture of the library the user chose for the tile, if any. */
    private val picture: Banner.Picture? = banner as? Banner.Picture

    /** The app's own image: shown without a picture, and while the picture isn't prepared. */
    private val own: Banner.Own = picture?.own ?: banner as Banner.Own

    /** Whether [picture] is being prepared, the tile showing its app's own image meanwhile. */
    private var waitsForPicture = false

    /** The app's name as it fits the tile's width, and where it starts: set with each new size. */
    private var name = ""
    private var nameLeft = 0f

    /**
     * The focus as it comes and goes, in the zoom's time: forward as it comes, back as it goes. The
     * name fades with it, and a see-through picture's look follows it; run only for those. Made
     * once, so a key press only starts it.
     */
    private val focusFade = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
        addUpdateListener { invalidate() }
    }

    /** Whether the state animator lifts the tile; null before the first. */
    private var lifts: Boolean? = null

    /** Whether the tile's backdrop is the wallpaper behind it: drawn again whenever it moves or zooms. */
    private val frosted: Boolean get() = parts?.frosted == true

    /** The user holds this tile, to move it. */
    var held = false
        set(value) {
            if (field == value) return
            field = value
            val zoom = when {
                value -> HELD_ZOOM
                isFocused -> FOCUSED_ZOOM   // as the focus animator leaves it
                else -> 1f
            }
            animate().scaleX(zoom).scaleY(zoom)
            invalidate()
        }

    /** OK's hold, which opens the tile's menu sooner than Android's long press ([OkHold]); made once, so a press allocates nothing. */
    private val okHold = OkHold(this)

    init {
        isFocusable = true
        // The OK key opens the app, whether or not Android thinks it's in touch mode.
        isFocusableInTouchMode = true
        isClickable = true
        contentDescription = app.name
        outlineProvider = paints.tileOutline
        // The held tile's zoom (animate(), in held) changes the zoom without drawing the tile again.
        animate().setUpdateListener { placeChanged() }
        restyle()
    }

    /** It moved on the screen, or its zoom changed: a Frosted tile draws again. */
    fun placeChanged() {
        if (frosted) invalidate()
    }

    /**
     * The screen's frost came or went: a Frosted tile draws again, with its new backdrop, and its
     * outline's shadow is asked again, whole over a frost, lighter without ([TilePainter.outlineAlpha]).
     */
    fun frostChanged() {
        if (!frosted) return
        invalidateOutline()
        invalidate()
    }

    // The state animator zooms the tile through these, which don't draw it again.
    override fun setScaleX(scaleX: Float) {
        super.setScaleX(scaleX)
        placeChanged()
    }

    override fun setScaleY(scaleY: Float) {
        super.setScaleY(scaleY)
        placeChanged()
    }

    // OK down on the tile, Android pressing it (its long press timed from now): the hold starts,
    // where it's shorter than Android's.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val handled = super.onKeyDown(keyCode, event)
        okHold.down(keyCode, event)
        return handled
    }

    // OK released before the hold ran out: Android's, which clicks the tile (opens the app). After
    // the hold opened the menu, the release is consumed (OkHold.up).
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = okHold.up(keyCode) || super.onKeyUp(keyCode, event)

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) okHold.cancel()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        okHold.cancel()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // Placed elsewhere (an app moved, another layout) without a new size, it isn't drawn again.
        if (changed) placeChanged()
    }

    /**
     * Takes the style [paints] have now: the frame turned on or off swaps the state animator,
     * which zooms the tile, and lifts it without the frame, and puts it at once where the new one
     * has it; new corners give the shadow a new outline. Then redraws.
     */
    fun restyle() {
        chooseAnimator()
        invalidateOutline()
        invalidate()
    }

    /**
     * The state animator: lifting the tile while it has focus, without the frame, unless its
     * picture casts its own shadow; else zooming it only. A new one puts the tile at once where
     * it has it.
     */
    private fun chooseAnimator() {
        val lift = !paints.framed && parts?.castsOutlineShadow != false
        if (lift == lifts) return
        lifts = lift
        translationZ = 0f   // the zoom alone doesn't lift, so it wouldn't bring the tile down
        stateListAnimator = AnimatorInflater.loadStateListAnimator(
            context,
            if (lift) R.animator.home_tile_lift else R.animator.home_tile_focus,
        )
        jumpDrawablesToCurrentState()
    }

    /**
     * Follows its picture's preparing, as [tiles] says it is now: once it's done, the tile is
     * drawn again, with its picture, nothing else laid out. A tile without a picture does nothing.
     */
    fun followPreparing() {
        if (picture == null) return
        if (tiles.preparing(app.launchable)) {
            waitsForPicture = true
        } else if (waitsForPicture) {
            waitsForPicture = false
            render(width, height)
            chooseAnimator()
            invalidateOutline()
            invalidate()
        }
    }

    /**
     * The parts at [w] x [h] px: the picture as prepared for this size, read, with its look, else
     * the app's own image; none while the tile has no size.
     */
    private fun render(w: Int, h: Int) {
        val prepared = if (w > 0 && h > 0) picture?.let { tiles.read(app.launchable, it.drawing(TileSize(w, h))) } else null
        waitsForPicture = picture != null && prepared == null && tiles.preparing(app.launchable)
        parts = when {
            w <= 0 || h <= 0 -> null
            prepared != null -> paints.painter.parts(prepared.image, prepared.seeThrough, prepared.shape, picture!!.look)
            else -> paints.painter.parts(images.render(app, own, w, h))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        render(w, h)
        chooseAnimator()
        name = TextUtils.ellipsize(app.name, paints.namePaint, w.toFloat(), TextUtils.TruncateAt.END).toString()
        nameLeft = (w - paints.namePaint.measureText(name)) / 2
        invalidateOutline()
    }

    override fun onDraw(canvas: Canvas) {
        val parts = parts
        if (parts != null && parts.frosted) {
            // Where it is in the window, before its zoom: the frost covers the window.
            val origin = (parent as AppTilesView).origin
            val x = (origin[0] + left).toFloat()
            val y = (origin[1] + top).toFloat()
            paints.painter.draw(canvas, parts, paints.cornerRadius(height), focus(), isFocused, paints.framed, held, x, y, scaleX)
        } else if (parts != null) {
            paints.painter.draw(canvas, parts, paints.cornerRadius(height), focus(), isFocused, paints.framed, held)
        }
        if (paints.showsNames) drawName(canvas)
    }

    /** How far the focus has come, 0 to 1, as [focusFade] has it while it runs. */
    private fun focus(): Float = when {
        focusFade.isRunning -> focusFade.animatedFraction
        isFocused -> 1f
        else -> 0f
    }

    /** The app's name below the tile, whole, or as faded in as the focus has it, with its outline. */
    private fun drawName(canvas: Canvas) {
        val shown = if (paints.names == AppName.ALWAYS) 1f else focus()
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
        if (!gainFocus) okHold.cancel()
        if (paints.names == AppName.WHEN_FOCUSED || parts?.followsFocus == true) {
            // Back the way it came if it's still fading, else in from nothing or out from whole.
            if (focusFade.isRunning || !gainFocus) focusFade.reverse() else focusFade.start()
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
        val grownX = (width * (FOCUSED_ZOOM - 1) / 2).toInt()
        val grownY = (height * (FOCUSED_ZOOM - 1) / 2).toInt()
        val bottom = if (paints.showsNames) top + height + paints.nameHeight else top + height + grownY
        return PixelBounds(left - grownX, top - grownY, left + width + grownX, bottom)
    }

    companion object {
        /** The focused tile's zoom, framed (home_tile_focus.xml) or lifted (home_tile_lift.xml): :ui's. */
        const val FOCUSED_ZOOM = FOCUSED_TILE_ZOOM

        /** A held tile's zoom, while the user moves its app: the focused one's. */
        const val HELD_ZOOM = FOCUSED_ZOOM

        /** The most a tile grows, focused or held. */
        const val LARGEST_ZOOM = FOCUSED_ZOOM
    }
}
