package com.luncher.domain.appearance

import com.luncher.domain.options.Option
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.PictureCut
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.fitApplies
import com.luncher.domain.pictures.offeredPositions
import com.luncher.domain.pictures.positionApplies
import com.luncher.domain.appearance.AccentColor as Accent
import com.luncher.domain.appearance.AppName as Name
import com.luncher.domain.appearance.HorizontalAlignment as Horizontal
import com.luncher.domain.appearance.ThemeMode as Mode
import com.luncher.domain.appearance.VerticalAlignment as Vertical

/**
 * Something the user can change about how the home screen looks: the appearance's catalog of
 * [Option]s, over the [Appearance], each a type of its own, so every layer that handles them
 * (storage, labels) does so in an exhaustive `when`, and a new option doesn't compile until each
 * one handles it (docs/ARCHITECTURE.md#appearance). Which options the editor lists, in which
 * groups: [appearanceOptions]. Which of its values change anything may also depend on what isn't
 * in the appearance: an [AppearanceContext].
 */
sealed interface AppearanceOption<T> : Option<Appearance, AppearanceContext, T> {

    /** The option's value in the home screen as it looks without settings. */
    override val default: T get() = get(Appearance())

    /**
     * Whether the option changes what the home screen draws, and the editors' preview of it; false
     * for one that changes only the menus and the settings (the theme's mode).
     */
    val showsOnHome: Boolean get() = true

    /** Where the rows of tiles go in the height. */
    data object VerticalAlignment : AppearanceOption<Vertical> {
        override val values: List<Vertical> = Vertical.entries
        override fun get(model: Appearance) = model.tileGeometry.verticalAlignment
        override fun set(model: Appearance, value: Vertical) =
            model.copy(tileGeometry = model.tileGeometry.copy(verticalAlignment = value))
    }

    /** Where a row of tiles that isn't full goes in the width. */
    data object HorizontalAlignment : AppearanceOption<Horizontal> {
        override val values: List<Horizontal> = Horizontal.entries
        override fun get(model: Appearance) = model.tileGeometry.horizontalAlignment
        override fun set(model: Appearance, value: Horizontal) =
            model.copy(tileGeometry = model.tileGeometry.copy(horizontalAlignment = value))
    }

    /** With the rows centered or at the bottom, whether the bottom row is always full. */
    data object FullBottomRow : AppearanceOption<Boolean> {
        override val values: List<Boolean> = listOf(false, true)
        override fun get(model: Appearance) = model.tileGeometry.fullBottomRow
        override fun set(model: Appearance, value: Boolean) =
            model.copy(tileGeometry = model.tileGeometry.copy(fullBottomRow = value))
        override fun appliesTo(model: Appearance, context: AppearanceContext) = model.tileGeometry.verticalAlignment != Vertical.TOP
    }

    /** How round the tiles' corners are: radii in dp, then round ends. */
    data object CornerRadius : AppearanceOption<Corners> {
        override val values: List<Corners> = listOf(0, 4, 8, 12, 16, 24, 32).map { Corners.Radius(it) } + Corners.Pill
        override fun get(model: Appearance) = model.tileStyle.corners
        override fun set(model: Appearance, value: Corners) =
            model.copy(tileStyle = model.tileStyle.copy(corners = value))
    }

    /** Whether the focused tile is framed; without a frame, it's lifted with a shadow. */
    data object FocusFrame : AppearanceOption<Boolean> {
        override val values: List<Boolean> = listOf(false, true)
        override fun get(model: Appearance) = model.tileStyle.focusFrame
        override fun set(model: Appearance, value: Boolean) =
            model.copy(tileStyle = model.tileStyle.copy(focusFrame = value))
    }

    /** Whether the app names show below the tiles: never, the focused one's, or every one. */
    data object AppName : AppearanceOption<Name> {
        override val values: List<Name> = Name.entries
        override fun get(model: Appearance) = model.tileGeometry.appName
        override fun set(model: Appearance, value: Name) =
            model.copy(tileGeometry = model.tileGeometry.copy(appName = value))
    }

    /** Whether the top bar shows the time. */
    data object ShowTime : AppearanceOption<Boolean> {
        override val values: List<Boolean> = listOf(true, false)
        override fun get(model: Appearance) = model.topBar.showTime
        override fun set(model: Appearance, value: Boolean) =
            model.copy(topBar = model.topBar.copy(showTime = value))
    }

    /** Whether the top bar shows the date. */
    data object ShowDate : AppearanceOption<Boolean> {
        override val values: List<Boolean> = listOf(true, false)
        override fun get(model: Appearance) = model.topBar.showDate
        override fun set(model: Appearance, value: Boolean) =
            model.copy(topBar = model.topBar.copy(showDate = value))
    }

