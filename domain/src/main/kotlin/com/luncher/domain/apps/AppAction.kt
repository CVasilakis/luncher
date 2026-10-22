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
}

/**
 * The actions the menu of [app], one of [arranged]'s shown apps, offers, in its order. Only those
 * that change something: with no other app shown, there's nowhere to move it.
 */
fun appActions(arranged: ArrangedApps, app: InstalledApp): List<AppAction> {
    require(app in arranged.shown) { "not shown: ${app.launchable}" }
    return if (arranged.shown.size > 1) listOf(AppAction.Move, AppAction.Hide) else listOf(AppAction.Hide)
}
