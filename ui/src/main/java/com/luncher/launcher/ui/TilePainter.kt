package com.luncher.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import com.luncher.domain.apps.ShapeMasks
import com.luncher.domain.apps.TileBackdrop
import com.luncher.domain.apps.TileFocusShape
import com.luncher.domain.apps.TileLook
import com.luncher.domain.apps.TileMask
import com.luncher.domain.appearance.ThemeAppearance
import com.luncher.domain.appearance.colorRoles

/**
 * Draws a tile: its image, and, for a see-through picture of the library, its look ([TileLook]):
 * the backdrop behind the picture, and a focus that follows the tile's shape or the picture's. One
 * per screen, shared by its tiles, which each keep their own [TileParts], made with each new image.
 * The home screen draws every tile with it, its apps' own images too, so a frame is drawn one way
 * only; the tile banner picker's preview draws with it as the home screen does.
 *
 * The focus style is the appearance's ([draw]'s `framed`), for every tile alike; a tile's look only
 * says what it follows:
 * - **Framed:** a frame in the accent ([accent]) inside the tile's edge, or, for a picture whose
 *   focus follows its shape, a band in the accent around the shape.
 * - **Lifted:** the view casts Android's shadow from its outline, as strong as
 *   [TileParts.outlineAlpha] says; where the focus follows the picture's shape, the picture casts
 *   its own soft shadow instead ([TileParts.castsOutlineShadow] false), drawn here under it.
 *
 * The backdrop, the band and the shadow follow the focus as its animation goes (`focus`, 0 to 1);
 * the frame shows while the tile has focus, at once, as it always did. A held tile (its app being
 * moved) has a white frame inside its edge, in place of the accent's frame or band.
 *
 * A see-through tile at rest is drawn in one pass, its glass composed under the picture in one
 * shader ([TileParts]); drawn as a pass of its own, the glass would cost a second fill of the
 * whole tile at every frame, for every see-through tile on the screen. Once the focus comes, the
 * glass is drawn on its own again: its strength follows the focus, and the band and the shape's
 * shadow go between it and the picture.
 *
 * Drawing allocates nothing (docs/ARCHITECTURE.md, rule 5): the paints and the glasses' shaders
 * are made once, a tile's shaders and its masks' matrices with its parts.
 */
class TilePainter(context: Context) {

    /** A focused tile's frame, in the theme's accent ([accent]). */
    val focusFrame = frame(colorRoles(ThemeAppearance()).homeAccent, context)

    /** A held tile's frame, while the user moves its app. */
    val heldFrame = frame(context.color(R.color.text_primary), context)

    /** The focus's color: the focus frame's, and the band's around a picture's shape. */
    var accent: Int
        get() = focusFrame.color
        set(value) {
            focusFrame.color = value
        }

    /** How far below the picture its shape's shadow falls, once focused. */
    private val shadowDrop = context.resources.getDimension(R.dimen.tile_shape_shadow_drop)

    /** Fills the tile's rounded shape with its image, through the tile's own shader. */
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** The glass behind a see-through picture, in its color and strength at each draw. */
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** The masks of a picture's shape, drawn twice their size, in the band's or the shadow's color. */
    private val maskPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** The glasses at their resting strength, shared by the tiles that compose them under their picture. */
    private val lightGlass = glass(Color.WHITE, LIGHT_GLASS)
    private val darkGlass = glass(Color.BLACK, DARK_GLASS)

    private val bounds = RectF()
    private val frameBounds = RectF()

    /**
     * What [draw] draws a tile with: [image], at the tile's size; whether it's [seeThrough], so its
     * [look] applies; and, where the look's focus follows the picture's shape, the shape's masks.
     * Allocates (shaders, the masks' matrices): made with each new image, never as it's drawn.
     */
    fun parts(
        image: Bitmap,
        seeThrough: Boolean = false,
        shape: ShapeMasks<Bitmap>? = null,
        look: TileLook = TileLook(),
    ): TileParts {
        // Frosted is drawn as light glass for now.
        val glass = when {
            !seeThrough -> null
            look.backdrop == TileBackdrop.DARK_GLASS -> darkGlass
            look.backdrop == TileBackdrop.NONE -> null
            else -> lightGlass
        }
        return TileParts(image, seeThrough, if (seeThrough && look.focus == TileFocusShape.SHAPE) shape else null, look.backdrop, glass)
    }

