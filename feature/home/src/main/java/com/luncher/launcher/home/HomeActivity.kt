package com.luncher.launcher.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.ThemeAppearance
import com.luncher.domain.appearance.colorRoles
import com.luncher.domain.apps.AppAction
import com.luncher.domain.apps.ArrangedApps
import com.luncher.domain.apps.HomeApp
import com.luncher.domain.apps.MAX_APP_NAME
import com.luncher.domain.apps.appActions
import com.luncher.domain.apps.appName
import com.luncher.domain.apps.homeApps
import com.luncher.launcher.settings.R as SettingsR
import com.luncher.launcher.settings.SettingsActivity
import com.luncher.launcher.ui.DialogButton
import com.luncher.launcher.ui.NameDialog
import com.luncher.launcher.ui.color
import com.luncher.launcher.ui.R as UiR
import java.util.Locale

/**
 * The home screen: the time, the date and a settings entry above the TV apps the user didn't hide,
 * as tiles; OK on a tile opens its app, OK on the settings entry (or the Menu key) opens the
 * settings panel. A long press of OK on a tile opens its [TileMenu], to move its app ([MoveMode],
 * which gets every key first while it's on), hide it, rename it, or open its info in the device's
 * settings.
 *
 * How it looks follows the user's [Appearance] while it's shown: each part goes to the view that
 * draws it (the background to the window, [HomeWallpaper]), which compares it with what it has, so
 * an unchanged part costs nothing.
 */
class HomeActivity : Activity() {

    private val installedApps by lazy { graph.installedApps }
    private val arrangements by lazy { graph.appArrangements }
    private val customizations by lazy { graph.appCustomizations }
    private val images by lazy { graph.appImages }
    private val appInfoScreen by lazy { graph.appInfoScreen }
    private val clock by lazy { graph.clock }
    private val appearanceSettings by lazy { graph.appearanceSettings }
    private lateinit var topBar: TopBarView
    private lateinit var tiles: AppTilesView
    private lateinit var clockView: ClockView
    private lateinit var settingsEntry: View
    private lateinit var empty: TextView
    private lateinit var move: MoveMode
    private lateinit var wallpaper: HomeWallpaper

    /** The tiles' menu: made the first time a long press opens it. */
    private var menu: TileMenu? = null

    /** The dialog Rename in a tile's menu opened; null when none was. */
    private var renameDialog: NameDialog? = null

    /** What the tiles show; null until the first [onStart]. */
    private var shown: List<HomeApp>? = null

    /** The apps as last read, shown and hidden: what the tiles' menu acts on. */
    private var arranged: ArrangedApps? = null

    private val showAppearance: () -> Unit = { show(appearanceSettings.read()) }

    /** The theme the screen shows, so an equal one changes nothing; null before the first. */
    private var shownTheme: ThemeAppearance? = null

    // Hidden or shown in the settings, over the home screen, or hidden from a tile's menu: shown at
    // once. Not while moving an app, whose own save this is, with the tiles showing it already.
    private val showArrangement: () -> Unit = { if (!move.active) refresh() }

    /** Apps installed, updated or removed while the user moves an app, read when the mode ends. */
    private var appsChangedWhileMoving = false

    // Installed, updated or removed, or renamed, while shown: shown at once, or, while moving an
    // app, whose tiles are the user's work in progress, once it ends.
    private val showAppChanges: () -> Unit = {
        if (move.active) appsChangedWhileMoving = true else refresh()
    }

    private val openApp = View.OnClickListener { open((it as AppTileView).app) }

