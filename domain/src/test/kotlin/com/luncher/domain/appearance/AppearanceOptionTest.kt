package com.luncher.domain.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
            TileGeometry(placement = TilePlacement.BOTTOM_RIGHT, fullBottomRow = true),
            TileStyle(Corners.Radius(8)),
            TopBarAppearance(clockSide = Side.RIGHT, timeSide = Side.RIGHT, showTime = true, showDate = false),
        )

        assertEquals(TilePlacement.BOTTOM_RIGHT, AppearanceOption.Placement.get(appearance))
        assertEquals(true, AppearanceOption.FullBottomRow.get(appearance))
        assertEquals(Corners.Radius(8), AppearanceOption.CornerRadius.get(appearance))
        assertEquals(Side.RIGHT, AppearanceOption.ClockSide.get(appearance))
        assertEquals(Side.RIGHT, AppearanceOption.TimeSide.get(appearance))
        assertEquals(true, AppearanceOption.ShowTime.get(appearance))
        assertEquals(false, AppearanceOption.ShowDate.get(appearance))
        assertEquals(TileStyle(Corners.Radius(12)), AppearanceOption.CornerRadius.set(appearance, Corners.Radius(12)).tileStyle)
        assertEquals(TileGeometry(placement = TilePlacement.TOP_LEFT, fullBottomRow = true), AppearanceOption.Placement.set(appearance, TilePlacement.TOP_LEFT).tileGeometry)
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
        assertEquals(TilePlacement.TOP_LEFT, AppearanceOption.Placement.default)
        assertEquals(false, AppearanceOption.FullBottomRow.default)
        assertEquals(Corners.Radius(8), AppearanceOption.CornerRadius.default)
        assertEquals(Side.LEFT, AppearanceOption.ClockSide.default)
        assertEquals(Side.LEFT, AppearanceOption.TimeSide.default)
        assertEquals(true, AppearanceOption.ShowTime.default)
        assertEquals(true, AppearanceOption.ShowDate.default)
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

    @Test
    fun `steps to the next and the previous value`() {
        val radius = AppearanceOption.CornerRadius

        val next = radius.step(Appearance(), forward = true)
        assertEquals(Corners.Radius(12), radius.get(next))
        assertEquals(Corners.Radius(8), radius.get(radius.step(next, forward = false)))
    }

    @Test
    fun `wraps around at the first and the last value`() {
        val radius = AppearanceOption.CornerRadius
        val first = radius.set(Appearance(), Corners.Radius(0))
        val last = radius.set(first, Corners.Pill)

        assertEquals(Corners.Pill, radius.get(radius.step(first, forward = false)))
        assertEquals(Corners.Radius(0), radius.get(radius.step(last, forward = true)))
    }

    @Test
    fun `steps through every value in order, then back to the first`() {
        val placement = AppearanceOption.Placement
        val seen = generateSequence(Appearance()) { placement.step(it, forward = true) }
            .take(TilePlacement.entries.size + 1)
            .map(placement::get)
            .toList()

        assertEquals(TilePlacement.entries + TilePlacement.TOP_LEFT, seen)
    }

    @Test
    fun `an option of two values toggles either way`() {
        val clock = AppearanceOption.ClockSide

        assertEquals(Side.RIGHT, clock.get(clock.step(Appearance(), forward = true)))
        assertEquals(Side.RIGHT, clock.get(clock.step(Appearance(), forward = false)))
    }

    // An appearance made in code with a value the option doesn't offer: the next step goes back to the default.
    @Test
    fun `steps from a value it doesn't offer to the default`() {
        val odd = Appearance(tileStyle = TileStyle(Corners.Radius(5)))

        assertEquals(Corners.Radius(8), AppearanceOption.CornerRadius.get(AppearanceOption.CornerRadius.step(odd, forward = true)))
    }

    @Test
    fun `a value it doesn't offer falls back to its default`() {
        assertEquals(Corners.Radius(8), AppearanceOption.CornerRadius.valueOrDefault(Corners.Radius(5)))
        assertEquals(Corners.Radius(8), AppearanceOption.CornerRadius.valueOrDefault(null))
        assertEquals(Corners.Radius(12), AppearanceOption.CornerRadius.valueOrDefault(Corners.Radius(12)))
        assertEquals(TilePlacement.TOP_LEFT, AppearanceOption.Placement.valueOrDefault(null))
        assertEquals(Side.RIGHT, AppearanceOption.ClockSide.valueOrDefault(Side.RIGHT))
    }

    @Test
    fun `a full bottom row applies only with the tiles at the bottom`() {
        val full = AppearanceOption.FullBottomRow
        fun at(placement: TilePlacement) = Appearance(TileGeometry(placement = placement))

        assertFalse(full.appliesTo(at(TilePlacement.TOP_LEFT)))
        assertFalse(full.appliesTo(at(TilePlacement.TOP_RIGHT)))
        assertTrue(full.appliesTo(at(TilePlacement.BOTTOM_LEFT)))
        assertTrue(full.appliesTo(at(TilePlacement.BOTTOM_RIGHT)))
    }

    @Test
    fun `the clock's side applies while the time or the date shows`() {
        val side = AppearanceOption.ClockSide
        fun showing(time: Boolean, date: Boolean) = Appearance(topBar = TopBarAppearance(showTime = time, showDate = date))

        assertTrue(side.appliesTo(showing(time = true, date = true)))
        assertTrue(side.appliesTo(showing(time = true, date = false)))
        assertTrue(side.appliesTo(showing(time = false, date = true)))
        assertFalse(side.appliesTo(showing(time = false, date = false)))
    }

    @Test
    fun `the time's side of the date applies only while both show`() {
        val side = AppearanceOption.TimeSide
        fun showing(time: Boolean, date: Boolean) = Appearance(topBar = TopBarAppearance(showTime = time, showDate = date))

        assertTrue(side.appliesTo(showing(time = true, date = true)))
        assertFalse(side.appliesTo(showing(time = true, date = false)))
        assertFalse(side.appliesTo(showing(time = false, date = true)))
        assertFalse(side.appliesTo(showing(time = false, date = false)))
    }

    @Test
    fun `every option applies to the defaults, and the others always`() {
        for (option in options) assertTrue("$option", option.appliesTo(Appearance()) || option == AppearanceOption.FullBottomRow)
        val conditional = setOf(AppearanceOption.FullBottomRow, AppearanceOption.ClockSide, AppearanceOption.TimeSide)
        val nothingShown = Appearance(
            TileGeometry(placement = TilePlacement.TOP_LEFT),
            topBar = TopBarAppearance(showTime = false, showDate = false),
        )
        for (option in options - conditional) assertTrue("$option", option.appliesTo(nothingShown))
    }
}
