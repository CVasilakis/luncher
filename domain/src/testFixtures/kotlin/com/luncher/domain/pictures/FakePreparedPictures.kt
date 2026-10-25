package com.luncher.domain.pictures

/**
 * [PreparedPictures] in memory: every picture is prepared but those in [missing], for [cause], which
 * [prepare] prepares; those in [failing] it can't.
 */
class FakePreparedPictures(vararg missing: PictureId) : PreparedPictures {

    val missing: MutableSet<PictureId> = missing.toMutableSet()

    /** Why the [missing] pictures aren't prepared. */
    var cause = PreparingCause.OUTDATED

    /** Pictures [prepare] can't prepare (their file gone, a full disk). */
    val failing: MutableSet<PictureId> = mutableSetOf()

    /** Every picture [prepare] prepared, in order. */
    val prepared = mutableListOf<PictureId>()

    /** How many times a picture was asked about ([whyPrepare]). */
    var asked = 0
        private set

    override fun whyPrepare(picture: PictureId): PreparingCause? {
        asked++
        return if (picture in missing) cause else null
    }

    override fun prepare(picture: PictureId, stopped: () -> Boolean): Boolean {
        if (stopped() || picture in failing) return false
        if (missing.remove(picture)) prepared += picture
        return true
    }
}
