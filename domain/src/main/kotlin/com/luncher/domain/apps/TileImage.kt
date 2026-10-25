package com.luncher.domain.apps

import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureChoice
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.offeredIn

/**
 * A picture of the library the user chose for an app's tile, and how it fills the tile, in place
 * of the app's own banner ([bannerFor]). What's stored for the app ([AppCustomization.image])
 * always has a [picture] ([ArrangedApps.withImage] stores one without as none); without one it's
 * a draft's "the app's own banner", its framing kept for the next picture chosen.
 */
data class TileImage(
    override val picture: PictureId?,
    override val framing: Framing = Framing(),
) : PictureChoice<TileImage> {

    override fun framed(framing: Framing): TileImage = copy(framing = framing)

    override fun starting(picture: PictureId?, framing: Framing): TileImage = TileImage(picture, framing)

    /** This image with only a framing that changes anything in [context]'s tile ([offeredIn]). */
    fun offeredIn(context: TileImageContext): TileImage = offeredIn(context.cut)
}
