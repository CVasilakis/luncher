package com.luncher.domain.appearance

import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.PictureCut
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.appearance.HorizontalAlignment as Horizontal
import com.luncher.domain.appearance.VerticalAlignment as Vertical

/**
 * Something the user can change about how the home screen looks: the part of [Appearance] it
 * reads and changes, and the values it can take, in the order the editors offer them. A
 * number is a list of steps too, so every option is changed the same way. Each option is a type of
 * its own, so every layer that handles options (storage, labels) does so in an exhaustive `when`,
 * and a new option doesn't compile until each one handles it (docs/ARCHITECTURE.md#appearance).
 * Which options the editor lists, in which groups: [appearanceOptions]. Which of its values change
 * anything may also depend on what isn't in the appearance: an [OptionContext].
 */
sealed interface AppearanceOption<T> {

    /**
     * Every value, in order; the option's [default] is one of them. What's stored is one of these,
     * whatever the [OptionContext].
     */
    val values: List<T>

    fun get(appearance: Appearance): T

    fun set(appearance: Appearance, value: T): Appearance

    /**
     * Whether the option changes anything with the rest of [appearance] as it is, and [context]:
     * one that doesn't is shown but can't be changed.
     */
    fun appliesTo(appearance: Appearance, context: OptionContext = OptionContext.UNKNOWN): Boolean = true

    /**
     * The values offered in [context], in order (stepped through, or listed): those of [values]
     * that change anything there, the [default] among them. All of them by default, and where it
     * isn't known.
     */
    fun values(context: OptionContext): List<T> = values

    /** Where the rows of tiles go in the height. */
    data object VerticalAlignment : AppearanceOption<Vertical> {
        override val values: List<Vertical> = Vertical.entries
        override fun get(appearance: Appearance) = appearance.tileGeometry.verticalAlignment
        override fun set(appearance: Appearance, value: Vertical) =
            appearance.copy(tileGeometry = appearance.tileGeometry.copy(verticalAlignment = value))
    }

    /** Where a row of tiles that isn't full goes in the width. */
    data object HorizontalAlignment : AppearanceOption<Horizontal> {
        override val values: List<Horizontal> = Horizontal.entries
        override fun get(appearance: Appearance) = appearance.tileGeometry.horizontalAlignment
        override fun set(appearance: Appearance, value: Horizontal) =
            appearance.copy(tileGeometry = appearance.tileGeometry.copy(horizontalAlignment = value))
    }

    /** With the rows centered or at the bottom, whether the bottom row is always full. */
    data object FullBottomRow : AppearanceOption<Boolean> {
        override val values: List<Boolean> = listOf(false, true)
        override fun get(appearance: Appearance) = appearance.tileGeometry.fullBottomRow
        override fun set(appearance: Appearance, value: Boolean) =
            appearance.copy(tileGeometry = appearance.tileGeometry.copy(fullBottomRow = value))
        override fun appliesTo(appearance: Appearance, context: OptionContext) = appearance.tileGeometry.verticalAlignment != Vertical.TOP
    }

    /** How round the tiles' corners are: radii in dp, then round ends. */
    data object CornerRadius : AppearanceOption<Corners> {
        override val values: List<Corners> = listOf(0, 4, 8, 12, 16, 24, 32).map { Corners.Radius(it) } + Corners.Pill
        override fun get(appearance: Appearance) = appearance.tileStyle.corners
        override fun set(appearance: Appearance, value: Corners) =
            appearance.copy(tileStyle = appearance.tileStyle.copy(corners = value))
    }

    /** Whether the top bar shows the time. */
    data object ShowTime : AppearanceOption<Boolean> {
        override val values: List<Boolean> = listOf(true, false)
        override fun get(appearance: Appearance) = appearance.topBar.showTime
        override fun set(appearance: Appearance, value: Boolean) =
            appearance.copy(topBar = appearance.topBar.copy(showTime = value))
    }

    /** Whether the top bar shows the date. */
    data object ShowDate : AppearanceOption<Boolean> {
        override val values: List<Boolean> = listOf(true, false)
        override fun get(appearance: Appearance) = appearance.topBar.showDate
        override fun set(appearance: Appearance, value: Boolean) =
            appearance.copy(topBar = appearance.topBar.copy(showDate = value))
    }

    /** The side of the top bar the time and the date are on, while either shows. */
    data object ClockSide : AppearanceOption<Side> {
        override val values: List<Side> = Side.entries
        override fun get(appearance: Appearance) = appearance.topBar.clockSide
        override fun set(appearance: Appearance, value: Side) =
            appearance.copy(topBar = appearance.topBar.copy(clockSide = value))
        override fun appliesTo(appearance: Appearance, context: OptionContext) = appearance.topBar.showTime || appearance.topBar.showDate
    }

