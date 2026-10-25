package com.luncher.domain.apps

/**
 * Something the user can do to one app from its tile, in the menu a long press of OK opens. Each
 * kind is a type of its own, so :feature:home handles every one of them (its label, what OK on it
 * does) in an exhaustive `when`: a new action that it doesn't handle yet doesn't compile.
 */
sealed interface AppAction {

    /** Moves the app to another place among the shown apps. */
    data object Move : AppAction

    /** Hides the app from the home screen; the settings' Hide apps list shows it again. */
    data object Hide : AppAction

    /** Gives the app a name of the user's own, or its own label back ([appName], [ArrangedApps.renamed]). */
    data object Rename : AppAction

    /** Shows a picture of the library on the app's tile, framed as the user says, or its own banner again ([TileImage]). */
    data object TileBanner : AppAction

    /** Opens the app's page in the device's settings ([AppInfoScreen]). */
    data object Info : AppAction
}

/**
 * An entry of a tile's menu: its [action], and whether the user can choose it. One the device
 * can't do ([available] false) is still listed, dimmed, so the menu looks the same on every
 * device and says what's missing; the focus passes it by.
 */
data class AppMenuEntry(val action: AppAction, val available: Boolean = true)

/**
 * The entries the menu of [app], one of [arranged]'s shown apps, lists, in its order: Move, Hide,
 * Rename, Tile banner, App info. Move only where it changes something: with no other app shown, there's nowhere to move
 * it, so it isn't listed. App info is listed always, available where the device has the app's
 * page in its settings ([hasAppInfo], [AppInfoScreen.exists]).
 */
fun appActions(arranged: ArrangedApps, app: HomeApp, hasAppInfo: Boolean): List<AppMenuEntry> {
    require(app in arranged.shown) { "not shown: ${app.launchable}" }
    return listOfNotNull(
        AppMenuEntry(AppAction.Move).takeIf { arranged.shown.size > 1 },
        AppMenuEntry(AppAction.Hide),
        AppMenuEntry(AppAction.Rename),
        AppMenuEntry(AppAction.TileBanner),
        AppMenuEntry(AppAction.Info, available = hasAppInfo),
    )
}
