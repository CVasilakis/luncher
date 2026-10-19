package com.luncher.launcher.settings

import android.content.Context
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.AppearanceOption
import com.luncher.domain.appearance.AppearanceTopic
import com.luncher.domain.appearance.Corners
import com.luncher.domain.appearance.HorizontalAlignment
import com.luncher.domain.appearance.Side
import com.luncher.domain.appearance.VerticalAlignment
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.PicturePosition

// What the appearance's options, their values and groups are called, in the user's language: one
// exhaustive `when` each, so a new option doesn't compile until it has its labels. The appearance
// editor and the background editor both show them.

internal fun topicTitle(topic: AppearanceTopic): Int = when (topic) {
    AppearanceTopic.TILES -> R.string.settings_appearance_tiles
    AppearanceTopic.TOP_BAR -> R.string.settings_appearance_top_bar
    AppearanceTopic.BACKGROUND -> R.string.settings_appearance_background
}

internal fun optionLabel(option: AppearanceOption<*>): Int = when (option) {
    AppearanceOption.VerticalAlignment -> R.string.settings_appearance_vertical_alignment
    AppearanceOption.HorizontalAlignment -> R.string.settings_appearance_horizontal_alignment
    AppearanceOption.FullBottomRow -> R.string.settings_appearance_full_bottom_row
    AppearanceOption.CornerRadius -> R.string.settings_appearance_corner_radius
    AppearanceOption.ShowTime -> R.string.settings_appearance_time
    AppearanceOption.ShowDate -> R.string.settings_appearance_date
    AppearanceOption.ClockSide -> R.string.settings_appearance_clock_side
    AppearanceOption.TimeSide -> R.string.settings_appearance_time_side
    AppearanceOption.BackgroundFit -> R.string.settings_appearance_fit
    AppearanceOption.BackgroundPosition -> R.string.settings_appearance_position
    AppearanceOption.BackgroundDim -> R.string.settings_appearance_dim
    AppearanceOption.BackgroundBlur -> R.string.settings_appearance_blur
}

/** What [option]'s value in [appearance] is called. */
internal fun Context.optionValue(option: AppearanceOption<*>, appearance: Appearance): String = when (option) {
    AppearanceOption.VerticalAlignment -> getString(vertical(AppearanceOption.VerticalAlignment.get(appearance)))
    AppearanceOption.HorizontalAlignment -> getString(horizontal(AppearanceOption.HorizontalAlignment.get(appearance)))
    AppearanceOption.FullBottomRow -> getString(onOff(AppearanceOption.FullBottomRow.get(appearance)))
    AppearanceOption.CornerRadius -> when (val corners = AppearanceOption.CornerRadius.get(appearance)) {
        is Corners.Radius -> getString(R.string.settings_appearance_radius_dp, corners.dp)
        Corners.Pill -> getString(R.string.settings_appearance_pill)
    }
    AppearanceOption.ShowTime -> getString(shown(AppearanceOption.ShowTime.get(appearance)))
    AppearanceOption.ShowDate -> getString(shown(AppearanceOption.ShowDate.get(appearance)))
    AppearanceOption.ClockSide -> getString(side(AppearanceOption.ClockSide.get(appearance)))
    AppearanceOption.TimeSide -> getString(order(AppearanceOption.TimeSide.get(appearance)))
    AppearanceOption.BackgroundFit -> getString(fit(AppearanceOption.BackgroundFit.get(appearance)))
    AppearanceOption.BackgroundPosition -> getString(position(AppearanceOption.BackgroundPosition.get(appearance)))
    AppearanceOption.BackgroundDim -> getString(dim(AppearanceOption.BackgroundDim.get(appearance)))
    AppearanceOption.BackgroundBlur -> getString(blur(AppearanceOption.BackgroundBlur.get(appearance)))
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
