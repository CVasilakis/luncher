package com.luncher.launcher.settings

import android.content.Context
import com.luncher.domain.appearance.AccentColor
import com.luncher.domain.appearance.AppName
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.AppearanceOption
import com.luncher.domain.appearance.AppearanceTopic
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.HorizontalAlignment
import com.luncher.domain.appearance.Side
import com.luncher.domain.appearance.ThemeMode
import com.luncher.domain.appearance.VerticalAlignment
import com.luncher.domain.appearance.colorRoles
import com.luncher.domain.apps.TileBackdrop
import com.luncher.domain.apps.TileFocusShape
import com.luncher.domain.apps.TileImage
import com.luncher.domain.apps.TileImageOption
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.PicturePosition
import com.luncher.launcher.ui.R as UiR

// What the options, their values and the appearance's groups are called, in the user's language:
// one exhaustive `when` each, so a new option doesn't compile until it has its labels. The
// appearance editor and the background editor both show the appearance's; the tile banner picker
// the tile image's, its fit and position named as the background's are.

/**
 * What a catalog's options [O], over its model [M], are called, and their values, in the user's
 * language; the color shown before a value. A catalog's labels answer each in an exhaustive `when`
 * over its options, so an option added to the catalog doesn't compile until it has its labels.
 */
internal interface OptionLabels<M, O> {

    /** [option]'s name, a string resource. */
    fun label(option: O): Int

    /** What [option]'s value in [model] is called. */
    fun value(option: O, model: M, context: Context): String

    /** The color shown before [option]'s value; none by default. */
    fun swatch(option: O, model: M): Int? = null
}

internal fun topicTitle(topic: AppearanceTopic): Int = when (topic) {
    AppearanceTopic.TILES -> R.string.settings_appearance_tiles
    AppearanceTopic.TOP_BAR -> R.string.settings_appearance_top_bar
    AppearanceTopic.THEME -> R.string.settings_appearance_theme
    AppearanceTopic.BACKGROUND -> R.string.settings_appearance_background
}

/** The icon before a group's name. */
internal fun topicIcon(topic: AppearanceTopic): Int = when (topic) {
    AppearanceTopic.TILES -> UiR.drawable.icon_grid
    AppearanceTopic.TOP_BAR -> UiR.drawable.icon_clock
    AppearanceTopic.THEME -> UiR.drawable.icon_palette
    AppearanceTopic.BACKGROUND -> UiR.drawable.icon_image
}

/** The appearance's options ([AppearanceOption]), one exhaustive `when` each. */
internal object AppearanceLabels : OptionLabels<Appearance, AppearanceOption<*>> {

    override fun label(option: AppearanceOption<*>): Int = when (option) {
        AppearanceOption.VerticalAlignment -> R.string.settings_appearance_vertical_alignment
        AppearanceOption.HorizontalAlignment -> R.string.settings_appearance_horizontal_alignment
        AppearanceOption.FullBottomRow -> R.string.settings_appearance_full_bottom_row
        AppearanceOption.CornerRadius -> R.string.settings_appearance_corner_radius
        AppearanceOption.FocusFrame -> R.string.settings_appearance_focus_frame
        AppearanceOption.AppName -> R.string.settings_appearance_app_name
        AppearanceOption.ShowTime -> R.string.settings_appearance_time
        AppearanceOption.ShowDate -> R.string.settings_appearance_date
        AppearanceOption.ClockSide -> R.string.settings_appearance_clock_side
        AppearanceOption.TimeSide -> R.string.settings_appearance_time_side
        AppearanceOption.AccentColor -> R.string.settings_appearance_accent
        AppearanceOption.ThemeMode -> R.string.settings_appearance_mode
        AppearanceOption.BackgroundFit -> R.string.settings_appearance_fit
        AppearanceOption.BackgroundPosition -> R.string.settings_appearance_position
        AppearanceOption.BackgroundDim -> R.string.settings_appearance_dim
        AppearanceOption.BackgroundBlur -> R.string.settings_appearance_blur
    }

