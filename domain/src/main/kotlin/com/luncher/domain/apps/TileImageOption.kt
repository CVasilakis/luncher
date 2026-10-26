package com.luncher.domain.apps

import com.luncher.domain.layout.TileShape
import com.luncher.domain.options.Option
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.PictureCut
import com.luncher.domain.pictures.PicturePosition
import com.luncher.domain.pictures.fitApplies
import com.luncher.domain.pictures.offeredPositions
import com.luncher.domain.pictures.pictureCut
import com.luncher.domain.pictures.positionApplies

/**
 * What the user can change of a tile's picture ([TileImage]), in the picker that chooses it: the
 * tile image's catalog of options (docs/ARCHITECTURE.md#appearance), in the order it shows them.
 * Each one offers only what moves the picture in the tile's shape ([TileImageContext]), as the
 * background's do on the screen; the look's apply only to a see-through picture.
 */
sealed interface TileImageOption<T> : Option<TileImage, TileImageContext, T> {

    /** As a tile's first picture starts: filling the tile, its middle showing, on light glass, the focus following the tile. */
    override val default: T get() = get(TileImage(null))

    /** How the picture fills the tile: covering it, cut, or stretched to its shape, whole. */
    data object TileFit : TileImageOption<Fit> {
        override val values: List<Fit> = Fit.entries
        override fun get(model: TileImage) = model.framing.fit
        override fun set(model: TileImage, value: Fit) = model.framed(model.framing.copy(fit = value))
        override fun appliesTo(model: TileImage, context: TileImageContext) = model.fitApplies(context.cut)
    }

    /**
     * Which part of the picture shows, while it fills the tile and is cut: the middle or an end of
     * the side cut, left or right of a picture wider than a tile, top or bottom of a taller one.
     */
    data object TilePosition : TileImageOption<PicturePosition> {
        override val values: List<PicturePosition> = PicturePosition.entries
        override fun get(model: TileImage) = model.framing.position
        override fun set(model: TileImage, value: PicturePosition) = model.framed(model.framing.copy(position = value))
        override fun appliesTo(model: TileImage, context: TileImageContext) = model.positionApplies(context.cut)
        override fun values(context: TileImageContext) = offeredPositions(context.cut)
    }

    /** What's behind a see-through picture on the tile ([TileLook.backdrop]). */
    data object Backdrop : TileImageOption<TileBackdrop> {
        override val values: List<TileBackdrop> = TileBackdrop.entries
        override fun get(model: TileImage) = model.look.backdrop
        override fun set(model: TileImage, value: TileBackdrop) = model.copy(look = model.look.copy(backdrop = value))
        override fun appliesTo(model: TileImage, context: TileImageContext) = model.lookApplies(context)
    }

    /** What the focus frame, or the lifted tile's shadow, follows on a see-through picture's tile ([TileLook.focus]). */
    data object Focus : TileImageOption<TileFocusShape> {
        override val values: List<TileFocusShape> = TileFocusShape.entries
        override fun get(model: TileImage) = model.look.focus
        override fun set(model: TileImage, value: TileFocusShape) = model.copy(look = model.look.copy(focus = value))
        override fun appliesTo(model: TileImage, context: TileImageContext) = model.lookApplies(context)
    }
}

/** The tile image's options, in the order the picker shows them, two a row: the fit, the position, the backdrop, the focus. */
val tileImageOptions: List<TileImageOption<*>> = listOf(
    TileImageOption.TileFit,
    TileImageOption.TilePosition,
    TileImageOption.Backdrop,
    TileImageOption.Focus,
)

/**
 * What decides, besides the [TileImage], which of its options and values change anything: what
 * filling a tile cuts from its picture ([pictureCut], against [TileShape]), null while it isn't
 * known (no picture), which offers every value; and whether the picture is see-through, which the
 * look needs ([TileImage.lookApplies]). The tile image's own, as the appearance has its
 * [AppearanceContext][com.luncher.domain.appearance.AppearanceContext].
 */
data class TileImageContext(
    val cut: PictureCut? = null,
    /** Whether the picture has see-through parts; false where that isn't known, as a picture not known to be counts as opaque ([Picture.seeThrough][com.luncher.domain.pictures.Picture.seeThrough]). */
    val seeThrough: Boolean = false,
) {
    companion object {
        /** Nothing known: every value of every option offered, the look not applying. */
        val UNKNOWN = TileImageContext()

        /** For a [pictureWidth] x [pictureHeight] picture filling a tile, [seeThrough] or not. */
        fun of(pictureWidth: Int, pictureHeight: Int, seeThrough: Boolean = false) =
            TileImageContext(pictureCut(pictureWidth, pictureHeight, TileShape.WIDTH, TileShape.HEIGHT), seeThrough)
    }
}
