package com.luncher.domain.appearance

/** [AppearanceSettings] in memory; [appearance] is what's stored, and what the test checks after a write. */
class FakeAppearanceSettings(var appearance: Appearance = Appearance()) : AppearanceSettings {

    private val listeners = mutableListOf<() -> Unit>()

    /** How many listeners are registered: 0 while nothing shows the appearance. */
    val listenerCount: Int get() = listeners.size

    override fun read(): Appearance = appearance

    override fun write(appearance: Appearance) {
        this.appearance = appearance
        listeners.toList().forEach { it() }
    }

    override fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    override fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }
}
