package com.luncher.domain.pictures

/**
 * A picture of the library chosen for something, or none, and how it's framed into that thing's
 * space: what the home screen's background is, or an app's tile. Beside them a choice may hold
 * values of two kinds: values that go with its picture, set for it, as the background's dim and
 * blur, and values of the thing itself, which no picture changes, as a tile's look. The rules of
 * choosing one and of the framing it offers are written once, below, for every such choice; each
 * says, as [P], what it is with another framing, or another picture.
 */
interface PictureChoice<P : PictureChoice<P>> {

    /** The picture; null for none, as when it was deleted. */
    val picture: PictureId?

    /** How [picture] is put into the space. */
    val framing: Framing

    /** This choice with [framing], everything else as it is. */
    fun framed(framing: Framing): P

    /**
     * [picture] chosen with [framing], what goes with a picture as by default, and the thing's own
     * values kept: what another picture chosen starts as.
     */
    fun starting(picture: PictureId?, framing: Framing): P

    /**
     * [stored]'s picture with what goes with it as [stored] has it, and the thing's own values
     * kept as in this choice: what the stored picture chosen again comes back as.
     */
    fun restoring(stored: P): P
}

/**
 * This choice with [picture] chosen in it: [stored]'s, if that's its picture, so the stored one
 * comes back as it's saved ([PictureChoice.restoring]); else the picture in the middle and what
 * goes with it as by default ([PictureChoice.starting]), so another picture shows as it is, and its
 * options are set for it. The fit is kept, as in [offeredIn], and so are the thing's own values.
 * The picture it has already changes nothing.
 */
fun <P : PictureChoice<P>> P.choosing(picture: PictureId?, stored: P): P = when (picture) {
    this.picture -> this
    stored.picture -> restoring(stored)
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
