package com.luncher.domain.pictures

import com.luncher.domain.jobs.Cancellable

/**
 * The pictures other apps shared, added to the library one at a time, which the window after a
 * share shows: how far it got while they're added, then how it went. One per process (AppGraph
 * makes it), so a window recreated by a configuration change finds them where they were, and
 * watched like a port (docs/ARCHITECTURE.md#background-work-and-changes-while-shown).
 *
 * The pictures received while a window shows them are one session: a share that arrives
 * meanwhile joins it, its pictures added after the others, and the end says how all of them
 * went. The window that took the session last holds it ([receive], [refuse], [hold] return its
 * hold) and [end]s it, when the user is done with it or leaves it; a window that doesn't hold it
 * any more can't: neither one that ends late, after a new session started, nor an older window
 * Android destroys late, after a new one took this session over (on API 29, Android closes
 * Luncher's task when another app's opens, and a share then comes to a new window, before the
 * old one is destroyed).
 *
 * A share of more than [PictureLimits.MAX_SHARED] pictures adds none ([refuse]), so the user can
 * share again the ones they want.
 *
 * Adding can be [stop]ped while the session goes on: the pictures still to add are cancelled, and
 * the end says how many, after how the others went.
 *
 * Add from folder's Add all adds its pictures through a session too, of an instance of its own,
 * which its screen holds only while it adds.
 *
 * Used on the main thread only, where [import] answers.
 */
class ReceivedPictures<Source>(private val import: PictureImport<Source>) {

    /** How a session went, once every picture of it has its answer. */
    sealed interface Outcome {

        /** One picture, and no share refused: the window says what became of it, as before there were sessions. */
        data class One(val result: ImportResult) : Outcome

        /**
         * Several pictures, a share refused, or adding stopped: each answer, in the order the
         * pictures came ([results]), how many were [cancelled] by [stop], and whether a share had
         * [tooMany].
         */
        data class Several(val results: List<ImportResult>, val cancelled: Int = 0, val tooMany: Boolean = false) : Outcome {

            /** How many were added to the library now. */
            val added: Int get() = results.count { it is ImportResult.Added }
        }
    }

    private class Entry<Source>(val source: Source) {
        var result: ImportResult? = null

        /** Cancelled by [stop] before its answer: never added. */
        var cancelled = false

        val answered get() = result != null || cancelled
    }

    private val entries = mutableListOf<Entry<Source>>()
    private var refused = false
    private var adding: Cancellable? = null
    private val listeners = mutableListOf<() -> Unit>()

    /** The session open, or 0 for none; each new one is numbered after the last. */
    var session: Int = 0
        private set
    private var sessions = 0

    /** The hold of the window that took the session last, the one that may [end] it; 0 with no session. */
    var holder: Int = 0
        private set
    private var holds = 0

    /** How many pictures the session has. */
    val total: Int get() = entries.size

    /** The pictures of the session without an answer yet, in order: the one being added first. */
    val pending: List<Source> get() = entries.filter { !it.answered }.map { it.source }

    /**
     * Whether the picture being added is being copied: past what its name and size decide, real
     * work, which the window shows as a wait. False while it's checked, and for one refused at once.
     */
    var copying: Boolean = false
        private set

    /** How many of them have their answer, those [stop] cancelled too. */
    val answered: Int get() = entries.count { it.answered }

    /** How many of them were added to the library so far. */
    val added: Int get() = entries.count { it.result is ImportResult.Added }

    /** How the session went, once every picture has its answer; null while one is being added, or with no session. */
    val outcome: Outcome?
        get() {
            if (session == 0 || answered < total) return null
            val cancelled = entries.count { it.cancelled }
            if (total == 1 && !refused && cancelled == 0) return Outcome.One(entries[0].result!!)
            return Outcome.Several(entries.mapNotNull { it.result }, cancelled, refused)
        }

    /**
     * Adds [sources] to the session, opening one if none is: each after those before it, one at
     * a time, and none the session has already. Returns the caller's hold on the session, for
     * [end].
     */
    fun receive(sources: List<Source>): Int {
        val hold = open()
        for (source in sources) {
            if (entries.none { it.source == source }) entries += Entry(source)
        }
        next()
        changed()
        return hold
    }

    /** A share of more pictures than [PictureLimits.MAX_SHARED]: none is added, and the session says so. Returns the caller's hold, for [end]. */
    fun refuse(): Int {
        val hold = open()
        refused = true
        changed()
        return hold
    }

    /**
     * Stops adding, the session still open, so it can say how it went: the picture being added
     * and those after it are cancelled, never added; those added stay in the library. A share
     * that comes later is added as usual.
     */
    fun stop() {
        if (session == 0) return
        adding?.cancel()
        adding = null
        copying = false
        entries.filter { !it.answered }.forEach { it.cancelled = true }
        changed()
    }

    /** Takes the open session over, as a window that shows it without receiving anything does; 0 with none. */
    fun hold(): Int {
        if (session == 0) return 0
        holder = ++holds
        return holder
    }

    /**
     * Ends the session, if [hold] still holds it: the picture being added and those after it are
     * cancelled, never added; those added stay in the library. Nothing happens otherwise.
     */
    fun end(hold: Int) {
        if (hold == 0 || hold != holder || session == 0) return
        adding?.cancel()
        adding = null
        copying = false
        entries.clear()
        refused = false
        session = 0
        holder = 0
        changed()
    }

    /** Calls [listener] on the main thread whenever the session changes: a picture's answer, more received, its end. */
    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    /** Opens a session if none is, and gives the caller the hold on it. */
    private fun open(): Int {
        if (session == 0) session = ++sessions
        holder = ++holds
        return holder
    }

    /**
     * Starts adding the first picture without an answer, unless one is being added. An import that
     * answers at once (a fake's) goes on to the next in the loop, rather than nesting a call per
     * picture.
     */
    private fun next() {
        while (adding == null) {
            val entry = entries.firstOrNull { !it.answered } ?: return
            val started = session
            var waiting = false
            val handle = import.add(entry.source, copying = {
                if (started == session && !entry.answered && !copying) {
                    copying = true
                    changed()
                }
            }) { result ->
                if (started != session) return@add
                entry.result = result
                copying = false
                if (!waiting) return@add
                adding = null
                next()
                changed()
            }
            waiting = !entry.answered
            if (waiting) adding = handle
        }
    }

    private fun changed() = listeners.toList().forEach { it() }
}
