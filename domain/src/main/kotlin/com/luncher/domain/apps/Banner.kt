package com.luncher.domain.apps

/** Which image the home screen shows for an app. :app turns it into a picture of the tile's size. */
sealed interface Banner {

    /** The TV banner the app declares. */
    data object AppBanner : Banner

    /** No banner: the app's icon and label on a plain card. */
    data object AppIcon : Banner
}

/** The image to show for [app]: its own banner when it has one, otherwise its icon and label. */
fun bannerFor(app: InstalledApp): Banner = if (app.hasBanner) Banner.AppBanner else Banner.AppIcon
