package com.luncher.launcher.appearance

import android.content.Context
import android.content.SharedPreferences
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.AppearanceOption
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.Side
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.TilePlacement
import com.luncher.domain.appearance.TileStyle
import com.luncher.domain.appearance.TopBarAppearance
import com.luncher.domain.appearance.appearanceOptions
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PreferencesAppearanceSettingsTest {

    private val context = RuntimeEnvironment.getApplication()

    // The file the adapter keeps the appearance in, named here as the stored format below is: a
    // TV keeps it across updates too.
    private val preferences: SharedPreferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    private val changed = Appearance(
        TileGeometry(placement = TilePlacement.BOTTOM_RIGHT, fullBottomRow = true),
        TileStyle(Corners.Radius(12)),
        TopBarAppearance(clockSide = Side.RIGHT, timeSide = Side.RIGHT),
    )

    // The stored format, written out: a TV keeps it across updates, so a change of it has to read
    // this one too. The other tests write and read back, which a change of both sides would pass.
    private val stored = mapOf(
        "tile_placement" to "BOTTOM_RIGHT",
        "tile_full_bottom_row" to "true",
        "tile_corner_radius" to "12",
        "clock_side" to "RIGHT",
        "time_side" to "RIGHT",
    )

    private fun store(values: Map<String, Any>) {
        val editor = preferences.edit()
        for ((key, value) in values) {
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                else -> error("$value")
            }
        }
        editor.commit()
    }

    @Test
    fun `reads the stored format`() {
        store(stored)

        assertEquals(changed, PreferencesAppearanceSettings(context).read())
    }

    @Test
    fun `writes in the stored format`() {
        PreferencesAppearanceSettings(context).write(changed)

        assertEquals(stored, preferences.all)
    }

    @Test
    fun `stores round ends by name`() {
        PreferencesAppearanceSettings(context).write(AppearanceOption.CornerRadius.set(changed, Corners.Pill))

        assertEquals("pill", preferences.all["tile_corner_radius"])
        assertEquals(Corners.Pill, PreferencesAppearanceSettings(context).read().tileStyle.corners)
    }

    @Test
    fun `has the defaults before anything is written`() {
        assertEquals(Appearance(), PreferencesAppearanceSettings(context).read())
    }

    // A loop over the catalog, so an option added to it is covered too.
    @Test
    fun `reads back every value of every option, after the process restarts`() {
        for (option in appearanceOptions().all) checkRoundTrip(option)
    }

    private fun <T> checkRoundTrip(option: AppearanceOption<T>) {
        for (value in option.values) {
            val appearance = option.set(changed, value)

            PreferencesAppearanceSettings(context).write(appearance)

            assertEquals("$option = $value", appearance, PreferencesAppearanceSettings(context).read())
        }
    }

    // Stored by an older or newer version: only that option falls back to its default.
    @Test
    fun `a stored value it doesn't know reads as that option's default, and changes nothing else`() {
        val unknown = listOf(
            Triple("tile_placement", "MIDDLE", AppearanceOption.Placement),
            Triple("tile_full_bottom_row", "maybe", AppearanceOption.FullBottomRow),
            Triple("tile_corner_radius", "5", AppearanceOption.CornerRadius),
            Triple("clock_side", "right", AppearanceOption.ClockSide),   // names are exact
            Triple("time_side", "1", AppearanceOption.TimeSide),         // a position, never stored
        )
        for ((key, value, option) in unknown) {
            preferences.edit().clear().commit()
            store(stored + (key to value))

            assertEquals("$key = $value", toDefault(option, changed), PreferencesAppearanceSettings(context).read())
        }
    }

    @Test
    fun `a value stored as another type reads as that option's default`() {
        store(stored - "tile_corner_radius" + ("tile_corner_radius" to 12))

        assertEquals(AppearanceOption.CornerRadius.set(changed, Corners.Radius(8)), PreferencesAppearanceSettings(context).read())
    }

    @Test
    fun `reads the storage once`() {
        val settings = PreferencesAppearanceSettings(context)
        settings.read()

        store(stored)

        assertEquals(Appearance(), settings.read())
    }

    @Test
    fun `a write is read back at once`() {
        val settings = PreferencesAppearanceSettings(context)
        settings.read()

        settings.write(changed)

        assertEquals(changed, settings.read())
    }

    @Test
    fun `tells its listeners of every write, until they're removed`() {
        val settings = PreferencesAppearanceSettings(context)
        val heard = mutableListOf<Appearance>()
        val listener = { heard += settings.read() }
        settings.addListener(listener)

        settings.write(changed)
        settings.write(changed)   // the same again: the listeners decide what changed
        settings.removeListener(listener)
        settings.write(Appearance())

        assertEquals(listOf(changed, changed), heard)
    }

    private fun <T> toDefault(option: AppearanceOption<T>, appearance: Appearance) = option.set(appearance, option.get(Appearance()))
}
