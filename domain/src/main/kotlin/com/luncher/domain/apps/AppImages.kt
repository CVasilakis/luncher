package com.luncher.domain.apps

/**
 * Port: an app's image, the one [bannerFor] chose, drawn at the size a screen shows it. [Image] is
 * the UI's own image type (a Bitmap on Android), which this module can't name; the screens get the
 * port with it filled in, so they don't depend on the adapter that draws it.
 */
interface AppImages<Image> {

    /** [app]'s [banner], drawn [width] x [height] px. */
    fun render(app: InstalledApp, banner: Banner, width: Int, height: Int): Image
}
