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
 * background's do on the screen.
 */
sealed interface TileImageOption<T> : Option<TileImage, TileImageContext, T> {

    /** As a picture chosen starts ([TileImage.starting]): filling the tile, its middle showing. */
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
}

/** The tile image's options, in the order the picker shows them: the fit, then the position. */
val tileImageOptions: List<TileImageOption<*>> = listOf(TileImageOption.TileFit, TileImageOption.TilePosition)

/**
 * What decides, besides the [TileImage], which values of its options change anything: what filling
 * a tile cuts from its picture ([pictureCut], against [TileShape]), null while it isn't known (no
 * picture), which offers every value. The tile image's own, as the appearance has its
 * [AppearanceContext][com.luncher.domain.appearance.AppearanceContext].
 */
data class TileImageContext(val cut: PictureCut? = null) {
    companion object {
        /** Nothing known: every value of every option offered. */
        val UNKNOWN = TileImageContext()

        /** For a [pictureWidth] x [pictureHeight] picture filling a tile. */
        fun of(pictureWidth: Int, pictureHeight: Int) =
            TileImageContext(pictureCut(pictureWidth, pictureHeight, TileShape.WIDTH, TileShape.HEIGHT))
    }
}
