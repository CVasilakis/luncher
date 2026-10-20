package com.luncher.domain.appearance

/** What the appearance editor lists: groups of options, in the order shown. */
data class AppearanceOptions(val groups: List<AppearanceGroup>) {

    /** Every option, in order. */
    val all: List<AppearanceOption<*>> get() = groups.flatMap { it.options }
}

/**
 * Options shown together under a title: what part of the home screen they change. A group
 * [editedApart] is one entry of the appearance editor that opens a screen of its own, where the
 * user chooses a picture with the options and saves them together: the pictures aren't a list of
 * values known here, and applying them is slow (the wallpaper is drawn), so they're stored only
 * when the user says so, not at each change as the other options are.
 */
data class AppearanceGroup(
    val topic: AppearanceTopic,
    val options: List<AppearanceOption<*>>,
    val editedApart: Boolean = false,
)

/** The part of the home screen a group's options change; each screen names it in its language. */
enum class AppearanceTopic { TILES, TOP_BAR, BACKGROUND }

/** The options the user can change, and where each one goes: the tiles', the top bar's, then the background's. */
fun appearanceOptions(): AppearanceOptions =
    AppearanceOptions(
        listOf(
            AppearanceGroup(
                AppearanceTopic.TILES,
                listOf(
                    AppearanceOption.VerticalAlignment,
                    AppearanceOption.HorizontalAlignment,
                    AppearanceOption.FullBottomRow,
                    AppearanceOption.CornerRadius,
                ),
            ),
            AppearanceGroup(
                AppearanceTopic.TOP_BAR,
                listOf(
                    AppearanceOption.ShowTime,
                    AppearanceOption.ShowDate,
                    AppearanceOption.ClockSide,
                    AppearanceOption.TimeSide,
                ),
            ),
            AppearanceGroup(
                AppearanceTopic.BACKGROUND,
                listOf(
                    AppearanceOption.BackgroundFit,
                    AppearanceOption.BackgroundPosition,
                    AppearanceOption.BackgroundDim,
                    AppearanceOption.BackgroundBlur,
                ),
                editedApart = true,
            ),
        ),
    )
