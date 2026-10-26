package com.luncher.domain.apps

/**
 * Port: an app's own image ([ownBanner]), drawn at the size a screen shows it. [Image] is the UI's
 * own image type (a Bitmap on Android), which this module can't name; the screens get the port
 * with it filled in, so they don't depend on the adapter that draws it. A picture of the library
 * on a tile isn't drawn here: it's prepared ahead, and read ([PreparedTileImages]).
 */
interface AppImages<Image> {

    /** [app]'s own [banner], drawn [width] x [height] px. */
    fun render(app: HomeApp, banner: Banner.Own, width: Int, height: Int): Image
}
