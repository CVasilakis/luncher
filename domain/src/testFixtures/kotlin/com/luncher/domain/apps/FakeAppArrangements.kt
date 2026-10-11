package com.luncher.domain.apps

/** [AppArrangements] in memory; [arrangement] is what's stored, and what the test checks after a save. */
class FakeAppArrangements(var arrangement: AppArrangement = AppArrangement.NONE) : AppArrangements {

    private val listeners = mutableListOf<() -> Unit>()

    /** How many listeners are registered: 0 while nothing shows the apps. */
    val listenerCount: Int get() = listeners.size

    override fun read(): AppArrangement = arrangement

    override fun save(arrangement: AppArrangement) {
        this.arrangement = arrangement
        listeners.toList().forEach { it() }
    }

    override fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    override fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }
}
