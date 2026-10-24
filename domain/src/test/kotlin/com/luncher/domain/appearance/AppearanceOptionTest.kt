package com.luncher.domain.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.luncher.domain.options.step
import com.luncher.domain.options.valueOrDefault
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureCut
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PicturePosition

class AppearanceOptionTest {

    private val options = appearanceOptions().all

    @Test
    fun `each option sets and gets each of its values, and changes nothing else`() {
        for (option in options) checkSetAndGet(option)
    }

    private fun <T> checkSetAndGet(option: AppearanceOption<T>) {
        for (value in option.values) {
            val changed = option.set(Appearance(), value)
            assertEquals("$option", value, option.get(changed))
            for (other in options - option) assertEquals("$other after $option = $value", other.get(Appearance()), other.get(changed))
        }
    }

    @Test
    fun `the options read and change their part of the appearance`() {
        val appearance = Appearance(
            TileGeometry(
                verticalAlignment = VerticalAlignment.BOTTOM,
                horizontalAlignment = HorizontalAlignment.RIGHT,
                fullBottomRow = true,
            ),
            TileStyle(Corners.Radius(8)),
            TopBarAppearance(clockSide = Side.RIGHT, timeSide = Side.RIGHT, showTime = true, showDate = false),
        )

        assertEquals(VerticalAlignment.BOTTOM, AppearanceOption.VerticalAlignment.get(appearance))
        assertEquals(HorizontalAlignment.RIGHT, AppearanceOption.HorizontalAlignment.get(appearance))
        assertEquals(true, AppearanceOption.FullBottomRow.get(appearance))
        assertEquals(Corners.Radius(8), AppearanceOption.CornerRadius.get(appearance))
        assertEquals(Side.RIGHT, AppearanceOption.ClockSide.get(appearance))
        assertEquals(Side.RIGHT, AppearanceOption.TimeSide.get(appearance))
        assertEquals(true, AppearanceOption.ShowTime.get(appearance))
        assertEquals(false, AppearanceOption.ShowDate.get(appearance))
        assertEquals(TileStyle(Corners.Radius(12)), AppearanceOption.CornerRadius.set(appearance, Corners.Radius(12)).tileStyle)
        assertEquals(
            TileGeometry(verticalAlignment = VerticalAlignment.TOP, horizontalAlignment = HorizontalAlignment.RIGHT, fullBottomRow = true),
            AppearanceOption.VerticalAlignment.set(appearance, VerticalAlignment.TOP).tileGeometry,
        )
        assertEquals(
            TileGeometry(verticalAlignment = VerticalAlignment.BOTTOM, horizontalAlignment = HorizontalAlignment.CENTER, fullBottomRow = true),
            AppearanceOption.HorizontalAlignment.set(appearance, HorizontalAlignment.CENTER).tileGeometry,
        )
        assertEquals(
            TopBarAppearance(clockSide = Side.LEFT, timeSide = Side.RIGHT, showTime = true, showDate = false),
            AppearanceOption.ClockSide.set(appearance, Side.LEFT).topBar,
        )
        assertEquals(
            TopBarAppearance(clockSide = Side.RIGHT, timeSide = Side.RIGHT, showTime = false, showDate = false),
            AppearanceOption.ShowTime.set(appearance, false).topBar,
        )
    }

    // The defaults are the home screen as it looks without settings.
    @Test
    fun `each option's default is one of its values, and today's look`() {
        for (option in options) assertTrue("$option", option.default in option.values)
        assertEquals(VerticalAlignment.BOTTOM, AppearanceOption.VerticalAlignment.default)
        assertEquals(HorizontalAlignment.CENTER, AppearanceOption.HorizontalAlignment.default)
        assertEquals(true, AppearanceOption.FullBottomRow.default)
        assertEquals(Corners.Radius(16), AppearanceOption.CornerRadius.default)
        assertEquals(true, AppearanceOption.FocusFrame.default)
        assertEquals(AppName.OFF, AppearanceOption.AppName.default)
        assertEquals(Side.LEFT, AppearanceOption.ClockSide.default)
        assertEquals(Side.LEFT, AppearanceOption.TimeSide.default)
        assertEquals(true, AppearanceOption.ShowTime.default)
        assertEquals(true, AppearanceOption.ShowDate.default)
        assertEquals(Fit.FILL, AppearanceOption.BackgroundFit.default)
        assertEquals(PicturePosition.CENTER, AppearanceOption.BackgroundPosition.default)
        assertEquals(0, AppearanceOption.BackgroundDim.default)
        assertEquals(Blur.OFF, AppearanceOption.BackgroundBlur.default)
        assertEquals("no wallpaper", null, Appearance().background.picture)
    }

