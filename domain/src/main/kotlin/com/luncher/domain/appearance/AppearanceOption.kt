package com.luncher.domain.appearance

/**
 * Something the user can change about how the home screen looks: the part of [Appearance] it
 * reads and changes, and the values it can take, in the order the user steps through them. A
 * number is a list of steps too, so every option is changed the same way. Each option is a type of
 * its own, so every layer that handles options (storage, labels) does so in an exhaustive `when`,
 * and a new option doesn't compile until each one handles it (docs/ARCHITECTURE.md#appearance).
 * Which options the editor lists, in which groups: [appearanceOptions].
 */
sealed interface AppearanceOption<T> {

    /** The values, in order; the option's [default] is one of them. */
    val values: List<T>

    fun get(appearance: Appearance): T

    fun set(appearance: Appearance, value: T): Appearance

    /**
     * Whether the option changes anything with the rest of [appearance] as it is: one that
     * doesn't is shown but can't be changed.
     */
    fun appliesTo(appearance: Appearance): Boolean = true

    /** The corner the tiles gather in. */
    data object Placement : AppearanceOption<TilePlacement> {
        override val values: List<TilePlacement> = TilePlacement.entries
        override fun get(appearance: Appearance) = appearance.tileGeometry.placement
        override fun set(appearance: Appearance, value: TilePlacement) =
            appearance.copy(tileGeometry = appearance.tileGeometry.copy(placement = value))
    }

    /** With the tiles at the bottom, whether the bottom row is always full. */
    data object FullBottomRow : AppearanceOption<Boolean> {
        override val values: List<Boolean> = listOf(false, true)
        override fun get(appearance: Appearance) = appearance.tileGeometry.fullBottomRow
        override fun set(appearance: Appearance, value: Boolean) =
            appearance.copy(tileGeometry = appearance.tileGeometry.copy(fullBottomRow = value))
        override fun appliesTo(appearance: Appearance) = appearance.tileGeometry.placement.atBottom
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
        override fun appliesTo(appearance: Appearance) = appearance.topBar.showTime || appearance.topBar.showDate
    }

    /** The side of the date the time is on, while both show. */
    data object TimeSide : AppearanceOption<Side> {
        override val values: List<Side> = Side.entries
        override fun get(appearance: Appearance) = appearance.topBar.timeSide
        override fun set(appearance: Appearance, value: Side) =
            appearance.copy(topBar = appearance.topBar.copy(timeSide = value))
        override fun appliesTo(appearance: Appearance) = appearance.topBar.showTime && appearance.topBar.showDate
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
 * [appearance] with this option at its next value ([forward]) or its previous one, around: after
 * the last value comes the first, and before the first the last.
 */
fun <T> AppearanceOption<T>.step(appearance: Appearance, forward: Boolean): Appearance {
    val index = values.indexOf(get(appearance))
    if (index < 0) return set(appearance, default)
    val next = (index + if (forward) 1 else -1).mod(values.size)
    return set(appearance, values[next])
}