    override fun value(option: AppearanceOption<*>, model: Appearance, context: Context): String = when (option) {
        AppearanceOption.VerticalAlignment -> context.getString(vertical(AppearanceOption.VerticalAlignment.get(model)))
        AppearanceOption.HorizontalAlignment -> context.getString(horizontal(AppearanceOption.HorizontalAlignment.get(model)))
        AppearanceOption.FullBottomRow -> context.getString(onOff(AppearanceOption.FullBottomRow.get(model)))
        AppearanceOption.CornerRadius -> when (val corners = AppearanceOption.CornerRadius.get(model)) {
            is Corners.Radius -> context.getString(R.string.settings_appearance_radius_dp, corners.dp)
            Corners.Pill -> context.getString(R.string.settings_appearance_pill)
        }
        AppearanceOption.FocusFrame -> context.getString(onOff(AppearanceOption.FocusFrame.get(model)))
        AppearanceOption.AppName -> context.getString(appName(AppearanceOption.AppName.get(model)))
        AppearanceOption.ShowTime -> context.getString(shown(AppearanceOption.ShowTime.get(model)))
        AppearanceOption.ShowDate -> context.getString(shown(AppearanceOption.ShowDate.get(model)))
        AppearanceOption.ClockSide -> context.getString(side(AppearanceOption.ClockSide.get(model)))
        AppearanceOption.TimeSide -> context.getString(order(AppearanceOption.TimeSide.get(model)))
        AppearanceOption.AccentColor -> context.getString(accent(AppearanceOption.AccentColor.get(model)))
        AppearanceOption.ThemeMode -> context.getString(mode(AppearanceOption.ThemeMode.get(model)))
        AppearanceOption.BackgroundFit -> context.getString(fit(AppearanceOption.BackgroundFit.get(model)))
        AppearanceOption.BackgroundPosition -> context.getString(position(AppearanceOption.BackgroundPosition.get(model)))
        AppearanceOption.BackgroundDim -> context.getString(dim(AppearanceOption.BackgroundDim.get(model)))
        AppearanceOption.BackgroundBlur -> context.getString(blur(AppearanceOption.BackgroundBlur.get(model)))
    }

    /** For the accent: the accent itself, as the menus show it. */
    override fun swatch(option: AppearanceOption<*>, model: Appearance): Int? = when (option) {
        AppearanceOption.AccentColor -> colorRoles(model.theme).primary
        else -> null
    }
}

/** The tile image's options ([TileImageOption]): the fit and the position, called as the background's; the tile's look. */
internal object TileImageLabels : OptionLabels<TileImage, TileImageOption<*>> {

    override fun label(option: TileImageOption<*>): Int = when (option) {
        TileImageOption.TileFit -> R.string.settings_appearance_fit
        TileImageOption.TilePosition -> R.string.settings_appearance_position
        TileImageOption.Backdrop -> R.string.settings_tile_banner_backdrop
        TileImageOption.Focus -> R.string.settings_tile_banner_focus
    }

    override fun value(option: TileImageOption<*>, model: TileImage, context: Context): String = when (option) {
        TileImageOption.TileFit -> context.getString(fit(TileImageOption.TileFit.get(model)))
        TileImageOption.TilePosition -> context.getString(position(TileImageOption.TilePosition.get(model)))
        TileImageOption.Backdrop -> context.getString(backdrop(TileImageOption.Backdrop.get(model)))
        TileImageOption.Focus -> context.getString(focusShape(TileImageOption.Focus.get(model)))
    }
}

private fun backdrop(backdrop: TileBackdrop): Int = when (backdrop) {
    TileBackdrop.NONE -> R.string.settings_tile_banner_none
    TileBackdrop.LIGHT_GLASS -> R.string.settings_tile_banner_light_glass
    TileBackdrop.DARK_GLASS -> R.string.settings_tile_banner_dark_glass
    TileBackdrop.FROSTED -> R.string.settings_tile_banner_frosted
}