    /** The side of the top bar the time and the date are on, while either shows. */
    data object ClockSide : AppearanceOption<Side> {
        override val values: List<Side> = Side.entries
        override fun get(model: Appearance) = model.topBar.clockSide
        override fun set(model: Appearance, value: Side) =
            model.copy(topBar = model.topBar.copy(clockSide = value))
        override fun appliesTo(model: Appearance, context: AppearanceContext) = model.topBar.showTime || model.topBar.showDate
    }

    /** The side of the date the time is on, while both show. */
    data object TimeSide : AppearanceOption<Side> {
        override val values: List<Side> = Side.entries
        override fun get(model: Appearance) = model.topBar.timeSide
        override fun set(model: Appearance, value: Side) =
            model.copy(topBar = model.topBar.copy(timeSide = value))
        override fun appliesTo(model: Appearance, context: AppearanceContext) = model.topBar.showTime && model.topBar.showDate
    }

    /** The accent color of the menus and settings, and of the home screen's focus frames. */
    data object AccentColor : AppearanceOption<Accent> {
        override val values: List<Accent> = Accent.entries
        override fun get(model: Appearance) = model.theme.accent
        override fun set(model: Appearance, value: Accent) =
            model.copy(theme = model.theme.copy(accent = value))
    }

    /** Whether the menus and settings are dark or light; the home screen stays as it is. */
    data object ThemeMode : AppearanceOption<Mode> {
        override val values: List<Mode> = Mode.entries
        override fun get(model: Appearance) = model.theme.mode
        override fun set(model: Appearance, value: Mode) =
            model.copy(theme = model.theme.copy(mode = value))
        override val showsOnHome: Boolean get() = false
    }

    /**
     * How the background's picture fills the screen, while there's one of another shape: filling or
     * stretched, one of the screen's shape looks the same.
     */
    data object BackgroundFit : AppearanceOption<Fit> {
        override val values: List<Fit> = Fit.entries
        override fun get(model: Appearance) = model.background.framing.fit
        override fun set(model: Appearance, value: Fit) =
            model.copy(background = model.background.copy(framing = model.background.framing.copy(fit = value)))
        override fun appliesTo(model: Appearance, context: AppearanceContext) = model.background.fitApplies(context.backgroundCut)
    }

    /**
     * Which part of the background's picture shows, while it fills the screen and is cut: the
     * middle or an end of the side cut, left or right of a wide picture, top or bottom of a tall one.
     */
    data object BackgroundPosition : AppearanceOption<PicturePosition> {
        override val values: List<PicturePosition> = PicturePosition.entries
        override fun get(model: Appearance) = model.background.framing.position
        override fun set(model: Appearance, value: PicturePosition) =
            model.copy(background = model.background.copy(framing = model.background.framing.copy(position = value)))
        override fun appliesTo(model: Appearance, context: AppearanceContext) = model.background.positionApplies(context.backgroundCut)
        override fun values(context: AppearanceContext) = offeredPositions(context.backgroundCut)
    }

    /** How much darker the background's picture is drawn, in percent, while there's one. */
    data object BackgroundDim : AppearanceOption<Int> {
        override val values: List<Int> = listOf(0, 20, 40, 60)
        override fun get(model: Appearance) = model.background.dim
        override fun set(model: Appearance, value: Int) =
            model.copy(background = model.background.copy(dim = value))
        override fun appliesTo(model: Appearance, context: AppearanceContext) = model.background.picture != null
    }

    /** How blurred the background's picture is drawn, while there's one. */
    data object BackgroundBlur : AppearanceOption<Blur> {
        override val values: List<Blur> = Blur.entries
        override fun get(model: Appearance) = model.background.blur
        override fun set(model: Appearance, value: Blur) =
            model.copy(background = model.background.copy(blur = value))
        override fun appliesTo(model: Appearance, context: AppearanceContext) = model.background.picture != null
    }
}

/**
 * What decides, besides the [Appearance], which values of its options change anything: facts of
 * the device and the user's things, which no option sets, such as the shape of the background's
 * picture on this screen. Each fact is null while it isn't known, which offers every value, as for
 * the options it doesn't concern; a fact added later is one more field, which leaves every place
 * that doesn't know it as it is.
 */
data class AppearanceContext(
    /** What filling the screen cuts from the background's picture ([pictureCut][com.luncher.domain.pictures.pictureCut]). */
    val backgroundCut: PictureCut? = null,
) {
    companion object {
        /** Nothing known: every value of every option offered. */
        val UNKNOWN = AppearanceContext()
    }
}
