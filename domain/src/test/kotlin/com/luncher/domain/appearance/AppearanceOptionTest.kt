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
            TileStyle(cornerRadiusDp = 8),
            TopBarAppearance(clockSide = Side.RIGHT, timeSide = Side.RIGHT),
        )

        assertEquals(TilePlacement.BOTTOM_RIGHT, AppearanceOption.Placement.get(appearance))
        assertEquals(true, AppearanceOption.FullBottomRow.get(appearance))
        assertEquals(8, AppearanceOption.CornerRadius.get(appearance))
        assertEquals(Side.RIGHT, AppearanceOption.ClockSide.get(appearance))
        assertEquals(Side.RIGHT, AppearanceOption.TimeSide.get(appearance))
        assertEquals(TileStyle(cornerRadiusDp = 12), AppearanceOption.CornerRadius.set(appearance, 12).tileStyle)
        assertEquals(TileGeometry(placement = TilePlacement.TOP_LEFT, fullBottomRow = true), AppearanceOption.Placement.set(appearance, TilePlacement.TOP_LEFT).tileGeometry)
        assertEquals(TopBarAppearance(clockSide = Side.LEFT, timeSide = Side.RIGHT), AppearanceOption.ClockSide.set(appearance, Side.LEFT).topBar)
    }

    // The defaults are the home screen as it looks without settings.
    @Test
    fun `each option's default is one of its values, and today's look`() {
        for (option in options) assertTrue("$option", option.default in option.values)
        assertEquals(TilePlacement.TOP_LEFT, AppearanceOption.Placement.default)
        assertEquals(false, AppearanceOption.FullBottomRow.default)
        assertEquals(0, AppearanceOption.CornerRadius.default)
        assertEquals(Side.LEFT, AppearanceOption.ClockSide.default)
        assertEquals(Side.LEFT, AppearanceOption.TimeSide.default)
    }

    @Test
    fun `each option has two values at least, each once`() {
        for (option in options) {
            assertTrue("$option", option.values.size >= 2)
            assertEquals("$option", option.values.distinct(), option.values)
        }
    }

    @Test
    fun `the corner radius steps from square to 16 dp`() {
        assertEquals(listOf(0, 4, 8, 12, 16), AppearanceOption.CornerRadius.values)
    }

    @Test
    fun `steps to the next and the previous value`() {
        val radius = AppearanceOption.CornerRadius

        val next = radius.step(Appearance(), forward = true)
        assertEquals(4, radius.get(next))
        assertEquals(0, radius.get(radius.step(next, forward = false)))
    }

    @Test
    fun `wraps around at the first and the last value`() {
        val radius = AppearanceOption.CornerRadius
        val first = Appearance()
        val last = radius.set(first, 16)

        assertEquals(16, radius.get(radius.step(first, forward = false)))
        assertEquals(0, radius.get(radius.step(last, forward = true)))
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
        val odd = Appearance(tileStyle = TileStyle(cornerRadiusDp = 5))

        assertEquals(0, AppearanceOption.CornerRadius.get(AppearanceOption.CornerRadius.step(odd, forward = true)))
    }

    @Test
    fun `a value it doesn't offer falls back to its default`() {
        assertEquals(0, AppearanceOption.CornerRadius.valueOrDefault(5))
        assertEquals(0, AppearanceOption.CornerRadius.valueOrDefault(null))
        assertEquals(12, AppearanceOption.CornerRadius.valueOrDefault(12))
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
    fun `the other options always apply`() {
        for (option in options - AppearanceOption.FullBottomRow) assertTrue("$option", option.appliesTo(Appearance()))
    }
}
