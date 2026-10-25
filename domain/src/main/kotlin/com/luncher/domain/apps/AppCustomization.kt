package com.luncher.domain.apps

/**
 * What the user set for one app, as stored: its [name], and its tile's [image]. Each value is null
 * while the user hasn't set it, and the app shows as the device reports it there ([HomeApp]).
 * Kept apart from the [AppArrangement], in a store of its own ([AppCustomizations]).
 */
data class AppCustomization(
    /** The name the user gave the app, shown wherever the home screen names it; null: its own label. */
    val name: String? = null,
    /** The picture the app's tile shows, always with a picture; null: the app's own banner. */
    val image: TileImage? = null,
) {
    companion object {
        /** Nothing set: the app as the device reports it. */
        val NONE = AppCustomization()
    }
}