    @Test
    fun `each option has two values at least, each once`() {
        for (option in options) {
            assertTrue("$option", option.values.size >= 2)
            assertEquals("$option", option.values.distinct(), option.values)
        }
    }

    @Test
    fun `the corner radius steps from square to round ends`() {
        assertEquals(
            listOf(0, 4, 8, 12, 16, 24, 32).map { Corners.Radius(it) } + Corners.Pill,
            AppearanceOption.CornerRadius.values,
        )
    }

    // Off and On, as Full bottom row; a name below is a new look, never the default.
    @Test
    fun `the focus frame steps as an on-off option, the app name from today's look to the new ones`() {
        assertEquals(listOf(false, true), AppearanceOption.FocusFrame.values)
        assertEquals(listOf(AppName.OFF, AppName.WHEN_FOCUSED, AppName.ALWAYS), AppearanceOption.AppName.values)
    }

    // The focus frame only redraws the tiles; the name needs room between the rows, a new layout.
    @Test
    fun `the focus frame changes the tiles' style, the app name their geometry`() {
        val lifted = AppearanceOption.FocusFrame.set(Appearance(), false)
        val named = AppearanceOption.AppName.set(Appearance(), AppName.WHEN_FOCUSED)

        assertEquals(TileStyle(focusFrame = false), lifted.tileStyle)
        assertEquals(TileGeometry(), lifted.tileGeometry)
        assertEquals(TileGeometry(appName = AppName.WHEN_FOCUSED), named.tileGeometry)
        assertEquals(TileStyle(), named.tileStyle)
    }

    @Test
    fun `steps to the next and the previous value`() {
        val radius = AppearanceOption.CornerRadius

        val next = radius.step(Appearance(), forward = true, AppearanceContext.UNKNOWN)
        assertEquals(Corners.Radius(24), radius.get(next))
        assertEquals(Corners.Radius(16), radius.get(radius.step(next, forward = false, AppearanceContext.UNKNOWN)))
    }

    @Test
    fun `wraps around at the first and the last value`() {
        val radius = AppearanceOption.CornerRadius
        val first = radius.set(Appearance(), Corners.Radius(0))
        val last = radius.set(first, Corners.Pill)

        assertEquals(Corners.Pill, radius.get(radius.step(first, forward = false, AppearanceContext.UNKNOWN)))
        assertEquals(Corners.Radius(0), radius.get(radius.step(last, forward = true, AppearanceContext.UNKNOWN)))
    }

    @Test
    fun `steps through every value in order, then back to the first`() {
        val vertical = AppearanceOption.VerticalAlignment
        val seen = generateSequence(Appearance()) { vertical.step(it, forward = true, AppearanceContext.UNKNOWN) }
            .take(VerticalAlignment.entries.size + 1)
            .map(vertical::get)
            .toList()

        assertEquals(listOf(VerticalAlignment.BOTTOM, VerticalAlignment.TOP, VerticalAlignment.CENTER, VerticalAlignment.BOTTOM), seen)
    }

    @Test
    fun `the horizontal alignment steps from left through center to right`() {
        assertEquals(
            listOf(HorizontalAlignment.LEFT, HorizontalAlignment.CENTER, HorizontalAlignment.RIGHT),
            AppearanceOption.HorizontalAlignment.values,
        )
    }

