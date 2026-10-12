package com.luncher.domain.appearance

/** What the appearance editor lists: groups of options, in the order shown. */
data class AppearanceOptions(val groups: List<AppearanceGroup>) {

    /** Every option, in order. */
    val all: List<AppearanceOption<*>> get() = groups.flatMap { it.options }
}

/** Options shown together under a title: what part of the home screen they change. */
data class AppearanceGroup(val topic: AppearanceTopic, val options: List<AppearanceOption<*>>)

/** The part of the home screen a group's options change; each screen names it in its language. */
enum class AppearanceTopic { TILES, TOP_BAR }

/** The options the user can change, and where each one goes: the tiles' first, then the top bar's. */
fun appearanceOptions(): AppearanceOptions =
    AppearanceOptions(
        listOf(
            AppearanceGroup(
                AppearanceTopic.TILES,
                listOf(AppearanceOption.Placement, AppearanceOption.FullBottomRow, AppearanceOption.CornerRadius),
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
        ),
    )