    private val openMenu = View.OnLongClickListener {
        val arranged = arranged ?: return@OnLongClickListener false
        val tile = it as AppTileView
        val menu = menu ?: TileMenu(this).also { menu = it }
        menu.style(colorRoles(shownTheme ?: appearanceSettings.read().theme), tiles.tileStyle.corners)
        menu.show(tile, appActions(arranged, tile.app, appInfoScreen.exists())) { action -> act(action, tile) }
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_activity)
        topBar = findViewById(R.id.home_top_bar)
        tiles = findViewById(R.id.home_apps)
        empty = findViewById(R.id.home_empty)
        clockView = findViewById(R.id.home_clock)
        settingsEntry = findViewById(R.id.home_settings)
        settingsEntry.setOnClickListener { openSettings() }
        wallpaper = HomeWallpaper(this, graph.wallpaper, ::showOnPicture)
        move = MoveMode(
            tiles,
            normalBar = listOf(clockView, settingsEntry),
            title = findViewById(R.id.home_move_title),
            hint = findViewById(R.id.home_move_hint),
            arrangements,
            onEnd = ::movingEnded,
        )
        // A home activity must not finish on Back: Back only ends moving an app. From Android 16
        // (API 36) on, Back no longer calls onBackPressed in apps targeting it, nor reaches them
        // as a key, and closes the activity unless a callback takes it; before that,
        // onBackPressed below does.
        if (Build.VERSION.SDK_INT >= 36) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                move.back()
            }
        }
    }

    // Visible from onStart to onStop; paused only, e.g. behind a dialog or the settings panel, it
    // still shows. The appearance, the arrangement, the names and the installed apps, as the clock,
    // only while visible: a change made meanwhile (in the appearance or background editor, which
    // cover the home screen whole) is applied once, when it comes back. The apps are read again
    // here, not as it resumes: started again behind the settings panel, it shows what changed while
    // it was hidden (a picture deleted from the tiles that showed it) at once, not only once the
    // panel closes; and while it's started, the listeners show every change, so resuming needs no
    // read. Moving an app ended as it stopped.
    override fun onStart() {
        super.onStart()
        clockView.start(clock)
        wallpaper.start()
        appearanceSettings.addListener(showAppearance)
        showAppearance()
        arrangements.addListener(showArrangement)
        customizations.addListener(showAppChanges)
        installedApps.addListener(showAppChanges)
        refresh()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the home screen (another app, the screen off) closes the menu, or the rename
        // dialog, and ends moving.
        menu?.dismiss()
        renameDialog?.dismiss()
        move.end()
        clockView.stop()
        wallpaper.stop()
        appearanceSettings.removeListener(showAppearance)
        arrangements.removeListener(showArrangement)
        customizations.removeListener(showAppChanges)
        installedApps.removeListener(showAppChanges)
    }

    // Home while the home screen is in front.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        menu?.dismiss()
        renameDialog?.dismiss()
        move.end()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        move.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)

    /**
     * Reads the apps again: apps may have been installed or removed, hidden or shown in the
     * settings, or renamed, since the home screen was last shown.
     */
    private fun refresh() {
        val arranged = homeApps(installedApps.tvApps(), packageName, arrangements.read(), customizations.read(), Locale.getDefault())
        this.arranged = arranged
        // Unchanged, as on most returns to the home screen: keep the tiles, their images and focus.
        if (arranged.shown != shown) show(arranged.shown)
        showWhyEmpty(arranged)
    }

    private fun show(appearance: Appearance) {
        tiles.tileGeometry = appearance.tileGeometry
        tiles.tileStyle = appearance.tileStyle
        topBar.appearance = appearance.topBar
        wallpaper.show(appearance.background)
        showTheme(appearance.theme)
    }

    /**
     * The theme's accent on the tiles' focus frame and the gear's disc, and its colors in the
     * tile's menu and the window while the wallpaper is drawn; the home screen itself stays dark,
     * on its wallpaper. Applied only when the theme changed.
     */
    private fun showTheme(theme: ThemeAppearance) {
        if (theme == shownTheme) return
        shownTheme = theme
        val roles = colorRoles(theme)
        tiles.accent = roles.homeAccent
        settingsEntry.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(roles.homeAccent)
            })
        }
        menu?.style(roles, tiles.tileStyle.corners)
        wallpaper.roles = roles
    }

    /**
     * The secondary texts (the date, the move hint, why there are no tiles) in an opaque gray over
     * a picture: translucent, they'd take its colors, muddy inside their dark edge on a light one.
     * On the plain color, translucent, as Luncher's other secondary texts.
     */
    private fun showOnPicture(onPicture: Boolean) {
        val color = color(if (onPicture) UiR.color.text_secondary_on_picture else UiR.color.text_secondary)
        for (id in intArrayOf(R.id.home_date, R.id.home_move_hint, R.id.home_empty)) findViewById<TextView>(id).setTextColor(color)
    }

    /** What OK on an entry of [tile]'s menu does. */
    private fun act(action: AppAction, tile: AppTileView) {
        val arranged = arranged ?: return
        when (action) {
            AppAction.Move -> move.start(arranged, tile)
            // Stored, and shown as any change of the arrangement is (showArrangement).
            AppAction.Hide -> arrangements.save(arranged.hide(tile.app).arrangement)
            AppAction.Rename -> askName(tile.app)
            AppAction.Info -> openAppInfo(tile.app)
        }
    }

    /**
     * Asks for [app]'s new name in a dialog, the field holding the name it shows; for an app the
     * user renamed, its original name below it, which Reset puts back (dimmed for one that has
     * it). Rename, or the keyboard's Done, stores the name typed ([appName]: an empty field, or
     * the original name, is the original name again), which shows as any change of the names does
     * (showAppChanges); nothing is refused.
     */
    private fun askName(app: HomeApp) {
        val renamed = app.customization.name != null
        val dialog = NameDialog(
            this,
            colorRoles(shownTheme ?: appearanceSettings.read().theme),
            getString(R.string.home_rename_title),
            app.name,
            getString(R.string.home_rename_cancel),
            getString(R.string.home_rename_confirm),
            note = if (renamed) getString(R.string.home_rename_original, app.installed.label) else null,
            extra = DialogButton(getString(R.string.home_rename_reset), DialogButton.Kind.TONAL, UiR.drawable.icon_reset, available = renamed) {
                rename(app, null)
            },
            maxLength = MAX_APP_NAME,
        ) { typed ->
            rename(app, appName(typed, app.installed.label))
            null
        }
        renameDialog = dialog
        dialog.show()
    }

    /** Stores [app] named [name], or by its own label for null, as the apps are now. */
    private fun rename(app: HomeApp, name: String?) {
        val arranged = arranged ?: return
        customizations.save(arranged.renamed(app, name))
    }

    /**
     * The app's page in the device's settings, in a task of its own, as any other app; Back from it
     * returns here. The menu offered it only if the page exists (AppInfoScreen), which the
     * intent's action and Uri must match; a settings app disabled since gets a message.
     */
    private fun openAppInfo(app: HomeApp) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.launchable.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.home_menu_info_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    /** Moving an app ended: the tiles show [arranged] already, unless apps changed meanwhile. */
    private fun movingEnded(arranged: ArrangedApps) {
        this.arranged = arranged
        shown = arranged.shown
        if (appsChangedWhileMoving) {
            appsChangedWhileMoving = false
            refresh()
        }
    }

    private fun show(apps: List<HomeApp>) {
        // The menu, or the rename dialog, is of a tile that may be gone, or of an app that changed.
        menu?.dismiss()
        renameDialog?.dismiss()
        val focusedTile = tiles.focusedChild as? AppTileView
        val focused = focusedTile?.app?.launchable
        val focusedIndex = focusedTile?.let { tiles.indexOfChild(it) } ?: 0
        // Focus in the top bar (on the settings entry) stays there; the tiles don't take it.
        val topBarFocused = topBar.hasFocus()
        // Apps still there keep their tile and its image: installing one app draws one banner. A
        // renamed app is another HomeApp, so its tile is made again, with its new name.
        val kept = (0 until tiles.childCount).map { tiles.getChildAt(it) as AppTileView }.associateBy { it.app }
        tiles.removeAllViews()
        for (app in apps) {
            tiles.addView(
                kept[app] ?: AppTileView(this, app, images, tiles.paints).apply {
                    setOnClickListener(openApp)
                    setOnLongClickListener(openMenu)
                },
            )
        }
        shown = apps
        // The focused app gone (hidden, uninstalled): the tile now in its place, or the last one;
        // without tiles, the settings entry.
        val index = apps.indexOfFirst { it.launchable == focused }.takeIf { it >= 0 } ?: focusedIndex.coerceAtMost(apps.size - 1)
        if (!topBarFocused) (tiles.getChildAt(index) ?: settingsEntry).requestFocus()
    }

    /** Without tiles, says why: nothing installed, or everything hidden (and where to show apps again). */
    private fun showWhyEmpty(arranged: ArrangedApps) {
        if (arranged.shown.isNotEmpty()) {
            empty.visibility = View.GONE
            return
        }
        empty.text = if (arranged.hidden.isEmpty()) {
            getString(R.string.home_no_apps)
        } else {
            getString(R.string.home_all_hidden, getString(SettingsR.string.settings_title), getString(SettingsR.string.settings_hide_apps))
        }
        empty.visibility = View.VISIBLE
    }

    private fun open(app: HomeApp) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setClassName(app.launchable.packageName, app.launchable.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            refresh()   // uninstalled since the tiles were read: drop its tile
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    // The Menu key opens the settings too, on remotes that have one; the top bar's entry is the
    // way on those that don't. On release, and only for a press that started here: not the end
    // of a press that closed something else.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_MENU) return super.onKeyDown(keyCode, event)
        event.startTracking()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_MENU) return super.onKeyUp(keyCode, event)
        if (event.isTracking && !event.isCanceled) openSettings()
        return true
    }

    // Back on Android 15 (API 35) and older; onCreate handles Android 16, which lint doesn't see.
    // Deprecated, but its replacements need AndroidX (OnBackPressedCallback) or API 33
    // (OnBackInvokedCallback). Doesn't call super, which would close the home screen.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = move.back()
}