    @Test
    fun `an option of two values toggles either way`() {
        val clock = AppearanceOption.ClockSide

        assertEquals(Side.RIGHT, clock.get(clock.step(Appearance(), forward = true, AppearanceContext.UNKNOWN)))
        assertEquals(Side.RIGHT, clock.get(clock.step(Appearance(), forward = false, AppearanceContext.UNKNOWN)))
    }

    // An appearance made in code with a value the option doesn't offer: the next step goes back to the default.
    @Test
    fun `steps from a value it doesn't offer to the default`() {
        val odd = Appearance(tileStyle = TileStyle(Corners.Radius(5)))

        assertEquals(Corners.Radius(16), AppearanceOption.CornerRadius.get(AppearanceOption.CornerRadius.step(odd, forward = true, AppearanceContext.UNKNOWN)))
    }

    @Test
    fun `a value it doesn't offer falls back to its default`() {
        assertEquals(Corners.Radius(16), AppearanceOption.CornerRadius.valueOrDefault(Corners.Radius(5)))
        assertEquals(Corners.Radius(16), AppearanceOption.CornerRadius.valueOrDefault(null))
        assertEquals(Corners.Radius(24), AppearanceOption.CornerRadius.valueOrDefault(Corners.Radius(24)))
        assertEquals(VerticalAlignment.BOTTOM, AppearanceOption.VerticalAlignment.valueOrDefault(null))
        assertEquals(Side.RIGHT, AppearanceOption.ClockSide.valueOrDefault(Side.RIGHT))
    }

    @Test
    fun `a full bottom row applies only with the rows centered or at the bottom`() {
        val full = AppearanceOption.FullBottomRow
        fun at(vertical: VerticalAlignment, horizontal: HorizontalAlignment) =
            Appearance(TileGeometry(verticalAlignment = vertical, horizontalAlignment = horizontal))

        for (horizontal in HorizontalAlignment.entries) {
            assertFalse(full.appliesTo(at(VerticalAlignment.TOP, horizontal), AppearanceContext.UNKNOWN))
            assertTrue(full.appliesTo(at(VerticalAlignment.CENTER, horizontal), AppearanceContext.UNKNOWN))
            assertTrue(full.appliesTo(at(VerticalAlignment.BOTTOM, horizontal), AppearanceContext.UNKNOWN))
        }
    }

    @Test
    fun `the clock's side applies while the time or the date shows`() {
        val side = AppearanceOption.ClockSide
        fun showing(time: Boolean, date: Boolean) = Appearance(topBar = TopBarAppearance(showTime = time, showDate = date))

        assertTrue(side.appliesTo(showing(time = true, date = true), AppearanceContext.UNKNOWN))
        assertTrue(side.appliesTo(showing(time = true, date = false), AppearanceContext.UNKNOWN))
        assertTrue(side.appliesTo(showing(time = false, date = true), AppearanceContext.UNKNOWN))
        assertFalse(side.appliesTo(showing(time = false, date = false), AppearanceContext.UNKNOWN))
    }

    @Test
    fun `the time's side of the date applies only while both show`() {
        val side = AppearanceOption.TimeSide
        fun showing(time: Boolean, date: Boolean) = Appearance(topBar = TopBarAppearance(showTime = time, showDate = date))

        assertTrue(side.appliesTo(showing(time = true, date = true), AppearanceContext.UNKNOWN))
        assertFalse(side.appliesTo(showing(time = true, date = false), AppearanceContext.UNKNOWN))
        assertFalse(side.appliesTo(showing(time = false, date = true), AppearanceContext.UNKNOWN))
        assertFalse(side.appliesTo(showing(time = false, date = false), AppearanceContext.UNKNOWN))
    }

    @Test
    fun `the background's options apply only while it has a picture, its position only while it fills`() {
        val picture = Background(picture = PictureId("sunset"))
        val background = listOf(AppearanceOption.BackgroundFit, AppearanceOption.BackgroundPosition, AppearanceOption.BackgroundDim, AppearanceOption.BackgroundBlur)

        for (option in background) {
            assertFalse("$option", option.appliesTo(Appearance(), AppearanceContext.UNKNOWN))
            assertTrue("$option", option.appliesTo(Appearance(background = picture), AppearanceContext.UNKNOWN))
        }
        val stretched = Appearance(background = picture.copy(framing = Framing(Fit.STRETCH)))
        assertFalse(AppearanceOption.BackgroundPosition.appliesTo(stretched, AppearanceContext.UNKNOWN))
        assertTrue(AppearanceOption.BackgroundDim.appliesTo(stretched, AppearanceContext.UNKNOWN))
    }

