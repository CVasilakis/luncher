package com.luncher.domain.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceOptionsTest {

    private val catalog = appearanceOptions()

    @Test
    fun `lists the tiles' options, the top bar's, then the background's, after its picture`() {
        assertEquals(listOf(AppearanceTopic.TILES, AppearanceTopic.TOP_BAR, AppearanceTopic.BACKGROUND), catalog.groups.map { it.topic })
        assertEquals(
            listOf(AppearanceOption.BackgroundFit, AppearanceOption.BackgroundPosition, AppearanceOption.BackgroundDim, AppearanceOption.BackgroundBlur),
            catalog.groups[2].options,
        )
        assertEquals(listOf(false, false, true), catalog.groups.map { it.choosesPicture })
        assertEquals(
            listOf(
                AppearanceOption.VerticalAlignment,
                AppearanceOption.HorizontalAlignment,
                AppearanceOption.FullBottomRow,
                AppearanceOption.CornerRadius,
            ),
            catalog.groups[0].options,
        )
        assertEquals(
            listOf(AppearanceOption.ShowTime, AppearanceOption.ShowDate, AppearanceOption.ClockSide, AppearanceOption.TimeSide),
            catalog.groups[1].options,
        )
    }

    // An option that isn't listed couldn't be changed, nor would the catalog-wide tests cover it.
    @Test
    fun `lists every option, each once`() {
        val declared = AppearanceOption::class.java.classes
            .filter { AppearanceOption::class.java.isAssignableFrom(it) }
            .map { it.getField("INSTANCE").get(null) }
            .toSet()

        assertEquals(declared, catalog.all.toSet())
        assertEquals(catalog.all.distinct(), catalog.all)
    }

    @Test
    fun `has no empty group`() {
        catalog.groups.forEach { assertTrue(it.options.isNotEmpty()) }
    }

    @Test
    fun `the changes the catalog-wide tests draw apply, and differ from their base`() {
        val changes = appearanceChanges()

        assertEquals(catalog.all.sumOf { it.values.size - 1 }, changes.size)
        for (change in changes) {
            assertTrue("$change", change.option.appliesTo(change.base))
            assertTrue("$change", change.changed != change.base)
        }
        // Each applies to the defaults, the background's to the defaults with a picture.
        val withPicture = Appearance(background = Background(picture = SAMPLE_WALLPAPER))
        for (change in changes) assertTrue("$change", change.base == Appearance() || change.base == withPicture)
    }
}
