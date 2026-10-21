package com.luncher.domain.appearance

/** How the focused tile stands out from the others. */
enum class FocusStyle {
    /** A frame in the accent color around it, and a slight zoom. */
    FRAME,

    /** No frame: a bigger zoom, and a soft shadow under it, as if it were lifted off the screen. */
    LIFT,
}
