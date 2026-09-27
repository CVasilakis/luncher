package com.luncher.domain.apps

/** [AppArrangements] in memory; [arrangement] is what's stored, and what the test checks after a save. */
class FakeAppArrangements(var arrangement: AppArrangement = AppArrangement.NONE) : AppArrangements {

    override fun read(): AppArrangement = arrangement

    override fun save(arrangement: AppArrangement) {
        this.arrangement = arrangement
    }
}
