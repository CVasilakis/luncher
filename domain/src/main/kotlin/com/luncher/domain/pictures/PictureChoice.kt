package com.luncher.domain.pictures

/**
 * A picture of the library chosen for something, or none, and how it's framed into that thing's
 * space: what the home screen's background is, with values of its own beside it (its dim and
 * blur). The rules of choosing one and of the framing it offers are written once, below, for every
 * such choice; each says, as [P], what it is with another framing, or another picture.
 */
interface PictureChoice<P : PictureChoice<P>> {

    /** The picture; null for none, as when it was deleted. */
    val picture: PictureId?

    /** How [picture] is put into the space. */
    val framing: Framing

    /** This choice with [framing], everything else as it is. */
    fun framed(framing: Framing): P

    /** [picture] chosen with [framing], everything else as by default: what another picture chosen starts as. */
    fun starting(picture: PictureId?, framing: Framing): P
}

/**
 * This choice with [picture] chosen in it: [stored] if that's its picture, so the stored one comes
 * back as it's saved; else the picture in the middle and everything else as by default
 * ([PictureChoice.starting]), so another picture shows as it is, and its options are set for it.
 * The fit is kept, as in [offeredIn]. The picture it has already changes nothing.
 */
fun <P : PictureChoice<P>> P.choosing(picture: PictureId?, stored: P): P = when (picture) {
    this.picture -> this
    stored.picture -> stored
    else -> starting(picture, Framing(framing.fit))
}

/**
 * This choice with only a framing that changes anything where filling cuts [cut] from its picture
 * ([pictureCut]; null where that isn't known): a position that doesn't move the picture, as the
 * top of a picture cut at the sides, is the middle, which draws the same, so the next change
 * starts there. A fit is kept: its other value draws otherwise as soon as a picture of another
 * shape is chosen.
 */
fun <P : PictureChoice<P>> P.offeredIn(cut: PictureCut?): P =
    if (framing.position in offeredPositions(cut)) this else framed(framing.copy(position = PicturePosition.CENTER))

/** Whether the fit changes anything where filling cuts [cut] from the picture: only for a picture that's cut. */
fun PictureChoice<*>.fitApplies(cut: PictureCut?): Boolean = picture != null && cut != PictureCut.NONE

/** Whether the position changes anything where filling cuts [cut] from the picture: only for one that fills, and is cut. */
fun PictureChoice<*>.positionApplies(cut: PictureCut?): Boolean = fitApplies(cut) && framing.fit == Fit.FILL
