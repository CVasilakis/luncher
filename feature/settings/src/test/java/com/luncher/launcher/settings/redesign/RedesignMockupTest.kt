package com.luncher.launcher.settings.redesign

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.github.takahirom.roborazzi.captureRoboImage
import com.luncher.launcher.testing.TV_1080P
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Throwaway mockups of the settings redesign options: plain Views, shape drawables and one custom
// icon drawable, drawn by Robolectric at 1080p. Not part of the project.

private class Palette(
    val background: Int, val surface: Int, val surfaceHigh: Int, val surfaceHighest: Int,
    val onSurface: Int, val onSurfaceVariant: Int, val outline: Int, val outlineVariant: Int,
    val primary: Int, val onPrimary: Int, val primaryContainer: Int, val onPrimaryContainer: Int,
    val error: Int, val onError: Int, val errorContainer: Int, val onErrorContainer: Int,
    val inverseSurface: Int, val inverseOnSurface: Int, val dark: Boolean,
)

private class Accent(val vivid: Int, val t10: Int, val t20: Int, val t30: Int, val t40: Int, val t80: Int, val t90: Int)

private val ORANGE = Accent(0xFFFF7043.toInt(), 0xFF3A0B00.toInt(), 0xFF5E1700.toInt(), 0xFF852400.toInt(),
    0xFFAB3500.toInt(), 0xFFFFB59D.toInt(), 0xFFFFDBD0.toInt())
private val BLUE = Accent(0xFF4C8DF6.toInt(), 0xFF001A41.toInt(), 0xFF002E69.toInt(), 0xFF004494.toInt(),
    0xFF005AC1.toInt(), 0xFFADC6FF.toInt(), 0xFFD8E2FF.toInt())

private fun c(v: Long) = v.toInt()

/** Option A takes the accent's pastel tones, as Material's tonal scheme does; the others its vivid color. */
private fun palette(accent: Accent, dark: Boolean, tonal: Boolean, translucent: Boolean = false): Palette =
    if (dark) Palette(
        background = c(0xFF101418), surface = if (translucent) c(0xF2161B22) else c(0xFF1A2028),
        surfaceHigh = if (translucent) c(0x14FFFFFF) else c(0xFF242B34), surfaceHighest = c(0xFF2F3742),
        onSurface = c(0xFFEEF1F5), onSurfaceVariant = c(0xFFA9B3BF),
        outline = if (translucent) c(0x40FFFFFF) else c(0xFF5C6672),
        outlineVariant = if (translucent) c(0x24FFFFFF) else c(0xFF343C46),
        primary = if (tonal) accent.t80 else accent.vivid, onPrimary = if (tonal) accent.t20 else accent.t10,
        primaryContainer = if (tonal) accent.t30 else blend(accent.vivid, c(0xFF1A2028), 0.28f),
        onPrimaryContainer = accent.t90,
        error = c(0xFFFFB4AB), onError = c(0xFF690005), errorContainer = c(0xFF5C1A1A), onErrorContainer = c(0xFFFFDAD6),
        inverseSurface = c(0xFFEEF1F5), inverseOnSurface = c(0xFF1A2028), dark = true,
    ) else Palette(
        background = c(0xFFF3F5F8), surface = if (translucent) c(0xF5FFFFFF) else c(0xFFFFFFFF),
        surfaceHigh = if (translucent) c(0x0F000000) else c(0xFFE8ECF1), surfaceHighest = c(0xFFDCE2E9),
        onSurface = c(0xFF161B22), onSurfaceVariant = c(0xFF4F5A66),
        outline = if (translucent) c(0x40000000) else c(0xFF8A95A1),
        outlineVariant = if (translucent) c(0x1F000000) else c(0xFFCDD4DC),
        primary = if (tonal) accent.t40 else accent.t40, onPrimary = c(0xFFFFFFFF),
        primaryContainer = accent.t90, onPrimaryContainer = accent.t10,
        error = c(0xFFBA1A1A), onError = c(0xFFFFFFFF), errorContainer = c(0xFFFFDAD6), onErrorContainer = c(0xFF410002),
        inverseSurface = c(0xFF1F252D), inverseOnSurface = c(0xFFEEF1F5), dark = false,
    )

private fun blend(a: Int, b: Int, t: Float): Int {
    fun ch(s: Int) = ((a shr s and 0xFF) * t + (b shr s and 0xFF) * (1 - t)).toInt() shl s
    return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
}

private enum class Focus { INVERSE, RING, ACCENT_FILL, OUTLINE }
private enum class Rest { GROUPED, CARDS, NONE, HAIRLINE }
private enum class Placement { RIGHT_SHEET, CENTER, LEFT_SHEET }
private enum class ButtonKind { PRIMARY, SECONDARY, DANGER }

private class Look(
    val id: String, val p: Palette, val focus: Focus, val rest: Rest, val placement: Placement,
    val entryCorner: Float, val panelCorner: Float, val pillEntries: Boolean,
    val focusScale: Float, val titleSize: Float,
)

private fun lookA(p: Palette = palette(ORANGE, dark = true, tonal = true)) =
    Look("A", p, Focus.INVERSE, Rest.GROUPED, Placement.RIGHT_SHEET, 12f, 28f, false, 1.05f, 34f)
private fun lookB(p: Palette = palette(ORANGE, dark = true, tonal = false)) =
    Look("B", p, Focus.RING, Rest.CARDS, Placement.CENTER, 16f, 28f, false, 1.04f, 34f)
private fun lookC(p: Palette = palette(ORANGE, dark = true, tonal = false)) =
    Look("C", p, Focus.ACCENT_FILL, Rest.NONE, Placement.LEFT_SHEET, 0f, 32f, true, 1.06f, 44f)
private fun lookD(p: Palette = palette(ORANGE, dark = true, tonal = false, translucent = true)) =
    Look("D", p, Focus.OUTLINE, Rest.HAIRLINE, Placement.CENTER, 14f, 24f, false, 1.03f, 34f)

private enum class Glyph { CHEVRON, CHECK, PLUS, TRASH, EYE_OFF, PALETTE, GEAR, MOVE, FOLDER, CARET, OPEN, WARNING, USB, TV, GRID, CLOCK, IMAGE, RESET, PENCIL, CHEVRON_LEFT, CARET_UP, INFO }

