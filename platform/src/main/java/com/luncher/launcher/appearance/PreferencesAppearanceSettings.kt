package com.luncher.launcher.appearance

import android.content.Context
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.AppearanceOption
import com.luncher.domain.appearance.AppearanceSettings
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.appearanceOptions
import com.luncher.domain.appearance.valueOrDefault
import com.luncher.domain.pictures.PictureId

/**
 * [AppearanceSettings] in SharedPreferences: one string per option of the catalog, under the key
 * [key] assigns it; a choice by its name, never its position, a number in digits (a corner radius
 * too, in dp, or "pill"). A stored value
 * that isn't one of the option's values (an older or newer version's, or of another type) reads
 * as that option's default, and changes nothing else. An option never written has no key. The
 * background's picture, which isn't an option, is its id under [PICTURE], or no key for none.
 *
 * Read from storage once per process, then kept; a write stores in the background (`apply`) and
 * tells the listeners at once. Every screen gets this one instance from AppGraph, so a write made
 * by one reaches the listeners of another.
 */
class PreferencesAppearanceSettings(context: Context) : AppearanceSettings {

    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val listeners = mutableListOf<() -> Unit>()

    private var current: Appearance? = null

    override fun read(): Appearance {
        current?.let { return it }
        val stored = preferences.all
        val picture = (stored[PICTURE] as? String)?.let(::PictureId)
        return appearanceOptions().all.fold(Appearance(background = Background(picture = picture))) { appearance, option -> readInto(appearance, option, stored) }
            .also { current = it }
    }

    override fun write(appearance: Appearance) {
        current = appearance
        val editor = preferences.edit()
        for (option in appearanceOptions().all) editor.putString(key(option), encode(option.get(appearance)))
        val picture = appearance.background.picture
        if (picture == null) editor.remove(PICTURE) else editor.putString(PICTURE, picture.value)
        editor.apply()
        listeners.toList().forEach { it() }
    }

    override fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    override fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    private fun <T> readInto(appearance: Appearance, option: AppearanceOption<T>, stored: Map<String, *>): Appearance {
        val text = stored[key(option)] as? String
        val value = option.values.firstOrNull { encode(it) == text }
        return option.set(appearance, option.valueOrDefault(value))
    }

    private fun encode(value: Any?): String = when (value) {
        is Enum<*> -> value.name
        is Int, is Boolean -> value.toString()
        is Corners.Radius -> value.dp.toString()
        Corners.Pill -> "pill"
        else -> error("no stored form for $value")
    }

    /** Where each option is kept. A key, once released, is never given to another option. */
    private fun key(option: AppearanceOption<*>): String = when (option) {
        AppearanceOption.VerticalAlignment -> "tile_vertical_alignment"
        AppearanceOption.HorizontalAlignment -> "tile_horizontal_alignment"
        AppearanceOption.FullBottomRow -> "tile_full_bottom_row"
        AppearanceOption.CornerRadius -> "tile_corner_radius"
        AppearanceOption.ClockSide -> "clock_side"
        AppearanceOption.TimeSide -> "time_side"
        AppearanceOption.ShowTime -> "show_time"
        AppearanceOption.ShowDate -> "show_date"
        AppearanceOption.BackgroundFit -> "background_fit"
        AppearanceOption.BackgroundPosition -> "background_position"
        AppearanceOption.BackgroundDim -> "background_dim"
        AppearanceOption.BackgroundBlur -> "background_blur"
    }

    private companion object {
        const val FILE = "appearance"
        const val PICTURE = "background_picture"
    }
}