    private val wide = AppearanceContext(backgroundCut = PictureCut.SIDES)
    private val tall = AppearanceContext(backgroundCut = PictureCut.TOP_AND_BOTTOM)
    private val sameShape = AppearanceContext(backgroundCut = PictureCut.NONE)

    @Test
    fun `the position offers the middle and the ends of the side the picture is cut at`() {
        val position = AppearanceOption.BackgroundPosition

        assertEquals(listOf(PicturePosition.CENTER, PicturePosition.LEFT, PicturePosition.RIGHT), position.values(wide))
        assertEquals(listOf(PicturePosition.CENTER, PicturePosition.TOP, PicturePosition.BOTTOM), position.values(tall))
        assertEquals(listOf(PicturePosition.CENTER), position.values(sameShape))
        assertEquals("unknown: all of them", PicturePosition.entries, position.values(AppearanceContext.UNKNOWN))
    }

    // What's stored doesn't depend on the picture: a position stays readable whatever is chosen later.
    @Test
    fun `the position keeps every value for storage, whatever the context`() {
        val position = AppearanceOption.BackgroundPosition

        assertEquals(PicturePosition.entries, position.values)
        for (value in PicturePosition.entries) assertEquals(value, position.valueOrDefault(value))
    }

    @Test
    fun `fit and position don't apply to a picture of the screen's shape, dim and blur do`() {
        val appearance = Appearance(background = Background(picture = PictureId("sunset")))

        for (context in listOf(wide, tall, AppearanceContext.UNKNOWN)) {
            assertTrue("$context", AppearanceOption.BackgroundFit.appliesTo(appearance, context))
            assertTrue("$context", AppearanceOption.BackgroundPosition.appliesTo(appearance, context))
        }
        assertFalse(AppearanceOption.BackgroundFit.appliesTo(appearance, sameShape))
        assertFalse(AppearanceOption.BackgroundPosition.appliesTo(appearance, sameShape))
        assertTrue(AppearanceOption.BackgroundDim.appliesTo(appearance, sameShape))
        assertTrue(AppearanceOption.BackgroundBlur.appliesTo(appearance, sameShape))
        val stretched = Appearance(background = Background(picture = PictureId("sunset"), framing = Framing(Fit.STRETCH)))
        assertFalse("stretched", AppearanceOption.BackgroundPosition.appliesTo(stretched, wide))
        assertFalse("no picture", AppearanceOption.BackgroundFit.appliesTo(Appearance(), wide))
    }

    @Test
    fun `every option offers its default in every context, and only the position narrows its values`() {
        val contexts = listOf(AppearanceContext.UNKNOWN, wide, tall, sameShape)
        for (option in options) {
            for (context in contexts) {
                assertTrue("$option in $context", option.default in option.values(context))
                assertTrue("$option in $context", option.values.containsAll(option.values(context)))
                if (option != AppearanceOption.BackgroundPosition) assertEquals("$option in $context", option.values, option.values(context))
            }
        }
    }

    @Test
    fun `the context decides nothing for the options it doesn't concern`() {
        val appearances = listOf(
            Appearance(),
            Appearance(TileGeometry(verticalAlignment = VerticalAlignment.TOP), topBar = TopBarAppearance(showTime = false, showDate = false)),
        )
        val background = setOf(AppearanceOption.BackgroundFit, AppearanceOption.BackgroundPosition)
        for (option in options - background) {
            for (appearance in appearances) {
                for (context in listOf(wide, tall, sameShape)) assertEquals("$option", option.appliesTo(appearance, AppearanceContext.UNKNOWN), option.appliesTo(appearance, context))
            }
        }
    }

