package com.luncher.launcher.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import com.luncher.domain.apps.ArrangedApps
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.apps.homeApps
import com.luncher.launcher.R
import com.luncher.launcher.graph
import com.luncher.launcher.settings.SettingsActivity

/**
 * The home screen: the time, the date and a settings entry above the TV apps the user didn't hide,
 * as tiles; OK on a tile opens its app, OK on the settings entry (or the Menu key) opens the
 * settings panel. A long press of OK on a tile starts [ArrangeMode], where the user moves and
 * hides apps; it gets every key first while it's on.
 */
class HomeActivity : Activity() {

    private val installedApps by lazy { graph.installedApps }
    private val arrangements by lazy { graph.appArrangements }
    private val banners by lazy { graph.bannerImages }
    private val clock by lazy { graph.clock }
    private lateinit var topBar: View
    private lateinit var tiles: AppTilesView
    private lateinit var clockView: ClockView
    private lateinit var empty: TextView
    private lateinit var arrange: ArrangeMode

    /** What the tiles show; null until the first [onResume]. */
    private var shown: List<InstalledApp>? = null

    /** The apps as last read, shown and hidden: where arrange mode starts from. */
    private var arranged: ArrangedApps? = null

    private val openApp = View.OnClickListener { open((it as AppTileView).app) }

    private val startArranging = View.OnLongClickListener {
        val arranged = arranged ?: return@OnLongClickListener false
        arrange.start(arranged, it as AppTileView)
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The manifest's Theme.Luncher.Launch is for the launch screen Android shows while the
        // process starts; the window itself gets the plain background, or the launch screen's
        // drawing would stay behind the tiles, drawn under every frame.
        setTheme(R.style.Theme_Luncher)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_activity)
        topBar = findViewById(R.id.home_top_bar)
        tiles = findViewById(R.id.home_apps)
        empty = findViewById(R.id.home_empty)
        clockView = findViewById(R.id.home_clock)
        val settingsEntry = findViewById<View>(R.id.home_settings)
        settingsEntry.setOnClickListener { openSettings() }
        arrange = ArrangeMode(
            tiles,
            normalBar = listOf(clockView, settingsEntry),
            title = findViewById(R.id.home_arrange_title),
            hint = findViewById(R.id.home_arrange_hint),
            banners,
            arrangements,
            onEnd = ::arrangingEnded,
        )
        // A home activity must not finish on Back: Back only ends arrange mode. From Android 16
        // (API 36) on, Back no longer calls onBackPressed in apps targeting it, nor reaches them
        // as a key, and closes the activity unless a callback takes it; before that,
        // onBackPressed below does.
        if (Build.VERSION.SDK_INT >= 36) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                arrange.back()
            }
        }
    }

    // Visible from onStart to onStop; paused only, e.g. behind a dialog, it still shows.
    override fun onStart() {
        super.onStart()
        clockView.start(clock)
    }

    override fun onStop() {
        super.onStop()
        arrange.end()   // leaving the home screen (another app, the screen off) ends it
        clockView.stop()
    }

    override fun onResume() {
        super.onResume()
        // Paused and resumed while arranging (a system dialog came and went): the tiles are the
        // user's work in progress, so the apps are read again only when the mode ends.
        if (!arrange.active) refresh()
    }

    // Home while the home screen is in front.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        arrange.end()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        arrange.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)

    /**
     * Reads the apps again: apps may have been installed or removed, or hidden or shown in the
     * settings, since the home screen was last shown.
     */
    private fun refresh() {
        val arranged = homeApps(installedApps.tvApps(), packageName, arrangements.read())
        this.arranged = arranged
        // Unchanged, as on most returns to the home screen: keep the tiles, their images and focus.
        if (arranged.shown != shown) show(arranged.shown)
        showWhyEmpty(arranged)
    }

    /** Arrange mode ended: the tiles show [arranged] already. */
    private fun arrangingEnded(arranged: ArrangedApps) {
        this.arranged = arranged
        shown = arranged.shown
        showWhyEmpty(arranged)
    }

    private fun show(apps: List<InstalledApp>) {
        val focused = (tiles.focusedChild as? AppTileView)?.app?.launchable
        // Focus in the top bar (on the settings entry) stays there; the tiles don't take it.
        val topBarFocused = topBar.hasFocus()
        // Apps still there keep their tile and its image: installing one app draws one banner.
        val kept = (0 until tiles.childCount).map { tiles.getChildAt(it) as AppTileView }.associateBy { it.app }
        tiles.removeAllViews()
        for (app in apps) {
            tiles.addView(
                kept[app] ?: AppTileView(this, app, banners).apply {
                    setOnClickListener(openApp)
                    setOnLongClickListener(startArranging)
                },
            )
        }
        shown = apps
        val index = apps.indexOfFirst { it.launchable == focused }.coerceAtLeast(0)
        if (!topBarFocused) tiles.getChildAt(index)?.requestFocus()
    }

    /** Without tiles, says why: nothing installed, or everything hidden (and where to show apps again). */
    private fun showWhyEmpty(arranged: ArrangedApps) {
        if (arranged.shown.isNotEmpty()) {
            empty.visibility = View.GONE
            return
        }
        empty.setText(if (arranged.hidden.isEmpty()) R.string.home_no_apps else R.string.home_all_hidden)
        empty.visibility = View.VISIBLE
    }

    private fun open(app: InstalledApp) {
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
    override fun onBackPressed() = arrange.back()
}