    /** The side of the date the time is on, while both show. */
    data object TimeSide : AppearanceOption<Side> {
        override val values: List<Side> = Side.entries
        override fun get(appearance: Appearance) = appearance.topBar.timeSide
        override fun set(appearance: Appearance, value: Side) =
            appearance.copy(topBar = appearance.topBar.copy(timeSide = value))
        override fun appliesTo(appearance: Appearance, context: OptionContext) = appearance.topBar.showTime && appearance.topBar.showDate
    }

    /**
     * How the background's picture fills the screen, while there's one of another shape: filling or
     * stretched, one of the screen's shape looks the same.
     */
    data object BackgroundFit : AppearanceOption<Fit> {
        override val values: List<Fit> = Fit.entries
        override fun get(appearance: Appearance) = appearance.background.fit
        override fun set(appearance: Appearance, value: Fit) =
            appearance.copy(background = appearance.background.copy(fit = value))
        override fun appliesTo(appearance: Appearance, context: OptionContext) =
            appearance.background.picture != null && context.backgroundCut != PictureCut.NONE
    }

    /**
     * Which part of the background's picture shows, while it fills the screen and is cut: the
     * middle or an end of the side cut, left or right of a wide picture, top or bottom of a tall one.
     */
    data object BackgroundPosition : AppearanceOption<PicturePosition> {
        override val values: List<PicturePosition> = PicturePosition.entries
        private val acrossTheSides = listOf(PicturePosition.CENTER, PicturePosition.LEFT, PicturePosition.RIGHT)
        private val acrossTheTop = listOf(PicturePosition.CENTER, PicturePosition.TOP, PicturePosition.BOTTOM)
        private val middleOnly = listOf(PicturePosition.CENTER)
        override fun get(appearance: Appearance) = appearance.background.position
        override fun set(appearance: Appearance, value: PicturePosition) =
            appearance.copy(background = appearance.background.copy(position = value))
        override fun appliesTo(appearance: Appearance, context: OptionContext) =
            appearance.background.picture != null && appearance.background.fit == Fit.FILL && context.backgroundCut != PictureCut.NONE
        override fun values(context: OptionContext) = when (context.backgroundCut) {
            null -> values
            PictureCut.SIDES -> acrossTheSides
            PictureCut.TOP_AND_BOTTOM -> acrossTheTop
            PictureCut.NONE -> middleOnly
        }
    }

    /** How much darker the background's picture is drawn, in percent, while there's one. */
    data object BackgroundDim : AppearanceOption<Int> {
        override val values: List<Int> = listOf(0, 20, 40, 60)
        override fun get(appearance: Appearance) = appearance.background.dim
        override fun set(appearance: Appearance, value: Int) =
            appearance.copy(background = appearance.background.copy(dim = value))
        override fun appliesTo(appearance: Appearance, context: OptionContext) = appearance.background.picture != null
    }

    /** How blurred the background's picture is drawn, while there's one. */
    data object BackgroundBlur : AppearanceOption<Blur> {
        override val values: List<Blur> = Blur.entries
        override fun get(appearance: Appearance) = appearance.background.blur
        override fun set(appearance: Appearance, value: Blur) =
            appearance.copy(background = appearance.background.copy(blur = value))
        override fun appliesTo(appearance: Appearance, context: OptionContext) = appearance.background.picture != null
    }
}

/**
 * What decides, besides the [Appearance], which values of the options change anything: facts of the
 * device and the user's things, which no option sets, such as the shape of the background's
 * picture on this screen. Each fact is null while it isn't known, which offers every value, as for
 * the options it doesn't concern; a fact added later is one more field, which leaves every place
 * that doesn't know it as it is.
 */
data class OptionContext(
    /** What filling the screen cuts from the background's picture ([pictureCut][com.luncher.domain.pictures.pictureCut]). */
    val backgroundCut: PictureCut? = null,
) {
    companion object {
        /** Nothing known: every value of every option offered. */
        val UNKNOWN = OptionContext()
    }
}

/** The option's value in the home screen as it looks without settings. */
val <T> AppearanceOption<T>.default: T get() = get(Appearance())

/**
 * [value] if it's one of the option's values, else the option's default: for a value stored by
 * an older or newer version, which affects this option only.
 */
fun <T> AppearanceOption<T>.valueOrDefault(value: T?): T =
    if (value != null && value in values) value else default

/**
 * [appearance] with this option at its next value ([forward]) or its previous one among those
 * [context] offers ([AppearanceOption.values]), around: after the last value comes the first, and
 * before the first the last. From a value not among them, the step is to the option's default.
 */
fun <T> AppearanceOption<T>.step(appearance: Appearance, forward: Boolean, context: OptionContext = OptionContext.UNKNOWN): Appearance {
    val values = values(context)
    val index = values.indexOf(get(appearance))
    if (index < 0) return set(appearance, default)
    val next = (index + if (forward) 1 else -1).mod(values.size)
    return set(appearance, values[next])
}