    @Test
    fun `the position steps around the values offered, either way`() {
        val position = AppearanceOption.BackgroundPosition
        val start = Appearance(background = Background(picture = PictureId("sunset")))
        fun walk(context: AppearanceContext, forward: Boolean) =
            generateSequence(start) { position.step(it, forward, context) }.take(4).map(position::get).toList()

        assertEquals(listOf(PicturePosition.CENTER, PicturePosition.LEFT, PicturePosition.RIGHT, PicturePosition.CENTER), walk(wide, forward = true))
        assertEquals(listOf(PicturePosition.CENTER, PicturePosition.RIGHT, PicturePosition.LEFT, PicturePosition.CENTER), walk(wide, forward = false))
        assertEquals(listOf(PicturePosition.CENTER, PicturePosition.TOP, PicturePosition.BOTTOM, PicturePosition.CENTER), walk(tall, forward = true))
        assertEquals(listOf(PicturePosition.CENTER, PicturePosition.BOTTOM, PicturePosition.TOP, PicturePosition.CENTER), walk(tall, forward = false))
    }

    @Test
    fun `the position steps from a value not offered to the middle`() {
        val position = AppearanceOption.BackgroundPosition
        val top = Appearance(background = Background(picture = PictureId("sunset"), framing = Framing(position = PicturePosition.TOP)))

        assertEquals(PicturePosition.CENTER, position.get(position.step(top, forward = true, wide)))
        assertEquals(PicturePosition.CENTER, position.get(position.step(top, forward = false, wide)))
        assertEquals("unknown: all of them", PicturePosition.BOTTOM, position.get(position.step(top, forward = true, AppearanceContext.UNKNOWN)))
    }

    @Test
    fun `the background's options change its part, keeping the picture`() {
        val appearance = Appearance(background = Background(picture = PictureId("sunset")))

        assertEquals(
            Background(PictureId("sunset"), Framing(Fit.STRETCH, PicturePosition.TOP), 40, Blur.STRONG),
            listOf(
                { a: Appearance -> AppearanceOption.BackgroundFit.set(a, Fit.STRETCH) },
                { a: Appearance -> AppearanceOption.BackgroundPosition.set(a, PicturePosition.TOP) },
                { a: Appearance -> AppearanceOption.BackgroundDim.set(a, 40) },
                { a: Appearance -> AppearanceOption.BackgroundBlur.set(a, Blur.STRONG) },
            ).fold(appearance) { a, step -> step(a) }.background,
        )
    }

    @Test
    fun `dim steps by a fifth, up to three fifths`() {
        assertEquals(listOf(0, 20, 40, 60), AppearanceOption.BackgroundDim.values)
    }

    @Test
    fun `blur steps from off through light and medium to strong, in equal steps`() {
        assertEquals(listOf(Blur.OFF, Blur.LIGHT, Blur.MEDIUM, Blur.STRONG), AppearanceOption.BackgroundBlur.values)
        val steps = AppearanceOption.BackgroundBlur.values.zipWithNext { a, b -> b.spread - a.spread }
        for (step in steps) assertEquals(steps.first(), step, 1e-9)
        assertTrue(steps.first() > 0)
    }

    @Test
    fun `every option but the background's applies to the defaults, and the others always`() {
        val background = setOf(AppearanceOption.BackgroundFit, AppearanceOption.BackgroundPosition, AppearanceOption.BackgroundDim, AppearanceOption.BackgroundBlur)
        for (option in options - background) assertTrue("$option", option.appliesTo(Appearance(), AppearanceContext.UNKNOWN))
        val conditional = setOf(AppearanceOption.FullBottomRow, AppearanceOption.ClockSide, AppearanceOption.TimeSide) + background
        val nothingShown = Appearance(
            TileGeometry(verticalAlignment = VerticalAlignment.TOP),
            topBar = TopBarAppearance(showTime = false, showDate = false),
        )
        for (option in options - conditional) assertTrue("$option", option.appliesTo(nothingShown, AppearanceContext.UNKNOWN))
    }
}
