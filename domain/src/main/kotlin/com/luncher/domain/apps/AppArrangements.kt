package com.luncher.domain.apps

/** Port: where the user's [AppArrangement] is kept. Implemented in :app on top of preferences. */
interface AppArrangements {

    /** The stored arrangement; [AppArrangement.NONE] if there's none yet. */
    fun read(): AppArrangement

    /** Stores [arrangement] in place of the previous one. */
    fun save(arrangement: AppArrangement)
}
