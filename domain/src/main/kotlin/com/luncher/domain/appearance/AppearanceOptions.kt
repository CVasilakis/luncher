package com.luncher.domain.appearance

/** What the appearance editor lists: groups of options, in the order shown. */
data class AppearanceOptions(val groups: List<AppearanceGroup>) {

    /** Every option, in order. */
    val all: List<AppearanceOption<*>> get() = groups.flatMap { it.options }
}

/**
 * Options shown together under a title: what part of the home screen they change. A group whose
 * options are about a picture ([choosesPicture]) starts with the choice of that picture, which
 * isn't an option: the pictures aren't a list of values known here, so the editor has the user
 * choose one in the picture library.
 */
data class AppearanceGroup(
    val topic: AppearanceTopic,
    val options: List<AppearanceOption<*>>,
    val choosesPicture: Boolean = false,
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
                choosesPicture = true,
            ),
        ),
    )
