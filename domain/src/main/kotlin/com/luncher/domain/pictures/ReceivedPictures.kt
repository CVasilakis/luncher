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
 * Used on the main thread only, where [import] answers.
 */
class ReceivedPictures<Source>(private val import: PictureImport<Source>) {

    /** How a session went, once every picture of it has its answer. */
    sealed interface Outcome {

        /** One picture, and no share refused: the window says what became of it, as before there were sessions. */
        data class One(val result: ImportResult) : Outcome

        /**
         * Several pictures, or a share refused: how many are in the library now ([saved], those
         * it had already too), how many [notAdded], and whether a share had [tooMany].
         */
        data class Several(val saved: Int, val notAdded: Int, val tooMany: Boolean) : Outcome
    }

    private class Entry<Source>(val source: Source) {
        var result: ImportResult? = null
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
    val pending: List<Source> get() = entries.filter { it.result == null }.map { it.source }

    /**
     * Whether the picture being added is being copied: past what its name and size decide, real
     * work, which the window shows as a wait. False while it's checked, and for one refused at once.
     */
    var copying: Boolean = false
        private set

    /** How many of them have their answer. */
    val answered: Int get() = entries.count { it.result != null }

    /** How the session went, once every picture has its answer; null while one is being added, or with no session. */
    val outcome: Outcome?
        get() {
            if (session == 0 || answered < total) return null
            if (total == 1 && !refused) return Outcome.One(entries[0].result!!)
            val saved = entries.count { it.result is ImportResult.Added || it.result is ImportResult.AlreadyThere }
            return Outcome.Several(saved, total - saved, refused)
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
            val entry = entries.firstOrNull { it.result == null } ?: return
            val started = session
            var waiting = false
            val handle = import.add(entry.source, copying = {
                if (started == session && entry.result == null && !copying) {
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
            waiting = entry.result == null
            if (waiting) adding = handle
        }
    }

    private fun changed() = listeners.toList().forEach { it() }
}
