package com.luncher.launcher.appearance

import android.content.Context
import android.content.SharedPreferences
import com.luncher.domain.appearance.AppName
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.AppearanceOption
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.HorizontalAlignment
import com.luncher.domain.appearance.Side
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.appearance.ThemeAppearance
import com.luncher.domain.appearance.ThemeMode
import com.luncher.domain.appearance.TileStyle
import com.luncher.domain.appearance.TopBarAppearance
import com.luncher.domain.appearance.VerticalAlignment
import com.luncher.domain.appearance.appearanceOptions
import com.luncher.domain.appearance.AccentColor
import com.luncher.domain.appearance.Background
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PicturePosition
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
        TileGeometry(
            verticalAlignment = VerticalAlignment.CENTER,
            horizontalAlignment = HorizontalAlignment.RIGHT,
            fullBottomRow = false,
            appName = AppName.WHEN_FOCUSED,
        ),
        TileStyle(Corners.Radius(16), focusFrame = false),
        TopBarAppearance(clockSide = Side.RIGHT, timeSide = Side.RIGHT, showTime = true, showDate = false),
        Background(PictureId("0123456789abcdef0123456789abcdef"), Fit.STRETCH, PicturePosition.LEFT, 40, Blur.LIGHT),
        ThemeAppearance(AccentColor.TEAL, ThemeMode.LIGHT),
    )

    // The stored format, written out: a TV keeps it across updates, so a change of it has to read
    // this one too. The other tests write and read back, which a change of both sides would pass.
    private val stored = mapOf(
        "tile_vertical_alignment" to "CENTER",
        "tile_horizontal_alignment" to "RIGHT",
        "tile_full_bottom_row" to "false",
        "tile_corner_radius" to "16",
        "tile_focus_frame" to "false",
        "tile_app_name" to "WHEN_FOCUSED",
        "clock_side" to "RIGHT",
        "time_side" to "RIGHT",
        "show_time" to "true",
        "show_date" to "false",
        "background_fit" to "STRETCH",
        "background_position" to "LEFT",
        "background_dim" to "40",
        "background_blur" to "LIGHT",
        "background_picture" to "0123456789abcdef0123456789abcdef",
        "theme_accent" to "TEAL",
        "theme_mode" to "LIGHT",
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

    // The time and the date could only show before there were options for them.
    @Test
    fun `stored before the time and the date could be hidden, shows both`() {
        store(stored - "show_time" - "show_date")

        assertEquals(changed.copy(topBar = changed.topBar.copy(showDate = true)), PreferencesAppearanceSettings(context).read())
    }

    @Test
    fun `stores no picture as no key, and reads it back as none`() {
        val none = changed.copy(background = changed.background.copy(picture = null))

        PreferencesAppearanceSettings(context).write(none)

        assertEquals(stored - "background_picture", preferences.all)
        assertEquals(none, PreferencesAppearanceSettings(context).read())
    }

    // The focus frame and the app name were added after the other options: an older version stored
    // neither, and the tiles keep the look they had.
    @Test
    fun `stored before the focus frame and the app name, frames the focused tile without a name`() {
        store(stored - "tile_focus_frame" - "tile_app_name")

        val before = changed.copy(
            tileGeometry = changed.tileGeometry.copy(appName = AppName.OFF),
            tileStyle = changed.tileStyle.copy(focusFrame = true),
        )
        assertEquals(before, PreferencesAppearanceSettings(context).read())
    }

    // The theme was added last: an older version stored none of it, and the screens keep the look they had.
    @Test
    fun `stored before the theme, has the orange accent, dark`() {
        store(stored - "theme_accent" - "theme_mode")

        assertEquals(changed.copy(theme = ThemeAppearance()), PreferencesAppearanceSettings(context).read())
    }

    // The background was added after the other options: an older version stored none of it.
    @Test
    fun `stored before the background, has none`() {
        store(stored.filterKeys { !it.startsWith("background_") })

        assertEquals(changed.copy(background = Background()), PreferencesAppearanceSettings(context).read())
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
            Triple("tile_vertical_alignment", "MIDDLE", AppearanceOption.VerticalAlignment),
            Triple("tile_horizontal_alignment", "center", AppearanceOption.HorizontalAlignment),   // names are exact
            Triple("tile_full_bottom_row", "maybe", AppearanceOption.FullBottomRow),
            Triple("tile_corner_radius", "5", AppearanceOption.CornerRadius),
            Triple("tile_focus_frame", "yes", AppearanceOption.FocusFrame),
            Triple("tile_app_name", "SOMETIMES", AppearanceOption.AppName),
            Triple("clock_side", "right", AppearanceOption.ClockSide),   // names are exact
            Triple("time_side", "1", AppearanceOption.TimeSide),         // a position, never stored
            Triple("show_date", "hidden", AppearanceOption.ShowDate),
            Triple("background_dim", "25", AppearanceOption.BackgroundDim),
            Triple("background_blur", "HEAVY", AppearanceOption.BackgroundBlur),
            Triple("theme_accent", "RED", AppearanceOption.AccentColor),
            Triple("theme_mode", "dark", AppearanceOption.ThemeMode),   // names are exact
        )
        for ((key, value, option) in unknown) {
            preferences.edit().clear().commit()
            store(stored + (key to value))

            assertEquals("$key = $value", toDefault(option, changed), PreferencesAppearanceSettings(context).read())
        }
    }

    @Test
    fun `a value stored as another type reads as that option's default`() {
        store(stored - "tile_corner_radius" + ("tile_corner_radius" to 24))

        assertEquals(AppearanceOption.CornerRadius.set(changed, Corners.Radius(16)), PreferencesAppearanceSettings(context).read())
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
