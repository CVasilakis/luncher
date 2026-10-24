package com.luncher.domain.apps

/** Which image the home screen shows for an app. [AppImages] draws it at the tile's size. */
sealed interface Banner {

    /** The TV banner the app declares. */
    data object AppBanner : Banner

    /** No banner: the app's icon and name (the one the home screen shows) on a plain card. */
    data object AppIcon : Banner
}

/** The image to show for [app]: its own banner when it has one, otherwise its icon and name. */
fun bannerFor(app: HomeApp): Banner = if (app.installed.hasBanner) Banner.AppBanner else Banner.AppIcon
