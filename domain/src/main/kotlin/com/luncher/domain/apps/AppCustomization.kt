package com.luncher.domain.apps

/**
 * What the user set for one app, as stored: today its [name]; later its own picture for its tile.
 * Each value is null while the user hasn't set it, and the app shows as the device reports it
 * there ([HomeApp]). Kept apart from the [AppArrangement], in a store of its own
 * ([AppCustomizations]).
 */
data class AppCustomization(
    /** The name the user gave the app, shown wherever the home screen names it; null: its own label. */
    val name: String? = null,
) {
    companion object {
        /** Nothing set: the app as the device reports it. */
        val NONE = AppCustomization()
    }
}
