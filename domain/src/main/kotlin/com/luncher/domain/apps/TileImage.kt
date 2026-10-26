package com.luncher.domain.apps

import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureChoice
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.offeredIn

/**
 * A picture of the library the user chose for an app's tile, how it fills the tile, and how the
 * tile shows it if it's see-through ([look]), in place of the app's own banner ([bannerFor]).
 * What's stored for the app ([AppCustomization.image]) always has a [picture]
 * ([ArrangedApps.withImage] stores one without as none); without one it's a draft's "the app's own
 * banner", its framing and look kept for the next picture chosen.
 */
data class TileImage(
    override val picture: PictureId?,
    override val framing: Framing = Framing(),
    /** The tile's own, kept whichever picture is chosen; it changes only a see-through picture's tile ([lookApplies]). */
    val look: TileLook = TileLook(),
) : PictureChoice<TileImage> {

    override fun framed(framing: Framing): TileImage = copy(framing = framing)

    /** [picture] with [framing], the tile's [look] kept. */
    override fun starting(picture: PictureId?, framing: Framing): TileImage = TileImage(picture, framing, look)

    /** [stored]'s picture and framing, the tile's [look] as the draft has it. */
    override fun restoring(stored: TileImage): TileImage = stored.copy(look = look)

    /** This image with only a framing that changes anything in [context]'s tile ([offeredIn]); the look as it is. */
    fun offeredIn(context: TileImageContext): TileImage = offeredIn(context.cut)

    /** Whether the [look] changes anything in [context]: only for a picture that's see-through. */
    fun lookApplies(context: TileImageContext): Boolean = picture != null && context.seeThrough

    /**
     * This image as a picker's Save counts it, in [context]: what [offeredIn] offers, and the look
     * only where it changes anything ([lookApplies]), else the default. So a look changed for the
     * app's own banner, or an opaque picture, is no change, though a draft keeps it for the next
     * picture.
     */
    fun counted(context: TileImageContext): TileImage {
        val offered = offeredIn(context)
        return if (lookApplies(context)) offered else offered.copy(look = TileLook())
    }
}