/** A line icon on a 24-unit grid, stroked. */
private class Icon(private val glyph: Glyph, color: Int, private val sizePx: Int, private val density: Float) : Drawable() {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        this.color = color
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }
    private val path = Path()

    override fun getIntrinsicWidth() = sizePx
    override fun getIntrinsicHeight() = sizePx

    override fun draw(canvas: Canvas) {
        val s = bounds.width() / 24f
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(s, s)
        stroke.strokeWidth = 2f * (1.5f / (s / density).coerceAtLeast(0.5f)).coerceIn(1f, 1.4f) / 1.2f
        path.reset()
        when (glyph) {
            Glyph.CHEVRON -> line(9f, 6f, 15f, 12f, 9f, 18f)
            Glyph.CARET -> line(7f, 10f, 12f, 15f, 17f, 10f)
            Glyph.CHECK -> line(5f, 12.5f, 10f, 17f, 19f, 7f)
            Glyph.PLUS -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            Glyph.TRASH -> {
                line(4f, 7f, 20f, 7f); line(9f, 7f, 9f, 4f, 15f, 4f, 15f, 7f)
                line(6f, 7f, 7f, 20f, 17f, 20f, 18f, 7f); line(10f, 11f, 10f, 16f); line(14f, 11f, 14f, 16f)
            }
            Glyph.EYE_OFF -> {
                path.moveTo(2.5f, 12f); path.quadTo(12f, 3f, 21.5f, 12f); path.quadTo(12f, 21f, 2.5f, 12f)
                path.addCircle(12f, 12f, 3f, Path.Direction.CW); line(4f, 4f, 20f, 20f)
            }
            Glyph.PALETTE -> {
                path.addCircle(12f, 12f, 9f, Path.Direction.CW)
                for ((x, y) in listOf(8f to 9.5f, 12f to 7f, 16f to 9.5f, 16.5f to 14f)) canvas.drawCircle(x, y, 1.4f, fill)
            }
            Glyph.GEAR -> {
                path.addCircle(12f, 12f, 3f, Path.Direction.CW); path.addCircle(12f, 12f, 7f, Path.Direction.CW)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    line(12f + 7f * Math.cos(a).toFloat(), 12f + 7f * Math.sin(a).toFloat(),
                        12f + 9.5f * Math.cos(a).toFloat(), 12f + 9.5f * Math.sin(a).toFloat())
                }
            }
            Glyph.MOVE -> {
                line(12f, 3f, 12f, 21f); line(3f, 12f, 21f, 12f)
                line(9f, 6f, 12f, 3f, 15f, 6f); line(9f, 18f, 12f, 21f, 15f, 18f)
                line(6f, 9f, 3f, 12f, 6f, 15f); line(18f, 9f, 21f, 12f, 18f, 15f)
            }
            Glyph.FOLDER -> { line(3f, 6f, 9f, 6f, 11f, 8.5f, 21f, 8.5f, 21f, 19f, 3f, 19f); path.close() }
            Glyph.OPEN -> { line(11f, 5f, 5f, 5f, 5f, 19f, 19f, 19f, 19f, 13f); line(14f, 5f, 19f, 5f, 19f, 10f); line(19f, 5f, 11f, 13f) }
            Glyph.WARNING -> { line(12f, 3.5f, 21.5f, 20f, 2.5f, 20f); path.close(); line(12f, 10f, 12f, 14f); canvas.drawCircle(12f, 17f, 1.1f, fill) }
            Glyph.USB -> { line(8f, 3f, 16f, 3f, 16f, 9f, 8f, 9f); path.close(); line(6f, 9f, 18f, 9f, 18f, 17f, 15f, 21f, 9f, 21f, 6f, 17f); path.close() }
            Glyph.TV -> { line(3f, 5f, 21f, 5f, 21f, 17f, 3f, 17f); path.close(); line(8f, 21f, 16f, 21f) }
            Glyph.GRID -> for ((x, y) in listOf(4f to 4f, 13f to 4f, 4f to 13f, 13f to 13f)) path.addRoundRect(RectF(x, y, x + 7f, y + 7f), 1.5f, 1.5f, Path.Direction.CW)
            Glyph.CLOCK -> { path.addCircle(12f, 12f, 9f, Path.Direction.CW); line(12f, 7f, 12f, 12f, 15.5f, 14f) }
            Glyph.IMAGE -> { path.addRoundRect(RectF(3f, 5f, 21f, 19f), 2f, 2f, Path.Direction.CW); line(3.5f, 17f, 9f, 11f, 13f, 15f, 16f, 12f, 20.5f, 16.5f); canvas.drawCircle(16f, 8.5f, 1.4f, fill) }
            Glyph.RESET -> { path.addArc(RectF(4f, 4f, 20f, 20f), -60f, 300f); line(3.5f, 4f, 4.5f, 9f, 9.5f, 8f) }
            Glyph.PENCIL -> { line(4f, 20f, 5f, 15f, 15.5f, 4.5f, 19.5f, 8.5f, 9f, 19f); path.close(); line(13f, 7f, 17f, 11f) }
            Glyph.CHEVRON_LEFT -> line(15f, 6f, 9f, 12f, 15f, 18f)
            Glyph.CARET_UP -> line(7f, 14f, 12f, 9f, 17f, 14f)
            Glyph.INFO -> { path.addCircle(12f, 12f, 9f, Path.Direction.CW); line(12f, 11f, 12f, 16.5f); canvas.drawCircle(12f, 7.8f, 1.1f, fill) }
        }
        canvas.drawPath(path, stroke)
        canvas.restore()
    }

    private fun line(vararg xy: Float) {
        path.moveTo(xy[0], xy[1])
        var i = 2
        while (i < xy.size) { path.lineTo(xy[i], xy[i + 1]); i += 2 }
    }

    override fun setAlpha(alpha: Int) { stroke.alpha = alpha; fill.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { stroke.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** A home screen sample, as in the editors' preview: a wallpaper, the top bar's blocks, tiles. */
private class PreviewView(activity: Activity, private val corner: Float, private val tile: Int, private val focusColor: Int) : View(activity) {
    private val wallpaper = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c(0xFF4B2B7F), c(0xFF9C5A78), c(0xFFE58B58)))
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    init {
        background = GradientDrawable().apply { cornerRadius = corner; setColor(Color.BLACK) }
        clipToOutline = true
    }

    override fun onDraw(canvas: Canvas) {
        wallpaper.setBounds(0, 0, width, height); wallpaper.draw(canvas)
        val u = width / 960f
        paint.style = Paint.Style.FILL; paint.color = Color.WHITE
        r.set(48 * u, 34 * u, 110 * u, 52 * u); canvas.drawRoundRect(r, 3 * u, 3 * u, paint)
        paint.color = c(0xB3FFFFFF); r.set(122 * u, 40 * u, 260 * u, 48 * u); canvas.drawRoundRect(r, 3 * u, 3 * u, paint)
        paint.color = Color.WHITE; canvas.drawCircle(900 * u, 43 * u, 10 * u, paint)
        val w = 153.6f * u; val h = 86f * u; val g = 24f * u
        val rows = listOf(listOf(314.4f, 338.4f + 153.6f) to 320f, (0 until 5).map { 48f + it * 177.6f } to 430f)
        for ((xs, y) in rows) for (x in xs) {
            val left = if (xs.size == 5) x * u else x * u
            r.set(left, y * u, left + w, y * u + h)
            paint.style = Paint.Style.FILL; paint.color = tile; canvas.drawRoundRect(r, 12 * u, 12 * u, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * u.coerceAtLeast(0.6f); paint.color = c(0x99FFFFFF)
            canvas.drawRoundRect(r, 12 * u, 12 * u, paint)
        }
        r.set(314.4f * u - 6 * u, 320 * u - 4 * u, 314.4f * u + w + 6 * u, 320 * u + h + 4 * u)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3 * u; paint.color = focusColor
        canvas.drawRoundRect(r, 14 * u, 14 * u, paint)
        if (g < 0) invalidate()
    }
}

private class Ui(val activity: Activity, val look: Look) {
    val p = look.p
    private val density = activity.resources.displayMetrics.density
    fun dp(v: Float) = v * density
    fun px(v: Float) = (v * density + 0.5f).toInt()
    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun shape(fill: Int, corner: Float, strokeColor: Int = 0, strokeWidth: Float = 0f) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(corner)
        if (strokeWidth > 0f) setStroke(px(strokeWidth), strokeColor)
    }

    fun text(s: String, size: Float, color: Int, bold: Boolean = false, lines: Int = 1) = TextView(activity).apply {
        text = s; setTextSize(TypedValue.COMPLEX_UNIT_SP, size); setTextColor(color)
        if (bold) typeface = medium
        maxLines = lines; ellipsize = TextUtils.TruncateAt.END; includeFontPadding = false
        if (lines > 1) setLineSpacing(0f, 1.2f)
    }

    fun icon(g: Glyph, color: Int, size: Float = 24f) = View(activity).apply {
        background = Icon(g, color, px(size), density)
        layoutParams = LinearLayout.LayoutParams(px(size), px(size))
    }

    fun lp(w: Int = ViewGroup.LayoutParams.WRAP_CONTENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight)

    val match = ViewGroup.LayoutParams.MATCH_PARENT

    fun column(vararg children: View) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; children.forEach { addView(it) }
    }

    fun row(vararg children: View) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; children.forEach { addView(it) }
    }

    fun space(w: Float = 0f, h: Float = 0f) = View(activity).apply { layoutParams = LinearLayout.LayoutParams(px(w), px(h)) }

    // --- Entries ---

    fun entryFill(focused: Boolean, selected: Boolean): Drawable? {
        val corner = if (look.pillEntries) 999f else look.entryCorner
        return when {
            focused -> when (look.focus) {
                Focus.INVERSE -> shape(p.inverseSurface, corner)
                Focus.RING -> shape(p.surfaceHighest, corner, p.primary, 3f)
                Focus.ACCENT_FILL -> shape(p.primary, corner)
                Focus.OUTLINE -> shape(if (p.dark) c(0x1FFFFFFF) else c(0x14000000), corner, p.onSurface, 2f)
            }
            selected -> when (look.rest) {
                Rest.CARDS -> shape(p.primaryContainer, corner)
                Rest.HAIRLINE -> shape(c(0x00000000), corner, p.primary, 1.5f)
                else -> shape(withAlpha(p.primary, 0x29), corner)
            }
            else -> when (look.rest) {
                Rest.CARDS -> shape(p.surfaceHigh, corner)
                Rest.HAIRLINE -> shape(c(0x00000000), corner, p.outline, 1f)
                else -> null
            }
        }
    }

    fun contentColor(focused: Boolean, selected: Boolean = false) = when {
        focused && look.focus == Focus.INVERSE -> p.inverseOnSurface
        focused && look.focus == Focus.ACCENT_FILL -> p.onPrimary
        selected && look.rest == Rest.CARDS -> p.onPrimaryContainer
        else -> p.onSurface
    }

    fun supportingColor(focused: Boolean) = when {
        focused && look.focus == Focus.INVERSE -> withAlpha(p.inverseOnSurface, 0xC0)
        focused && look.focus == Focus.ACCENT_FILL -> withAlpha(p.onPrimary, 0xC0)
        else -> p.onSurfaceVariant
    }

    fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

    fun lift(v: View, focused: Boolean) {
        if (!focused) return
        v.scaleX = look.focusScale; v.scaleY = look.focusScale
        if (look.focus == Focus.RING) v.elevation = dp(10f)
        if (look.focus == Focus.INVERSE || look.focus == Focus.ACCENT_FILL) v.elevation = dp(6f)
    }

    /**
     * A list entry: an optional leading icon or picture, a title and a supporting line, and an
     * optional trailing value, mark or chevron.
     */
    fun entry(
        title: String, focused: Boolean = false, selected: Boolean = false, leading: Glyph? = null, thumb: IntArray? = null,
        supporting: String? = null, supportingColor: Int? = null, supportingGlyph: Glyph? = null,
        value: String? = null, trailing: Glyph? = null, height: Float = 56f, disabled: Boolean = false, titleColor: Int? = null,
    ): LinearLayout {
        val fg = contentColor(focused, selected)
        val sub = supportingColor(focused)
        val e = row()
        e.minimumHeight = px(height)
        e.setPadding(px(if (look.pillEntries) 24f else 16f), px(8f), px(if (look.pillEntries) 20f else 16f), px(8f))
        e.background = entryFill(focused, selected)
        if (leading != null) {
            val iconColor = if (focused) fg else titleColor ?: if (selected) p.primary else p.onSurfaceVariant
            e.addView(icon(leading, iconColor))
            e.addView(space(w = 16f))
        }
        if (thumb != null) {
            val t = View(activity).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, thumb).apply { cornerRadius = dp(8f) }
                layoutParams = lp(px(80f), px(45f))
            }
            e.addView(t); e.addView(space(w = 16f))
        }
        val texts = column(text(title, 18f, if (titleColor != null && !focused) titleColor else if (disabled) withAlpha(fg, 0x61) else if (selected && !focused && look.rest != Rest.CARDS) p.primary else fg, bold = selected))
        if (supporting != null) {
            val s = text(supporting, 14f, supportingColor?.takeIf { !focused || look.focus == Focus.OUTLINE || look.focus == Focus.RING } ?: sub)
            val sr = if (supportingGlyph != null) row(icon(supportingGlyph, s.currentTextColor, 16f), space(w = 6f), s) else s
            texts.addView(space(h = 4f)); texts.addView(sr)
        }
        texts.layoutParams = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        e.addView(texts)
        if (value != null) {
            val v = text(value, 16f, if (focused) fg else if (disabled) withAlpha(p.onSurfaceVariant, 0x61) else p.onSurfaceVariant)
            if (look.rest == Rest.GROUPED || look.rest == Rest.NONE) {
                // The value as a chip, so it reads as something to change.
                val chipFill = when {
                    focused && look.focus == Focus.INVERSE -> withAlpha(p.inverseOnSurface, 0x1F)
                    focused && look.focus == Focus.ACCENT_FILL -> withAlpha(p.onPrimary, 0x1F)
                    else -> p.surfaceHighest
                }
                val chip = row(v, space(w = 4f), icon(Glyph.CARET, v.currentTextColor, 20f))
                chip.setPadding(px(14f), px(6f), px(8f), px(6f))
                chip.background = shape(chipFill, 999f)
                e.addView(chip)
            } else {
                e.addView(v); e.addView(space(w = 4f)); e.addView(icon(Glyph.CARET, v.currentTextColor, 20f))
            }
        }
        if (trailing != null) {
            e.addView(space(w = 12f))
            e.addView(icon(trailing, if (focused) fg else if (trailing == Glyph.CHECK) p.primary else p.onSurfaceVariant))
        }
        lift(e, focused)
        return e
    }

    // --- Buttons ---

    fun button(label: String, kind: ButtonKind, focused: Boolean = false, glyph: Glyph? = null, disabled: Boolean = false): LinearLayout {
        val pill = 999f
        val (bg, fg) = if (focused) when (look.focus) {
            Focus.INVERSE -> if (kind == ButtonKind.DANGER) shape(p.error, pill) to p.onError else shape(p.inverseSurface, pill) to p.inverseOnSurface
            Focus.RING -> buttonRest(kind).let { (d, f) -> (d as GradientDrawable).apply { setStroke(px(3f), if (kind == ButtonKind.DANGER) p.error else p.onSurface) } to f }
            Focus.ACCENT_FILL -> if (kind == ButtonKind.DANGER) shape(p.error, pill) to p.onError else shape(p.primary, pill) to p.onPrimary
            Focus.OUTLINE -> if (kind == ButtonKind.DANGER) shape(p.error, pill) to p.onError else shape(p.onSurface, pill) to p.background
        } else buttonRest(kind)
        val color = if (disabled) withAlpha(fg, 0x61) else fg
        val b = row()
        b.gravity = Gravity.CENTER
        b.minimumHeight = px(48f)
        b.setPadding(px(if (glyph != null) 20f else 28f), px(10f), px(28f), px(10f))
        b.background = bg
        if (disabled) b.background.alpha = 0x61
        if (glyph != null) { b.addView(icon(glyph, color, 20f)); b.addView(space(w = 8f)) }
        b.addView(text(label, 16f, color, bold = true))
        if (focused) {
            b.scaleX = 1.1f; b.scaleY = 1.1f
            if (look.focus != Focus.OUTLINE) b.elevation = dp(6f)
        }
        return b
    }

    private fun buttonRest(kind: ButtonKind): Pair<Drawable, Int> {
        val pill = 999f
        return when (look.id) {
            "A" -> when (kind) {
                ButtonKind.PRIMARY -> shape(p.primary, pill) to p.onPrimary
                ButtonKind.SECONDARY -> shape(p.surfaceHighest, pill) to p.onSurface
                ButtonKind.DANGER -> shape(0, pill, p.error, 1.5f) to p.error
            }
            "B" -> when (kind) {
                ButtonKind.PRIMARY -> shape(p.primary, pill) to p.onPrimary
                ButtonKind.SECONDARY -> shape(p.primaryContainer, pill) to p.onPrimaryContainer
                ButtonKind.DANGER -> shape(p.errorContainer, pill) to p.onErrorContainer
            }
            "C" -> when (kind) {
                ButtonKind.PRIMARY -> shape(0, pill, p.primary, 2f) to p.primary
                ButtonKind.SECONDARY -> shape(0, pill, p.outline, 2f) to p.onSurface
                ButtonKind.DANGER -> shape(0, pill, p.error, 2f) to p.error
            }
            else -> when (kind) {
                ButtonKind.PRIMARY -> shape(withAlpha(p.primary, 0x33), pill, p.primary, 1f) to p.onSurface
                ButtonKind.SECONDARY -> shape(0, pill, p.outline, 1f) to p.onSurface
                ButtonKind.DANGER -> shape(0, pill, withAlpha(p.error, 0x99), 1f) to p.error
            }
        }
    }

    // --- Panels ---

    fun panelBackground(corner: Float = look.panelCorner): Drawable = when (look.rest) {
        Rest.HAIRLINE -> shape(p.surface, corner, p.outlineVariant, 1f)
        else -> shape(p.surface, corner)
    }

    fun sectionLabel(s: String) = text(s, 14f, p.onSurfaceVariant, bold = true).apply { letterSpacing = 0.04f }

    /** Entries in a list: grouped in a container (A), or each its own card, a gap apart. */
    fun list(vararg entries: View, gap: Float = if (look.rest == Rest.CARDS || look.rest == Rest.HAIRLINE) 8f else 4f): LinearLayout {
        val l = column()
        entries.forEachIndexed { i, e ->
            if (i > 0) l.addView(space(h = gap))
            l.addView(e, lp(match))
        }
        if (look.rest == Rest.GROUPED) {
            l.background = shape(p.surface, look.entryCorner + 8f)
            l.setPadding(px(8f), px(8f), px(8f), px(8f))
        }
        return l
    }
}

