package com.luncher.launcher

/**
 * Robolectric qualifiers for the device Luncher targets: a 1080p TV (960x540 dp at xhdpi, like
 * the tv_1080p emulator profile), landscape, TV UI mode, no touchscreen, D-pad navigation.
 * Use with `@Config(qualifiers = TV_1080P)`.
 */
const val TV_1080P = "w960dp-h540dp-land-television-xhdpi-notouch-dpad"