private fun focusShape(focus: TileFocusShape): Int = when (focus) {
    TileFocusShape.TILE -> R.string.settings_tile_banner_tile
    TileFocusShape.SHAPE -> R.string.settings_tile_banner_shape
}

private fun accent(accent: AccentColor): Int = when (accent) {
    AccentColor.ORANGE -> R.string.settings_appearance_orange
    AccentColor.PINK -> R.string.settings_appearance_pink
    AccentColor.PURPLE -> R.string.settings_appearance_purple
    AccentColor.BLUE -> R.string.settings_appearance_blue
    AccentColor.TEAL -> R.string.settings_appearance_teal
    AccentColor.GREEN -> R.string.settings_appearance_green
    AccentColor.AMBER -> R.string.settings_appearance_amber
    AccentColor.GRAY -> R.string.settings_appearance_gray
}

private fun mode(mode: ThemeMode): Int = when (mode) {
    ThemeMode.DARK -> R.string.settings_appearance_dark
    ThemeMode.LIGHT -> R.string.settings_appearance_light_mode
}

private fun appName(name: AppName): Int = when (name) {
    AppName.OFF -> R.string.settings_appearance_off
    AppName.WHEN_FOCUSED -> R.string.settings_appearance_when_focused
    AppName.ALWAYS -> R.string.settings_appearance_always
}

private fun fit(fit: Fit): Int = when (fit) {
    Fit.FILL -> R.string.settings_appearance_fill
    Fit.STRETCH -> R.string.settings_appearance_stretch
}

private fun position(position: PicturePosition): Int = when (position) {
    PicturePosition.CENTER -> R.string.settings_appearance_center
    PicturePosition.TOP -> R.string.settings_appearance_top
    PicturePosition.BOTTOM -> R.string.settings_appearance_bottom
    PicturePosition.LEFT -> R.string.settings_appearance_left
    PicturePosition.RIGHT -> R.string.settings_appearance_right
}

private fun blur(blur: Blur): Int = when (blur) {
    Blur.OFF -> R.string.settings_appearance_off
    Blur.LIGHT -> R.string.settings_appearance_light
    Blur.MEDIUM -> R.string.settings_appearance_medium
    Blur.STRONG -> R.string.settings_appearance_strong
}

/** The dim's percentages, [AppearanceOption.BackgroundDim]'s values, said as Blur's are. */
private fun dim(percent: Int): Int = when (percent) {
    0 -> R.string.settings_appearance_off
    20 -> R.string.settings_appearance_dim_light
    40 -> R.string.settings_appearance_dim_medium
    60 -> R.string.settings_appearance_dim_strong
    else -> error("No label for a dim of $percent %")
}

private fun vertical(alignment: VerticalAlignment): Int = when (alignment) {
    VerticalAlignment.TOP -> R.string.settings_appearance_top
    VerticalAlignment.CENTER -> R.string.settings_appearance_center
    VerticalAlignment.BOTTOM -> R.string.settings_appearance_bottom
}

private fun horizontal(alignment: HorizontalAlignment): Int = when (alignment) {
    HorizontalAlignment.LEFT -> R.string.settings_appearance_left
    HorizontalAlignment.CENTER -> R.string.settings_appearance_center
    HorizontalAlignment.RIGHT -> R.string.settings_appearance_right
}

private fun onOff(on: Boolean): Int = if (on) R.string.settings_appearance_on else R.string.settings_appearance_off

private fun shown(shown: Boolean): Int =
    if (shown) R.string.settings_appearance_shown else R.string.settings_appearance_hidden

private fun side(side: Side): Int = when (side) {
    Side.LEFT -> R.string.settings_appearance_left
    Side.RIGHT -> R.string.settings_appearance_right
}

/** The time's side of the date, as the order the two read in, left to right. */
private fun order(timeSide: Side): Int = when (timeSide) {
    Side.LEFT -> R.string.settings_appearance_time_first
    Side.RIGHT -> R.string.settings_appearance_date_first
}