    /**
     * Draws the tile of [parts] at (0, 0), its corners [radius] px round (0, square), as focused as
     * [focus] says (0 at rest to 1, as its animation goes), with the frame while [focused], the
     * appearance's frame if [framed], else as lifted; [held]: its app being moved. Its zoom and
     * its lift are the view's.
     */
    fun draw(canvas: Canvas, parts: TileParts, radius: Float, focus: Float, focused: Boolean, framed: Boolean, held: Boolean) {
        val image = parts.image
        bounds.set(0f, 0f, image.width.toFloat(), image.height.toFloat())
        val band = parts.band
        val shadow = parts.shadow
        // At rest nothing goes between the glass and the picture: one pass draws both.
        val resting = parts.restingWithGlass.takeIf { focus <= 0f }
        if (parts.backdrop != null && resting == null) {
            drawBackdrop(canvas, parts.backdrop, radius, focus, lifted = !framed && parts.castsOutlineShadow)
        }
        if (shadow != null && !framed && focus > 0f) {
            // Darker and lower as the focus comes in.
            maskPaint.color = Color.BLACK
            maskPaint.alpha = (focus * SHAPE_SHADOW * OPAQUE).toInt()
            canvas.save()
            canvas.translate(0f, shadowDrop * (1 + focus) / 2)
            canvas.drawBitmap(shadow.image, shadow.matrix, maskPaint)
            canvas.restore()
        }
        if (band != null && framed && !held && focus > 0f) {
            maskPaint.color = accent
            maskPaint.alpha = (focus * OPAQUE).toInt()
            // The band is a blur's soft edge: laid on a few times, it's as clear as a frame.
            repeat(BAND_COATS) { canvas.drawBitmap(band.image, band.matrix, maskPaint) }
        }
        when {
            resting != null -> fill(canvas, resting, radius)
            radius == 0f -> canvas.drawBitmap(image, 0f, 0f, null)
            else -> fill(canvas, parts.shader, radius)
        }
        val frame = when {
            held -> heldFrame
            focused && framed && parts.castsOutlineShadow -> focusFrame
            else -> null
        }
        if (frame != null) {
            // The line's middle runs half its width inside the edge, its corners as much tighter.
            val inset = frame.strokeWidth / 2
            frameBounds.set(inset, inset, bounds.right - inset, bounds.bottom - inset)
            val frameRadius = (radius - inset).coerceAtLeast(0f)
            canvas.drawRoundRect(frameBounds, frameRadius, frameRadius, frame)
        }
    }

    /** Fills the tile's shape, its corners [radius] px round, through [shader]. */
    private fun fill(canvas: Canvas, shader: Shader, radius: Float) {
        imagePaint.shader = shader
        if (radius == 0f) canvas.drawRect(bounds, imagePaint) else canvas.drawRoundRect(bounds, radius, radius, imagePaint)
        imagePaint.shader = null
    }

    /**
     * The glass behind a see-through picture, stronger as the focus comes; Frosted is drawn as
     * light glass for now. Without one, a [lifted] tile gets a light card that comes with the
     * focus: Android's shadow of the tile would otherwise show through it, a dark box behind
     * nothing. At rest the glass is in the picture's pass instead ([TileParts.restingWithGlass]).
     */
    private fun drawBackdrop(canvas: Canvas, backdrop: TileBackdrop, radius: Float, focus: Float, lifted: Boolean) {
        val strength = when (backdrop) {
            TileBackdrop.NONE -> if (lifted) focus * LIFT_CARD else return
            TileBackdrop.LIGHT_GLASS, TileBackdrop.FROSTED -> LIGHT_GLASS + (LIGHT_GLASS_FOCUSED - LIGHT_GLASS) * focus
            TileBackdrop.DARK_GLASS -> DARK_GLASS + (DARK_GLASS_FOCUSED - DARK_GLASS) * focus
        }
        if (strength <= 0f) return
        glassPaint.color = if (backdrop == TileBackdrop.DARK_GLASS) Color.BLACK else Color.WHITE
        glassPaint.alpha = (strength * OPAQUE).toInt()
        canvas.drawRoundRect(bounds, radius, radius, glassPaint)
    }

