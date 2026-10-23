package com.luncher.domain.appearance

/** When an app's name shows on the home screen, centered below its tile. */
enum class AppName {
    /** Never: the banner says which app it is. */
    OFF,

    /** Below the focused tile only, coming and going with the focus. */
    WHEN_FOCUSED,

    /** Below every tile, at all times; the focused one's zoomed with its tile. */
    ALWAYS,
}
