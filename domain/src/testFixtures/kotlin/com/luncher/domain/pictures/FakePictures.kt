package com.luncher.domain.pictures

import com.luncher.domain.jobs.Cancellable

/**
 * [Pictures] and [PictureImport] in memory: [pictures] is the library, and [results] what each
 * [add] answers, by source, at once ([NotAPicture][ImportResult.NotAPicture] for one it doesn't
 * name). An [ImportResult.Added] picture joins the library, as the real one does. Cancelled
 * before it answers ([answerLater]), it never does, and nothing joins. Each one is copied, as
 * [add]'s `copying` says at once, but those of [refusedAtOnce], refused for their name or size
 * before any copying.
 */
class FakePictures<Source>(
    vararg pictures: Picture,
    val results: MutableMap<Source, ImportResult> = mutableMapOf(),
) : Pictures, PictureImport<Source> {

    val pictures: MutableList<Picture> = pictures.toMutableList()

    /** Every source [add] was given, in order. */
    val added = mutableListOf<Source>()

    /** The sources answered from what they say of their name and size, without copying anything. */
    val refusedAtOnce = mutableSetOf<Source>()

    /** Every source whose [add] was cancelled before it answered, in order. */
    val cancelled = mutableListOf<Source>()

    /** When set, [add] keeps its answer here instead of giving it, for the test to give with [finishAdding]. */
    var answerLater = false
    private var pending: (() -> Unit)? = null

    /** Whether an [add] waits for [finishAdding]. */
    val adding: Boolean get() = pending != null

    private val listeners = mutableListOf<() -> Unit>()

    /** How many listeners are registered: 0 while no screen shows the library. */
    val listenerCount: Int get() = listeners.size

    override fun read(): List<Picture> = pictures.toList()

    override fun delete(ids: Set<PictureId>) {
        pictures.removeAll { it.id in ids }
        changed()
    }

    override fun add(source: Source, copying: () -> Unit, done: (ImportResult) -> Unit): Cancellable {
        added += source
        if (source !in refusedAtOnce) copying()
        val result = results[source] ?: ImportResult.NotAPicture
        val answer = {
            if (result is ImportResult.Added && pictures.none { it.id == result.picture.id }) {
                pictures += result.picture
                changed()
            }
            done(result)
        }
        if (!answerLater) {
            answer()
            return Cancellable {}
        }
        pending = answer
        return Cancellable {
            if (pending === answer) {
                pending = null
                cancelled += source
            }
        }
    }

    /** Gives the answer [add] kept, with [answerLater]. */
    fun finishAdding() {
        val answer = checkNotNull(pending) { "nothing is being added" }
        pending = null
        answer()
    }

    override fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    override fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    private fun changed() = listeners.toList().forEach { it() }

    companion object {
        /** A picture named [name], 1920 x 1080 px unless said, added at [added], its id from its name. */
        fun picture(name: String, added: Long = 0, width: Int = 1920, height: Int = 1080, bytes: Long = 1_000_000) =
            Picture(PictureId("id-$name"), name, width, height, bytes, added)
    }
}