    private fun frame(color: Int, context: Context) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.getDimension(R.dimen.tile_frame)
        this.color = color
    }

    /**
     * A glass of [color] at [strength], as [drawBackdrop] draws it at rest, as a shader: a gradient of
     * one color, since Android has no public shader of a color, and below API 28 composes a bitmap's
     * shader only with one of another kind.
     */
    private fun glass(color: Int, strength: Float): Shader {
        val glass = Color.argb((strength * OPAQUE).toInt(), Color.red(color), Color.green(color), Color.blue(color))
        return LinearGradient(0f, 0f, 1f, 0f, glass, glass, Shader.TileMode.CLAMP)
    }

    companion object {
        private const val OPAQUE = 255

        /** The glasses' strength at rest and focused: white, and black. */
        const val LIGHT_GLASS = 0.12f
        const val LIGHT_GLASS_FOCUSED = 0.26f
        const val DARK_GLASS = 0.30f
        const val DARK_GLASS_FOCUSED = 0.45f

        /** The light card a lifted tile without a backdrop gets, focused. */
        const val LIFT_CARD = 0.22f

        /** How strong Android's shadow of a see-through tile is, lifted: lighter, behind glass. */
        const val SEE_THROUGH_OUTLINE = 0.3f

        /** How dark the shadow of a picture's shape is, focused. */
        const val SHAPE_SHADOW = 0.6f

        /** How many times the band around a picture's shape is laid on. */
        private const val BAND_COATS = 3
    }
}

/**
 * A tile's own parts, as [TilePainter.draw] draws it ([TilePainter.parts]): its [image], the shader
 * that fills its rounded shape with it, and what its look needs: the backdrop behind a
 * see-through picture (none for an opaque one), and the picture's shape where the focus follows it.
 */
class TileParts internal constructor(
    val image: Bitmap,
    seeThrough: Boolean,
    shape: ShapeMasks<Bitmap>?,
    backdrop: TileBackdrop,
    /** The see-through picture's glass at its resting strength, as a shader; none without glass. */
    restingGlass: Shader?,
) {
    internal val shader = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        // On API 36 a bitmap inside a ComposeShader is sampled without the paint's filtering; the
        // shader can be told itself from API 33. The same as the paint's for the picture alone.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) filterMode = BitmapShader.FILTER_MODE_LINEAR
    }

    /**
     * The picture over its glass at rest, in one shader: the tile drawn in one pass, as an opaque
     * one is. Not while the focus comes: the glass grows stronger with it, and the band and the
     * shape's shadow go between it and the picture.
     */
    internal val restingWithGlass: Shader? = restingGlass?.let { ComposeShader(it, shader, PorterDuff.Mode.SRC_OVER) }

    internal val backdrop: TileBackdrop? = if (seeThrough) backdrop else null

    /** The masks of the picture's shape, placed on the tile at its scale. */
    internal val band = shape?.band?.let(::PlacedMask)
    internal val shadow = shape?.shadow?.let(::PlacedMask)

    /**
     * Whether a lifted tile casts Android's shadow of its outline, and a framed one has the frame
     * inside its edge: not where the focus follows the picture's shape.
     */
    val castsOutlineShadow: Boolean = shape == null

    /**
     * How strong that shadow is: whole under an opaque image, lighter under a see-through picture,
     * whose glass it shows through, none where the picture casts its own.
     */
    val outlineAlpha: Float = when {
        !castsOutlineShadow -> 0f
        seeThrough -> TilePainter.SEE_THROUGH_OUTLINE
        else -> 1f
    }

    /** Whether the tile looks different as the focus comes and goes, besides its zoom and frame. */
    val followsFocus: Boolean = seeThrough

}

/** A mask of a picture's shape, at half the tile's scale, and the [matrix] that draws it in place at the tile's. */
internal class PlacedMask(mask: TileMask<Bitmap>) {
    val image: Bitmap = mask.image
    val matrix = Matrix().apply {
        setScale(TileShapes.SCALE.toFloat(), TileShapes.SCALE.toFloat())
        postTranslate((mask.left * TileShapes.SCALE).toFloat(), (mask.top * TileShapes.SCALE).toFloat())
    }
}