private val SUNSET = intArrayOf(c(0xFF4B2B7F), c(0xFFE58B58))
private val GREEN = intArrayOf(c(0xFF2E8B4F), c(0xFF7A5CE0))
private val BEACH = intArrayOf(c(0xFF2F6AA5), c(0xFFE05FA8))
private val PHONE = intArrayOf(c(0xFF8C2F5C), c(0xFFA8E05F))

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-$TV_1080P")
class RedesignMockupTest {

    private fun render(name: String, look: Look, after: (Ui.(FrameLayout) -> Unit)? = null, build: Ui.(FrameLayout) -> Unit) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val ui = Ui(activity, look)
        val root = FrameLayout(activity)
        root.setBackgroundColor(look.p.background)
        ui.build(root)
        activity.actionBar?.hide()
        activity.setContentView(root)
        if (after != null) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            ui.after(root)
        }
        unclip(root)
        activity.window.decorView.captureRoboImage("../../.redesign/shots/$name.png")
    }

    private fun unclip(v: View) {
        if (v is ViewGroup) {
            if (v.tag != "clip") { v.clipChildren = false; v.clipToPadding = false }
            for (i in 0 until v.childCount) unclip(v.getChildAt(i))
        }
    }

    // --- The home screen behind the floating panels ---

    private fun Ui.home(root: FrameLayout, focusedTile: Int?) {
        root.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c(0xFF2A1B4A), c(0xFF5A3558), c(0xFF8A5040)))
        val clock = row(text("14:05", 48f, Color.WHITE), space(w = 20f), text("Sunday, September 27", 24f, c(0xB3FFFFFF)))
        root.addView(clock, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = px(48f); topMargin = px(30f) })
        root.addView(icon(Glyph.GEAR, Color.WHITE, 32f), FrameLayout.LayoutParams(px(32f), px(32f)).apply {
            gravity = Gravity.END; rightMargin = px(56f); topMargin = px(40f)
        })
        val names = listOf("Movies", "Music", "News", "Photos", "Radio")
        val rows = listOf(listOf("Games", "Podcasts") to listOf(314.4f, 492f), names to (0 until 5).map { 48f + it * 177.6f })
        var index = 0
        for ((rowIndex, r) in rows.withIndex()) {
            val (labels, xs) = r
            val y = if (rowIndex == 0) 310f else 420f
            for ((i, label) in labels.withIndex()) {
                val focused = index == focusedTile
                val t = FrameLayout(activity)
                t.background = if (focused) shape(c(0xFF2A3440), 12f, c(0xFFFF7043), 3f) else shape(c(0xE626303B), 12f)
                t.addView(text(label, 16f, Color.WHITE), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
                if (focused) { t.scaleX = 1.1f; t.scaleY = 1.1f }
                root.addView(t, FrameLayout.LayoutParams(px(153.6f), px(86f)).apply { leftMargin = px(xs[i]); topMargin = px(y) })
                index++
            }
        }
    }

    private fun Ui.scrim(root: FrameLayout) {
        root.addView(View(activity).apply { setBackgroundColor(c(0x99000000)) }, FrameLayout.LayoutParams(-1, -1))
    }

    private fun Ui.tileMenu(root: FrameLayout) {
        home(root, focusedTile = 6)
        val menu = column(
            text("Radio", 14f, p.onSurfaceVariant, bold = true).apply { setPadding(px(16f), px(4f), px(16f), px(8f)) },
            list(entry("Move", focused = true, leading = Glyph.MOVE, height = 52f), entry("Hide", leading = Glyph.EYE_OFF, height = 52f), gap = 4f)
                .apply { if (look.rest == Rest.GROUPED) { background = null; setPadding(0, 0, 0, 0) } },
        )
        menu.setPadding(px(12f), px(14f), px(12f), px(12f))
        menu.background = panelBackground(if (look.pillEntries) 28f else 20f)
        menu.elevation = dp(16f)
        root.addView(menu, FrameLayout.LayoutParams(px(220f), -2).apply { leftMargin = px(912f - 220f); topMargin = px(228f) })
    }

    private fun Ui.settingsPanel(root: FrameLayout) {
        home(root, focusedTile = null)
        scrim(root)
        val entries = list(
            entry("Hide apps", focused = true, leading = Glyph.EYE_OFF, trailing = Glyph.CHEVRON, supporting = "2 hidden"),
            entry("Appearance", leading = Glyph.PALETTE, trailing = Glyph.CHEVRON, supporting = "Tiles, time and date, background"),
            entry("System settings", leading = Glyph.TV, trailing = Glyph.OPEN, supporting = "The TV's own settings"),
        )
        when (look.placement) {
            Placement.RIGHT_SHEET -> {
                val sheet = column(text("Settings", 28f, p.onSurface), space(h = 24f), entries)
                sheet.setPadding(px(24f), px(32f), px(24f), px(24f))
                sheet.background = panelBackground()
                sheet.elevation = dp(16f)
                root.addView(sheet, FrameLayout.LayoutParams(px(360f), -1, Gravity.END).apply {
                    topMargin = px(24f); bottomMargin = px(24f); rightMargin = px(24f)
                })
            }
            Placement.LEFT_SHEET -> {
                val sheet = column(text("Settings", look.titleSize, p.onSurface), space(h = 8f),
                    text("Luncher 0.3.0", 16f, p.onSurfaceVariant), space(h = 40f), entries)
                sheet.setPadding(px(48f), px(48f), px(32f), px(32f))
                sheet.background = GradientDrawable().apply {
                    setColor(p.surface); val r = dp(look.panelCorner)
                    cornerRadii = floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
                }
                sheet.elevation = dp(16f)
                root.addView(sheet, FrameLayout.LayoutParams(px(420f), -1, Gravity.START))
            }
            Placement.CENTER -> {
                val panel = column(text("Settings", 28f, p.onSurface), space(h = 24f), entries)
                panel.setPadding(px(28f), px(28f), px(28f), px(28f))
                panel.background = panelBackground()
                panel.elevation = dp(16f)
                root.addView(panel, FrameLayout.LayoutParams(px(460f), -2, Gravity.CENTER))
            }
        }
    }

    private fun Ui.backgroundEditor(
        root: FrameLayout, focusSave: Boolean = false, homeAccent: Int = p.primary, focusPicture: Boolean = !focusSave,
        optionsOverride: (Ui.() -> View)? = null, optionsHeight: Float = -1f, labelHint: String? = null, startShift: Float = 0f,
    ) {
        val config = activity.resources.configuration
        val compact = config.screenHeightDp < 450
        val gap = if (compact) 12f else 20f
        val fieldHeight = if (compact) 48f else 56f
        val columnWidth = (config.screenWidthDp - 96f - 40f) / 2f
        val narrow = columnWidth < 340f
        val previewHeight = minOf(columnWidth * 9f / 16f,
            config.screenHeightDp - 27f - (if (compact) 27f else 32f) - 32f - 3 * gap - (if (optionsHeight > 0f) optionsHeight else 2 * fieldHeight + 12f) - 48f)
        val preview = PreviewView(activity, dp(16f), c(0x33FFFFFF), homeAccent)
        val options = optionsOverride?.invoke(this) ?: column(
            row(field("Fit", "Fill", height = fieldHeight).apply { layoutParams = lp(0, -2, 1f) }, space(w = 12f),
                field("Position", "Center", disabled = true, height = fieldHeight).apply { layoutParams = lp(0, -2, 1f) }),
            space(h = 12f),
            row(field("Dim", "Light", height = fieldHeight).apply { layoutParams = lp(0, -2, 1f) }, space(w = 12f),
                field("Blur", "Light", height = fieldHeight).apply { layoutParams = lp(0, -2, 1f); tag = "blur" }),
        )
        val buttons = row(
            button("Save", ButtonKind.PRIMARY, focused = focusSave, glyph = if (narrow) null else Glyph.CHECK).apply {
                layoutParams = lp(0, -2, 1f); if (narrow) setPadding(px(12f), paddingTop, px(12f), paddingBottom)
            },
            space(w = if (narrow) 12f else 16f),
            button("Delete all", ButtonKind.DANGER, glyph = if (narrow) null else Glyph.TRASH).apply {
                layoutParams = lp(0, -2, 1f); if (narrow) setPadding(px(12f), paddingTop, px(12f), paddingBottom)
            },
        )
        val start = column(
            if (labelHint == null) sectionLabel("BACKGROUND")
            else row(sectionLabel("BACKGROUND").apply { layoutParams = lp(0, -2, 1f) }, text(labelHint, 14f, p.primary, bold = true)),
            space(h = 12f),
        )

        start.addView(preview, lp(px(previewHeight * 16f / 9f), px(previewHeight)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        start.addView(space(h = gap)); start.addView(options, lp(match)); start.addView(space(h = gap)); start.addView(buttons, lp(match))

        val pictures = list(
            entry("No picture", thumb = intArrayOf(c(0xFF0B0E12), c(0xFF161A20)), height = 61f),
            entry("a picture with a name far too long for its row.jpg", thumb = GREEN, height = 61f),
            entry("beach.png", thumb = BEACH, height = 61f),
            entry("phone-photo.jpg", thumb = PHONE, height = 61f),
            entry("sunset.jpg", thumb = SUNSET, focused = focusPicture, selected = true, trailing = Glyph.CHECK, height = 61f).apply { tag = "focusedPicture" },
            addFromFolder(),
            gap = if (look.rest == Rest.GROUPED || look.rest == Rest.NONE) 2f else 8f,
        )
        // Room for the focused entry's zoom inside the list's frame, which clips what scrolls.
        pictures.setPadding(px(12f), px(12f), px(12f), px(12f))
        val scroller = android.widget.ScrollView(activity).apply {
            tag = "clip"; isVerticalScrollBarEnabled = false
            addView(pictures, FrameLayout.LayoutParams(-1, -2))
            post {
                val f = pictures.findViewWithTag<View>("focusedPicture")
                val bottom = f.bottom + px(12f)
                if (bottom > height) scrollTo(0, bottom - height)
            }
        }
        // The container's fill and corners are the frame's, and the frame cuts what scrolls along
        // its outline, so the list keeps its rounded corners while it scrolls.
        val listBackground = pictures.background
        pictures.background = null
        val listFrame = FrameLayout(activity).apply {
            tag = "clip"; background = listBackground; clipToOutline = listBackground != null
            addView(scroller, FrameLayout.LayoutParams(-1, -1))
        }
        val end = column(row(sectionLabel("PICTURES").apply { layoutParams = lp(0, -2, 1f) },
            text(if (compact) "Hold OK: rename, delete" else "Hold OK to rename or delete", 14f, p.onSurfaceVariant)), space(h = 12f),
            listFrame.apply { layoutParams = lp(match, 0, 1f) })
        val startView: View = if (startShift > 0f) android.widget.ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            addView(start, FrameLayout.LayoutParams(-1, -2))
            post { scrollTo(0, px(startShift)) }
        } else start
        val body = row(startView.apply { layoutParams = lp(0, if (startShift > 0f) -1 else -2, 1f) }, space(w = 40f), end.apply { layoutParams = lp(0, -1, 1f) })
        body.gravity = Gravity.TOP
        body.setPadding(px(48f), px(if (compact) 27f else 32f), px(48f), px(27f))
        root.addView(body, FrameLayout.LayoutParams(-1, -1))
    }

    /** An option as a dropdown field: its name above its value, a mark that it opens a list. */
    private fun Ui.field(label: String, value: String, disabled: Boolean = false, height: Float = 56f): View {
        val a = if (disabled) 0x61 else 0xFF
        val f = row(
            column(text(label, 13f, withAlpha(p.onSurfaceVariant, a), bold = true), space(h = 2f), text(value, 17f, withAlpha(p.onSurface, a)))
                .apply { layoutParams = lp(0, -2, 1f) },
            icon(Glyph.CARET, withAlpha(p.onSurfaceVariant, a), 22f),
        )
        f.minimumHeight = px(height)
        f.setPadding(px(16f), px(8f), px(12f), px(8f))
        val corner = if (look.pillEntries) 28f else 12f
        f.background = when (look.rest) {
            Rest.GROUPED, Rest.CARDS -> shape(withAlpha(p.surfaceHigh, if (disabled) 0x80 else 0xFF), corner)
            Rest.NONE -> shape(0, corner, withAlpha(p.outline, a), 1.5f)
            Rest.HAIRLINE -> shape(0, corner, withAlpha(p.outline, a), 1f)
        }
        return f
    }

    private fun Ui.addFromFolder(): View {
        val e = entry("Add from folder", height = 61f)
        // In place of a thumbnail: a plus in a tonal box, of the thumbnails' size.
        val box = FrameLayout(activity).apply {
            background = when (look.rest) {
                Rest.HAIRLINE, Rest.NONE -> shape(0, 8f, p.outline, 1.5f)
                Rest.GROUPED -> shape(p.surfaceHighest, 8f)
                else -> shape(p.primaryContainer, 8f)
            }
            addView(icon(Glyph.PLUS, if (look.rest == Rest.CARDS) p.onPrimaryContainer else p.primary, 24f),
                FrameLayout.LayoutParams(px(24f), px(24f), Gravity.CENTER))
        }
        e.addView(box, 0, lp(px(80f), px(45f)))
        e.addView(space(w = 16f), 1)
        return e
    }

    private fun Ui.folder(root: FrameLayout) {
        val items = list(
            entry("holiday.jpg", focused = true, leading = Glyph.USB, supporting = "USB drive · 4000 × 3000 · 2.4 MB", height = 64f),
            entry("IMG_0001.HEIC", leading = Glyph.TV, supporting = "HEIF (HEIC) not supported on this TV",
                supportingColor = p.error, supportingGlyph = Glyph.WARNING, height = 64f),
            entry("logo-netflix.png", leading = Glyph.USB, supporting = "USB drive · 4000 × 3000 · 2.4 MB", height = 64f),
            entry("wallpaper-4k.jpg", leading = Glyph.TV, supporting = "Local · 4000 × 3000 · 2.4 MB", height = 64f),
            entry("winter.jpg", leading = Glyph.TV, supporting = "Local · 1920 × 1080 · 860 KB", height = 64f),
            gap = if (look.rest == Rest.GROUPED || look.rest == Rest.NONE) 2f else 8f,
        )
        val preview = View(activity).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c(0xFF2E8B6F), c(0xFF6A6FB0), c(0xFFB05FE0))).apply { cornerRadius = dp(16f) }
        }
        val end = column(sectionLabel("PREVIEW"), space(h = 12f))
        end.addView(preview, lp(match, px(336f * 9f / 16f)))
        end.addView(space(h = 12f))
        end.addView(text("holiday.jpg", 16f, p.onSurface))
        end.addView(space(h = 24f))
        end.addView(button("Add all", ButtonKind.SECONDARY, glyph = Glyph.PLUS), lp(match))
        end.addView(space(h = 16f))
        end.addView(text("OK adds a copy to Luncher's pictures. After that, the file and the USB drive are no longer needed.",
            14f, p.onSurfaceVariant, lines = 3))
        val start = column(row(sectionLabel("IN THE FOLDER").apply { layoutParams = lp(0, -2, 1f) }, text("5 pictures", 14f, p.onSurfaceVariant)),
            space(h = 12f), items)
        val body = row(start.apply { layoutParams = lp(0, -2, 1.45f) }, space(w = 40f), end.apply { layoutParams = lp(0, -2, 1f) })
        body.gravity = Gravity.TOP
        val screen = column(
            text("Add from folder", look.titleSize, p.onSurface), space(h = 8f),
            row(icon(Glyph.FOLDER, p.onSurfaceVariant, 18f), space(w = 8f),
                text("Android/media/com.luncher.launcher, on this TV or a USB drive", 16f, p.onSurfaceVariant)),
            space(h = 28f), body,
        )
        screen.setPadding(px(48f), px(32f), px(48f), px(27f))
        root.addView(screen, FrameLayout.LayoutParams(-1, -1))
    }

    private fun Ui.deleteAllDialog(root: FrameLayout) {
        backgroundEditor(root)
        scrim(root)
        val buttons = row(
            button("Cancel", ButtonKind.SECONDARY, focused = true).apply { layoutParams = lp(0, -2, 1f) },
            space(w = 16f),
            button("Delete all", ButtonKind.DANGER, glyph = Glyph.TRASH).apply { layoutParams = lp(0, -2, 1f) },
        )
        val iconBox = FrameLayout(activity).apply {
            background = shape(p.errorContainer, 999f)
            addView(icon(Glyph.TRASH, p.onErrorContainer, 24f), FrameLayout.LayoutParams(px(24f), px(24f), Gravity.CENTER))
        }
        val d = column()
        d.addView(iconBox, lp(px(48f), px(48f)))
        d.addView(space(h = 20f))
        d.addView(text("Delete all pictures?", 24f, p.onSurface))
        d.addView(space(h = 12f))
        d.addView(text("One is used as the background: the home screen will have no picture. The 5 pictures are deleted from Luncher's pictures only.",
            16f, p.onSurfaceVariant, lines = 4))
        d.addView(space(h = 28f))
        d.addView(buttons, lp(match))
        d.setPadding(px(28f), px(28f), px(28f), px(28f))
        d.background = panelBackground()
        d.elevation = dp(16f)
        root.addView(d, FrameLayout.LayoutParams(px(440f), -2, Gravity.CENTER))
    }

    // --- Option A, the other screens ---

    private fun Ui.stepper(label: String, value: String, focused: Boolean, swatch: Int? = null): View {
        val e = entry(label, focused = focused, height = 52f)
        e.setPadding(px(56f), e.paddingTop, e.paddingRight, e.paddingBottom)
        val fg = contentColor(focused)
        val chip = row(icon(Glyph.CHEVRON_LEFT, fg, 20f), space(w = 6f))
        if (swatch != null) {
            chip.addView(View(activity).apply {
                background = shape(swatch, 999f, if (focused) withAlpha(fg, 0x40) else p.outlineVariant, 1f)
                layoutParams = lp(px(16f), px(16f))
            })
            chip.addView(space(w = 8f))
        }
        chip.addView(text(value, 16f, fg)); chip.addView(space(w = 6f)); chip.addView(icon(Glyph.CHEVRON, fg, 20f))
        chip.setPadding(px(8f), px(6f), px(8f), px(6f))
        chip.background = shape(if (focused) withAlpha(p.inverseOnSurface, 0x1F) else p.surfaceHighest, 999f)
        e.addView(chip)
        return e
    }

    private fun Ui.appearanceEditor(root: FrameLayout, accentName: String, mode: String, homeAccent: Int) {
        val preview = PreviewView(activity, dp(16f), c(0x33FFFFFF), homeAccent)
        val start = column(text("Appearance", look.titleSize, p.onSurface), space(h = 20f))
        start.addView(preview, lp(match, px(412f * 9f / 16f)))
        start.addView(space(h = 20f))
        start.addView(row(
            button("Background", ButtonKind.SECONDARY, glyph = Glyph.IMAGE).apply { layoutParams = lp(0, -2, 1f) },
            space(w = 16f),
            button("Reset to defaults", ButtonKind.DANGER, glyph = Glyph.RESET).apply { layoutParams = lp(0, -2, 1f) },
        ), lp(match))
        val groups = list(
            entry("Tiles", leading = Glyph.GRID, trailing = Glyph.CARET, height = 52f),
            entry("Time and date", leading = Glyph.CLOCK, trailing = Glyph.CARET, height = 52f),
            entry("Theme", leading = Glyph.PALETTE, trailing = Glyph.CARET_UP, height = 52f),
            stepper("Accent color", accentName, focused = true, swatch = p.primary),
            stepper("Mode", mode, focused = false),
            gap = 2f,
        )
        val end = column(groups)
        val body = row(start.apply { layoutParams = lp(0, -2, 1f) }, space(w = 40f), end.apply { layoutParams = lp(0, -2, 1f) })
        body.setPadding(px(48f), px(27f), px(48f), px(27f))
        root.addView(body, FrameLayout.LayoutParams(-1, -1))
    }

    private fun Ui.sideSheet(root: FrameLayout, vararg content: View): LinearLayout {
        val sheet = column(*content)
        sheet.setPadding(px(24f), px(32f), px(24f), px(24f))
        sheet.background = panelBackground()
        sheet.elevation = dp(16f)
        root.addView(sheet, FrameLayout.LayoutParams(px(360f), -1, Gravity.END).apply {
            topMargin = px(24f); bottomMargin = px(24f); rightMargin = px(24f)
        })
        return sheet
    }

    private fun Ui.hideApps(root: FrameLayout, fade: Boolean = false, bar: Boolean = false) {
        home(root, focusedTile = null)
        scrim(root)
        fun app(name: String, hidden: Boolean, focused: Boolean = false): View {
            val e = entry(name, focused = focused, height = 52f,
                titleColor = if (hidden) p.onSurfaceVariant else null, leading = if (hidden) Glyph.EYE_OFF else Glyph.TV)
            if (hidden) {
                val fg = if (focused) p.inverseOnSurface else p.onSurfaceVariant
                e.addView(space(w = 8f))
                e.addView(text("Hidden", 14f, fg, bold = true).apply {
                    setPadding(px(12f), px(4f), px(12f), px(4f))
                    background = shape(if (focused) withAlpha(p.inverseOnSurface, 0x1F) else p.surfaceHighest, 999f)
                })
            }
            return e
        }
        val apps = list(
            app("An app name far too long for its row", hidden = true, focused = true), app("Games", false), app("Movies", false),
            app("Music", hidden = true), app("News", false), app("Photos", false), app("Podcasts", false), app("Radio", false),
        ).apply {
            background = null
            // Room for the focused entry's zoom (1.05 of its width, half on each side), inside the
            // list: a parent that clips cuts each child to the child's own bounds.
            setPadding(px(10f), px(4f), px(10f), 0)
        }
        val clip = FrameLayout(activity).apply {
            tag = "clip"; background = shape(p.surface, look.entryCorner); clipToOutline = true

            addView(apps, FrameLayout.LayoutParams(-1, -2))
            if (fade) {
                // The last row fades into the panel: one gradient drawn over it, no layer.
                addView(View(activity).apply {
                    background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(withAlpha(p.surface, 0), p.surface))
                }, FrameLayout.LayoutParams(-1, px(56f), Gravity.BOTTOM))
            }
        }
        if (bar) {
            // A thin bar at the end edge: where the rows shown are among all of them.
            apps.setPadding(px(10f), px(4f), px(24f), 0)
            clip.addView(View(activity).apply { background = shape(p.surfaceHighest, 999f) },
                FrameLayout.LayoutParams(px(4f), -1, Gravity.END))
            clip.addView(View(activity).apply { background = shape(p.onSurfaceVariant, 999f) },
                FrameLayout.LayoutParams(px(4f), px(180f), Gravity.END))
        }
        sideSheet(root, text("Hide apps", 28f, p.onSurface), space(h = 8f),
            text("OK hides an app, or shows it again", 14f, p.onSurfaceVariant), space(h = 20f),
            clip.apply { layoutParams = lp(match, 0, 1f) })
    }

    private fun Ui.badge(glyph: Glyph, fill: Int, color: Int): View = FrameLayout(activity).apply {
        background = shape(fill, 999f)
        addView(icon(glyph, color, 24f), FrameLayout.LayoutParams(px(24f), px(24f), Gravity.CENTER))
        layoutParams = lp(px(48f), px(48f))
    }

    private fun Ui.dialog(root: FrameLayout, width: Float = 440f, vararg content: View): LinearLayout {
        val d = column(*content)
        d.setPadding(px(28f), px(28f), px(28f), px(28f))
        d.background = panelBackground()
        d.elevation = dp(16f)
        root.addView(d, FrameLayout.LayoutParams(px(width), -2, Gravity.CENTER))
        return d
    }

    private fun Ui.pictureAdded(root: FrameLayout) {
        home(root, focusedTile = null)
        scrim(root)
        dialog(root, 440f,
            badge(Glyph.CHECK, p.primaryContainer, p.onPrimaryContainer), space(h = 20f),
            text("Picture added", 24f, p.onSurface), space(h = 8f), text("holiday.jpg", 16f, p.onSurfaceVariant),
            space(h = 24f), sectionLabel("USE IT AS"), space(h = 8f),
            entry("Background", focused = true, leading = Glyph.IMAGE, trailing = Glyph.CHEVRON).apply { layoutParams = lp(match) },
        )
    }

    private fun Ui.picturesAdded(root: FrameLayout) {
        home(root, focusedTile = null)
        scrim(root)
        dialog(root, 440f,
            badge(Glyph.CHECK, p.primaryContainer, p.onPrimaryContainer), space(h = 20f),
            text("2 pictures added", 24f, p.onSurface), space(h = 16f),
            row(icon(Glyph.INFO, p.onSurfaceVariant, 20f), space(w = 12f), text("1 already in Luncher's pictures", 16f, p.onSurfaceVariant)),
            space(h = 10f),
            row(icon(Glyph.WARNING, p.error, 20f), space(w = 12f), text("1 not a picture, or damaged", 16f, p.onSurfaceVariant)),
            space(h = 28f), button("OK", ButtonKind.PRIMARY, focused = true).apply { layoutParams = lp(match) },
        )
    }

    private fun Ui.pictureActions(root: FrameLayout) {
        backgroundEditor(root)
        scrim(root)
        val menu = column(
            column(text("sunset.jpg", 16f, p.onSurface, bold = true), space(h = 4f), text("4032 × 3024 · 3.2 MB", 14f, p.onSurfaceVariant))
                .apply { setPadding(px(16f), px(4f), px(16f), px(12f)) },
            entry("Rename", focused = true, leading = Glyph.PENCIL, height = 52f).apply { layoutParams = lp(match) },
            space(h = 4f),
            entry("Delete", leading = Glyph.TRASH, height = 52f, titleColor = p.error).apply { layoutParams = lp(match) },
        )
        menu.setPadding(px(12f), px(16f), px(12f), px(12f))
        menu.background = panelBackground(20f)
        menu.elevation = dp(16f)
        root.addView(menu, FrameLayout.LayoutParams(px(280f), -2).apply { leftMargin = px(576f); topMargin = px(190f) })
    }

    private fun Ui.rename(root: FrameLayout) {
        backgroundEditor(root)
        scrim(root)
        val typed = TextView(activity).apply {
            text = "beach.png"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); setTextColor(p.onSurface); includeFontPadding = false
        }
        val field = column(text("Name", 13f, p.error, bold = true), space(h = 4f),
            row(typed, View(activity).apply { setBackgroundColor(p.primary); layoutParams = lp(px(2f), px(22f)) }))
        field.setPadding(px(16f), px(10f), px(16f), px(10f))
        field.background = shape(p.surfaceHigh, 12f, p.error, 2f)
        dialog(root, 460f,
            text("Rename picture", 24f, p.onSurface), space(h = 20f), field.apply { layoutParams = lp(match) }, space(h = 8f),
            row(icon(Glyph.WARNING, p.error, 16f), space(w = 8f), text("Name already used in Luncher's pictures", 14f, p.error)),
            space(h = 28f),
            row(button("Cancel", ButtonKind.SECONDARY).apply { layoutParams = lp(0, -2, 1f) }, space(w = 16f),
                button("Rename", ButtonKind.PRIMARY, glyph = Glyph.CHECK).apply { layoutParams = lp(0, -2, 1f) }),
        )
    }

    /** The list of an option's values, over its field, the value it has lined up with the field. */
    private fun Ui.optionList(root: FrameLayout) {
        val anchor = root.findViewWithTag<View>("blur")
        val at = IntArray(2); anchor.getLocationInWindow(at)
        val values = list(
            entry("Off", height = 48f), entry("Light", focused = true, trailing = Glyph.CHECK, height = 48f),
            entry("Medium", height = 48f), entry("Strong", height = 48f), gap = 2f,
        ).apply { background = null; setPadding(0, 0, 0, 0) }
        val popup = column(values)
        popup.setPadding(px(8f), px(8f), px(8f), px(8f))
        popup.background = panelBackground(16f).also { (it as GradientDrawable).setColor(p.surfaceHigh) }
        popup.elevation = dp(16f)
        val top = at[1] + (anchor.height - px(48f)) / 2 - px(8f) - px(48f) - px(2f)
        root.addView(popup, FrameLayout.LayoutParams(anchor.width + px(16f), -2).apply { leftMargin = at[0] - px(8f); topMargin = top })
    }

    private class Spinner(activity: Activity, private val color: Int, private val track: Int, private val stroke: Float) : View(activity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = stroke }
        private val r = RectF()
        override fun onDraw(canvas: Canvas) {
            r.set(stroke, stroke, width - stroke, height - stroke)
            paint.color = track; canvas.drawArc(r, 0f, 360f, false, paint)
            paint.color = color; canvas.drawArc(r, -90f, 250f, false, paint)
        }
    }

    private fun Ui.working(root: FrameLayout) {
        folder(root)
        scrim(root)
        val spinner = Spinner(activity, p.primary, p.surfaceHighest, dp(4f)).apply { layoutParams = lp(px(40f), px(40f)) }
        dialog(root, 400f,
            row(spinner, space(w = 20f), text("Adding the picture…", 18f, p.onSurface)), space(h = 28f),
            button("Cancel", ButtonKind.SECONDARY, focused = true).apply { layoutParams = lp(match) },
        )
    }

    private fun Ui.settingsArabic(root: FrameLayout) {
        home(root, focusedTile = null)
        scrim(root)
        val entries = list(
            entry("إخفاء التطبيقات", focused = true, leading = Glyph.EYE_OFF, trailing = Glyph.CHEVRON_LEFT),
            entry("المظهر", leading = Glyph.PALETTE, trailing = Glyph.CHEVRON_LEFT),
            entry("إعدادات النظام", leading = Glyph.TV, trailing = Glyph.OPEN),
        )
        val sheet = column(text("الإعدادات", 28f, p.onSurface), space(h = 24f), entries)
        sheet.layoutDirection = View.LAYOUT_DIRECTION_RTL
        sheet.setPadding(px(24f), px(32f), px(24f), px(24f))
        sheet.background = panelBackground()
        sheet.elevation = dp(16f)
        root.addView(sheet, FrameLayout.LayoutParams(px(360f), -1, Gravity.LEFT).apply {
            topMargin = px(24f); bottomMargin = px(24f); leftMargin = px(24f)
        })
    }

    private fun all(look: Look) {
        render("${look.id}_1_tile_menu", look) { tileMenu(it) }
        render("${look.id}_2_settings", look) { settingsPanel(it) }
        render("${look.id}_3_background", look) { backgroundEditor(it) }
        render("${look.id}_4_add_from_folder", look) { folder(it) }
        render("${look.id}_5_delete_all", look) { deleteAllDialog(it) }
    }

    @Test fun optionA() = all(lookA())
    @Test fun optionB() = all(lookB())
    @Test fun optionC() = all(lookC())
    @Test fun optionD() = all(lookD())

    // The same screens with another accent, and light, as the future themes would make them.
    @Test fun themes() {
        val looks = listOf(
            lookA(palette(BLUE, dark = true, tonal = true)), lookA(palette(ORANGE, dark = false, tonal = true)),
            lookB(palette(BLUE, dark = true, tonal = false)), lookB(palette(ORANGE, dark = false, tonal = false)),
            lookC(palette(BLUE, dark = true, tonal = false)), lookC(palette(ORANGE, dark = false, tonal = false)),
            lookD(palette(BLUE, dark = true, tonal = false, translucent = true)), lookD(palette(ORANGE, dark = false, tonal = false, translucent = true)),
        )
        for ((i, look) in looks.withIndex()) {
            val variant = if (i % 2 == 0) "blue" else "light"
            render("${look.id}_theme_${variant}_background", look) { backgroundEditor(it, focusSave = true) }
            render("${look.id}_theme_${variant}_settings", look) { settingsPanel(it) }
        }
    }

    private class Named(val name: String, val accent: Accent)

    private val accents = listOf(
        Named("Orange", ORANGE),
        Named("Pink", Accent(c(0xFFE91E63), c(0xFF3F0019), c(0xFF65002D), c(0xFF8E0042), c(0xFFB80F58), c(0xFFFFB1C5), c(0xFFFFD9E1))),
        Named("Purple", Accent(c(0xFF7E57C2), c(0xFF24005A), c(0xFF3B1A87), c(0xFF53379E), c(0xFF6B51B8), c(0xFFCFBDFF), c(0xFFE9DDFF))),
        Named("Blue", BLUE),
        Named("Teal", Accent(c(0xFF009688), c(0xFF00201C), c(0xFF003731), c(0xFF005048), c(0xFF006A60), c(0xFF53DBC9), c(0xFF74F8E5))),
        Named("Green", Accent(c(0xFF43A047), c(0xFF002106), c(0xFF00390F), c(0xFF00531A), c(0xFF006E25), c(0xFF78DC77), c(0xFF94F990))),
        Named("Amber", Accent(c(0xFFFFB300), c(0xFF281900), c(0xFF432C00), c(0xFF604100), c(0xFF7F5700), c(0xFFF8BD2A), c(0xFFFFDEA6))),
        Named("Gray", Accent(c(0xFF8E9199), c(0xFF191C20), c(0xFF2E3135), c(0xFF44474C), c(0xFF5C5F64), c(0xFFC4C6CC), c(0xFFE0E2E8))),
    )

    @Test fun optionAMore() {
        val a = lookA()
        render("A2_appearance_theme", a) { appearanceEditor(it, "Orange", "Dark", ORANGE.vivid) }
        render("A2_appearance_theme_light_blue", lookA(palette(BLUE, dark = false, tonal = true))) { appearanceEditor(it, "Blue", "Light", BLUE.vivid) }
        render("A2_hide_apps", a) { hideApps(it) }
        render("A2_picture_added", a) { pictureAdded(it) }
        render("A2_pictures_added", a) { picturesAdded(it) }
        render("A2_picture_actions", a) { pictureActions(it) }
        render("A2_rename", a) { rename(it) }
        render("A2_option_list", a, after = { optionList(it) }) { backgroundEditor(it, focusPicture = false) }
        render("A2_working", a) { working(it) }
        render("A2_settings_arabic", a) { settingsArabic(it) }
        for (n in accents) {
            render("A3_accent_${n.name}", lookA(palette(n.accent, dark = true, tonal = true))) { backgroundEditor(it, homeAccent = n.accent.vivid) }
            render("A3_accent_light_${n.name}", lookA(palette(n.accent, dark = false, tonal = true))) { backgroundEditor(it, homeAccent = n.accent.vivid) }
        }
    }

    // 720p at the 1080p density, 640 x 360 dp, and 1080p at 160 dpi, 1920 x 1080 dp: the same layout, adapted.
    @Test @Config(qualifiers = "+w640dp-h360dp")
    fun optionASmall() {
        render("A2_small_background", lookA(), after = {}) { backgroundEditor(it) }
        render("A2_small_settings", lookA()) { settingsPanel(it) }
    }

    @Test @Config(qualifiers = "+w720dp-h540dp")
    fun optionA4by3() {
        render("A2_4by3_background", lookA()) { backgroundEditor(it) }
        render("A2_4by3_folder", lookA()) { folder(it) }
    }

    // --- Where an option's values open, in the background editor ---

    /** A field as it shows while its values are open beside it or under it: outlined, the mark pointing there. */
    private fun Ui.openField(label: String, value: String, mark: Glyph): View {
        val f = row(
            column(text(label, 13f, p.primary, bold = true), space(h = 2f), text(value, 17f, p.onSurface)).apply { layoutParams = lp(0, -2, 1f) },
            icon(mark, p.primary, 22f),
        )
        f.minimumHeight = px(56f)
        f.setPadding(px(16f), px(8f), px(12f), px(8f))
        f.background = shape(p.surfaceHigh, 12f, p.primary, 2f)
        return f
    }

    private fun Ui.grid(blur: View): View = column(
        row(field("Fit", "Fill").apply { layoutParams = lp(0, -2, 1f) }, space(w = 12f),
            field("Position", "Center", disabled = true).apply { layoutParams = lp(0, -2, 1f) }),
        space(h = 12f),
        row(field("Dim", "Light").apply { layoutParams = lp(0, -2, 1f) }, space(w = 12f),
            blur.apply { layoutParams = lp(0, -2, 1f); tag = "blur" }),
    )

    private fun Ui.values(width: Float, selected: Int = 1): LinearLayout {
        val names = listOf("Off", "Light", "Medium", "Strong")
        val l = list(
            *names.mapIndexed { i, n -> entry(n, focused = i == selected, trailing = if (i == selected) Glyph.CHECK else null, height = 44f) }.toTypedArray(),
            gap = 2f,
        ).apply { background = null; setPadding(0, 0, 0, 0) }
        val popup = column(l)
        popup.setPadding(px(8f), px(8f), px(8f), px(8f))
        popup.background = shape(p.surfaceHigh, 16f)
        popup.elevation = dp(16f)
        popup.layoutParams = FrameLayout.LayoutParams(px(width), -2)
        return popup
    }

    /** 1: the list opens at the options' end edge, over the pictures, its value level with the field. */
    private fun Ui.beside(root: FrameLayout) {
        val anchor = root.findViewWithTag<View>("blur")
        val at = IntArray(2); anchor.getLocationInWindow(at)
        val popup = values(220f)
        val top = at[1] + (anchor.height - px(44f)) / 2 - px(8f) - px(44f) - px(2f)
        (popup.layoutParams as FrameLayout.LayoutParams).apply { leftMargin = at[0] + anchor.width + px(16f); topMargin = top }
        root.addView(popup)
        // A pointer from the field to the list.
        val pointer = View(activity).apply { background = shape(p.primary, 999f) }
        root.addView(pointer, FrameLayout.LayoutParams(px(12f), px(2f)).apply {
            leftMargin = at[0] + anchor.width + px(2f); topMargin = at[1] + anchor.height / 2 - px(1f)
        })
    }

    /** 2: every value shows, as segments; the arrows move along them, OK picks one. */
    private fun Ui.segments(): View {
        fun seg(label: String, values: List<String>, selected: Int, focused: Int = -1, disabled: Boolean = false): View {
            val a = if (disabled) 0x61 else 0xFF
            val group = row()
            group.background = shape(0, 999f, withAlpha(p.outline, a), 1f)
            group.setPadding(px(3f), px(3f), px(3f), px(3f))
            for ((i, v) in values.withIndex()) {
                val isFocused = i == focused
                val isSelected = i == selected
                val fg = when { isFocused -> p.inverseOnSurface; isSelected -> p.onPrimaryContainer; else -> p.onSurface }
                val cell = row()
                cell.gravity = Gravity.CENTER
                cell.minimumHeight = px(38f)
                cell.background = when {
                    isFocused -> shape(p.inverseSurface, 999f)
                    isSelected -> shape(withAlpha(p.primaryContainer, a), 999f)
                    else -> null
                }
                if (isSelected) { cell.addView(icon(Glyph.CHECK, withAlpha(fg, a), 16f)); cell.addView(space(w = 4f)) }
                cell.addView(text(v, 15f, withAlpha(fg, a), bold = isSelected || isFocused))
                if (isFocused) { cell.scaleX = 1.06f; cell.scaleY = 1.06f; cell.elevation = dp(4f) }
                group.addView(cell, lp(0, -2, 1f))
            }
            return row(text(label, 15f, withAlpha(p.onSurfaceVariant, a), bold = true).apply { layoutParams = lp(px(80f), -2) },
                group.apply { layoutParams = lp(0, -2, 1f) })
        }
        return column(
            seg("Fit", listOf("Fill", "Stretch"), 0), space(h = 10f),
            seg("Position", listOf("Center", "Left", "Right"), 0, disabled = true), space(h = 10f),
            seg("Dim", listOf("Off", "Light", "Medium", "Strong"), 1), space(h = 10f),
            seg("Blur", listOf("Off", "Light", "Medium", "Strong"), 1, focused = 2),
        )
    }

    /** 3: OK makes the field itself change: the arrows step its value, a bar shows how much; nothing opens. */
    private fun Ui.levels(level: Int, color: Int, track: Int): View = row().apply {
        for (i in 1..3) {
            addView(View(activity).apply { background = shape(if (i <= level) color else track, 999f) }, lp(px(14f), px(4f)))
            if (i < 3) addView(space(w = 3f))
        }
    }

    private fun Ui.stepGrid(): View {
        fun levelField(label: String, value: String, level: Int, editing: Boolean): View {
            val fg = if (editing) p.inverseOnSurface else p.onSurface
            val sub = if (editing) withAlpha(p.inverseOnSurface, 0xC0) else p.onSurfaceVariant
            val valueRow = if (editing) row(icon(Glyph.CHEVRON_LEFT, fg, 18f), space(w = 2f), text(value, 17f, fg, bold = true), space(w = 2f), icon(Glyph.CHEVRON, fg, 18f))
                else row(text(value, 17f, fg))
            val f = row(
                column(text(label, 13f, sub, bold = true), space(h = 2f), valueRow).apply { layoutParams = lp(0, -2, 1f) },
                levels(level, if (editing) p.inverseOnSurface else p.primary, if (editing) withAlpha(p.inverseOnSurface, 0x33) else p.surfaceHighest),
            )
            f.minimumHeight = px(56f)
            f.setPadding(px(16f), px(8f), px(14f), px(8f))
            f.background = if (editing) shape(p.inverseSurface, 12f) else shape(p.surfaceHigh, 12f)
            if (editing) { f.scaleX = 1.05f; f.scaleY = 1.05f; f.elevation = dp(6f) }
            return f
        }
        return column(
            row(field("Fit", "Fill").apply { layoutParams = lp(0, -2, 1f) }, space(w = 12f),
                field("Position", "Center", disabled = true).apply { layoutParams = lp(0, -2, 1f) }),
            space(h = 12f),
            row(levelField("Dim", "Light", 1, editing = false).apply { layoutParams = lp(0, -2, 1f) }, space(w = 12f),
                levelField("Blur", "Medium", 2, editing = true).apply { layoutParams = lp(0, -2, 1f) }),
        )
    }

    /** 4: the field grows into its list, in place; what's below it moves down, the column scrolls. */
    private fun Ui.expandGrid(): View {
        val open = column(openField("Blur", "Light", Glyph.CARET_UP), space(h = 6f), values(0f).apply {
            layoutParams = lp(match); elevation = 0f; background = shape(p.surfaceHigh, 12f)
        })
        val blurCell = column(open)
        return column(
            row(field("Fit", "Fill").apply { layoutParams = lp(0, -2, 1f) }, space(w = 12f),
                field("Position", "Center", disabled = true).apply { layoutParams = lp(0, -2, 1f) }),
            space(h = 12f),
            row(field("Dim", "Light").apply { layoutParams = lp(0, -2, 1f) }, space(w = 12f),
                blurCell.apply { layoutParams = lp(0, -2, 1f) }).apply { gravity = Gravity.TOP },
        )
    }

    @Test fun optionLists() {
        val a = lookA()
        render("L1_beside", a, after = { beside(it) }) {
            backgroundEditor(it, focusPicture = false, optionsOverride = { grid(openField("Blur", "Light", Glyph.CHEVRON)) })
        }
        render("L2_segments", a) {
            backgroundEditor(it, focusPicture = false, optionsOverride = { segments() }, optionsHeight = 4 * 44f + 30f)
        }
        render("L3_step", a) {
            backgroundEditor(it, focusPicture = false, optionsOverride = { stepGrid() }, labelHint = "◀ ▶ change · OK keep · Back undo")
        }
        render("L4_expand", a, after = {}) {
            backgroundEditor(it, focusPicture = false, optionsOverride = { expandGrid() }, startShift = 150f)
        }
        render("L0_now", a, after = { optionList(it) }) { backgroundEditor(it, focusPicture = false) }
    }

    /**
     * Beside, with the value [selected]: level with the field where the list fits, else moved up or
     * down only as far as the overscan margin asks; the pointer stays level with the field.
     */
    private fun Ui.besideAt(root: FrameLayout, selected: Int) {
        val anchor = root.findViewWithTag<View>("blur")
        val at = IntArray(2); anchor.getLocationInWindow(at)
        val popup = values(220f, selected)
        val listHeight = px(8f) * 2 + 4 * px(44f) + 3 * px(2f)
        val fieldCenter = at[1] + anchor.height / 2
        val level = fieldCenter - px(8f) - selected * (px(44f) + px(2f)) - px(22f)
        val top = level.coerceIn(px(27f), root.height - px(27f) - listHeight)
        (popup.layoutParams as FrameLayout.LayoutParams).apply { leftMargin = at[0] + anchor.width + px(16f); topMargin = top }
        root.addView(popup)
        root.addView(View(activity).apply { background = shape(p.primary, 999f) }, FrameLayout.LayoutParams(px(12f), px(2f)).apply {
            leftMargin = at[0] + anchor.width + px(2f); topMargin = fieldCenter - px(1f)
        })
    }

    @Test fun besideFits() {
        render("L1_strong", lookA(), after = { besideAt(it, 3) }) {
            backgroundEditor(it, focusPicture = false, optionsOverride = { grid(openField("Blur", "Strong", Glyph.CHEVRON)) })
        }
        render("L1_off", lookA(), after = { besideAt(it, 0) }) {
            backgroundEditor(it, focusPicture = false, optionsOverride = { grid(openField("Blur", "Off", Glyph.CHEVRON)) })
        }
    }

    @Test @Config(qualifiers = "+w640dp-h360dp")
    fun besideOnASmallScreen() {
        render("L1_small_off", lookA(), after = { besideAt(it, 0) }) {
            backgroundEditor(it, focusPicture = false, optionsOverride = { compactGrid() })
        }
    }

    private fun Ui.compactGrid(): View = column(
        row(field("Fit", "Fill", height = 48f).apply { layoutParams = lp(0, -2, 1f) }, space(w = 12f),
            field("Position", "Center", disabled = true, height = 48f).apply { layoutParams = lp(0, -2, 1f) }),
        space(h = 12f),
        row(field("Dim", "Light", height = 48f).apply { layoutParams = lp(0, -2, 1f) }, space(w = 12f),
            openField("Blur", "Off", Glyph.CHEVRON).apply { minimumHeight = px(48f); layoutParams = lp(0, -2, 1f); tag = "blur" }),
    )

    // --- A dialog's mark next to its title ---

    private enum class Mark { ABOVE, BESIDE_BADGE, BESIDE_ICON }

    private fun Ui.header(mark: Mark, glyph: Glyph, fill: Int, color: Int, title: String, sub: String?): View {
        val titleText = text(title, 24f, p.onSurface)
        val head: View = when (mark) {
            Mark.ABOVE -> column(badge(glyph, fill, color), space(h = 20f), titleText)
            Mark.BESIDE_BADGE -> row(
                FrameLayout(activity).apply {
                    background = shape(fill, 999f)
                    addView(icon(glyph, color, 22f), FrameLayout.LayoutParams(px(22f), px(22f), Gravity.CENTER))
                    layoutParams = lp(px(40f), px(40f))
                },
                space(w = 16f), titleText,
            )
            Mark.BESIDE_ICON -> row(icon(glyph, if (fill == p.errorContainer) p.error else p.primary, 28f), space(w = 14f), titleText)
        }
        if (sub == null) return head
        val indent = when (mark) { Mark.ABOVE, Mark.BESIDE_BADGE -> 0f; Mark.BESIDE_ICON -> 42f }
        return column(head, space(h = 6f), text(sub, 16f, p.onSurfaceVariant).apply { setPadding(px(indent), 0, 0, 0) })
    }

    private fun Ui.addedWith(root: FrameLayout, mark: Mark) {
        home(root, focusedTile = null)
        scrim(root)
        dialog(root, 440f,
            header(mark, Glyph.CHECK, p.primaryContainer, p.onPrimaryContainer, "Picture added", "holiday.jpg"),
            space(h = 24f), sectionLabel("USE IT AS"), space(h = 8f),
            entry("Background", focused = true, leading = Glyph.IMAGE, trailing = Glyph.CHEVRON).apply { layoutParams = lp(match) },
        )
    }

    private fun Ui.deleteWith(root: FrameLayout, mark: Mark) {
        backgroundEditor(root)
        scrim(root)
        dialog(root, 440f,
            header(mark, Glyph.TRASH, p.errorContainer, p.onErrorContainer, "Delete all pictures?", null),
            space(h = 12f),
            text("One is used as the background: the home screen will have no picture. The 5 pictures are deleted from Luncher's pictures only.",
                16f, p.onSurfaceVariant, lines = 4),
            space(h = 28f),
            row(button("Cancel", ButtonKind.SECONDARY, focused = true).apply { layoutParams = lp(0, -2, 1f) }, space(w = 16f),
                button("Delete all", ButtonKind.DANGER, glyph = Glyph.TRASH).apply { layoutParams = lp(0, -2, 1f) }),
        )
    }

    @Test fun dialogMarks() {
        for (m in Mark.entries) {
            render("M_added_${m.name.lowercase()}", lookA()) { addedWith(it, m) }
            render("M_delete_${m.name.lowercase()}", lookA()) { deleteWith(it, m) }
        }
    }

    // --- The window after a share: a message and OK, the mark beside the title ---

    private fun Ui.shareResult(root: FrameLayout, glyph: Glyph, fill: Int, color: Int, title: String, vararg body: View) {
        home(root, focusedTile = null)
        scrim(root)
        val content = mutableListOf<View>(header(Mark.BESIDE_BADGE, glyph, fill, color, title, null), space(h = 12f))
        content.addAll(body)
        content.add(space(h = 28f))
        content.add(button("OK", ButtonKind.PRIMARY, focused = true).apply { layoutParams = lp(match) })
        dialog(root, 440f, *content.toTypedArray())
    }

    private fun Ui.line(glyph: Glyph, color: Int, s: String) =
        row(icon(glyph, color, 20f), space(w = 12f), text(s, 16f, p.onSurfaceVariant))

    @Test fun shareResults() {
        val a = lookA()
        render("S_added", a) {
            shareResult(it, Glyph.CHECK, p.primaryContainer, p.onPrimaryContainer, "Picture added",
                text("holiday.jpg", 16f, p.onSurface, bold = true))
        }
        render("S_several", a) {
            shareResult(it, Glyph.CHECK, p.primaryContainer, p.onPrimaryContainer, "2 pictures added",
                line(Glyph.INFO, p.onSurfaceVariant, "1 already in Luncher's pictures"), space(h = 10f),
                line(Glyph.WARNING, p.error, "1 not a picture, or damaged"))
        }
        render("S_already", a) {
            shareResult(it, Glyph.INFO, p.surfaceHighest, p.onSurface, "Already in Luncher's pictures",
                text("holiday.jpg", 16f, p.onSurface, bold = true))
        }
        render("S_not_added", a) {
            shareResult(it, Glyph.WARNING, p.errorContainer, p.onErrorContainer, "Picture not added",
                text("IMG_0001.HEIC", 16f, p.onSurface, bold = true), space(h = 8f),
                text("This TV can't show HEIF (HEIC) pictures. Save it as JPEG or PNG, then add it again.", 16f, p.onSurfaceVariant, lines = 3))
        }
        render("M_delete_beside_badge", a) { deleteWith(it, Mark.BESIDE_BADGE) }
    }

    @Test fun hideAppsEnds() {
        render("H_half", lookA()) { hideApps(it) }
        render("H_fade", lookA()) { hideApps(it, fade = true) }
        render("H_fade_bar", lookA()) { hideApps(it, fade = true, bar = true) }
    }
}
