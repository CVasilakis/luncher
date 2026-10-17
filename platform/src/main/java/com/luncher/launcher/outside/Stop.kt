package com.luncher.launcher.outside

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tells work that waits on another app to give up: set once, from any thread (a Cancel, on the
 * main thread), seen by the job that waits ([OutsideCalls], [OutsideReader]). Setting it again
 * does nothing.
 */
class Stop {

    private val stopped = AtomicBoolean()
    private val actions = CopyOnWriteArrayList<() -> Unit>()

    val isStopped: Boolean get() = stopped.get()

    fun stop() {
        // Each action runs where it's removed, here or in whenStopped, so once, though both race.
        if (stopped.compareAndSet(false, true)) actions.forEach { if (actions.remove(it)) it() }
    }

    /**
     * Runs [action] once when it's set, on the thread that sets it, or at once if it is: something
     * quick that wakes a wait. The handle removes it.
     */
    internal fun whenStopped(action: () -> Unit): () -> Unit {
        actions += action
        if (isStopped && actions.remove(action)) action()
        return { actions.remove(action) }
    }
}
