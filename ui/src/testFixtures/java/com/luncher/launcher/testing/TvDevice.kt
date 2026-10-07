package com.luncher.launcher.testing

/**
 * Robolectric qualifiers for the device Luncher targets: a 1080p TV (960x540 dp at xhdpi, like
 * the tv_1080p emulator profile), landscape, TV UI mode, no touchscreen, D-pad navigation.
 * Use with `@Config(qualifiers = TV_1080P)`.
 */
const val TV_1080P = "w960dp-h540dp-land-television-xhdpi-notouch-dpad"

/** A screen to check a layout on: its Robolectric qualifiers, and the user's font scale. */
class TvScreen(private val name: String, val qualifiers: String, val fontScale: Float = 1f) {
    override fun toString() = name   // the parameterized tests' names
}

private fun tv(width: Int, height: Int, density: String) = "w${width}dp-h${height}dp-land-television-$density-notouch-dpad"

/**
 * The screens Luncher's layouts must work on (`*LayoutTest`): TVs at their usual sizes, other
 * shapes, the lower densities some TV boxes are set to, and large text.
 */
val TV_SCREENS = listOf(
    TvScreen("1080p", TV_1080P),
    TvScreen("720p", tv(960, 540, "tvdpi")),
    TvScreen("720p at 320 dpi", tv(640, 360, "xhdpi")),
    TvScreen("4 by 3", tv(720, 540, "xhdpi")),
    TvScreen("16 by 10", tv(864, 540, "xhdpi")),
    TvScreen("21 by 9", tv(1260, 540, "xhdpi")),
    TvScreen("1080p at 240 dpi", tv(1280, 720, "hdpi")),
    TvScreen("1080p at 160 dpi", tv(1920, 1080, "mdpi")),
    // Android TV's largest text size. From API 34 on Android grows large text less than small:
    // 32 sp stays about 32 dp, where API 22 to 33 make it 41.6 dp. So the *LayoutTests run on
    // API 33 as well as 36.
    TvScreen("1080p, large text", TV_1080P, fontScale = 1.3f),
)
