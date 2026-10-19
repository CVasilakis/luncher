package com.luncher.domain.pictures

/** [PreparedPictures] in memory: every picture is prepared but those in [missing], which [prepare] prepares. */
class FakePreparedPictures(vararg missing: PictureId) : PreparedPictures {

    val missing: MutableSet<PictureId> = missing.toMutableSet()

    /** Every picture [prepare] prepared, in order. */
    val prepared = mutableListOf<PictureId>()

    override fun isPrepared(picture: PictureId): Boolean = picture !in missing

    override fun prepare(picture: PictureId, stopped: () -> Boolean): Boolean {
        if (stopped()) return false
        if (missing.remove(picture)) prepared += picture
        return true
    }
}
