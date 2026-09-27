package com.luncher.launcher.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.window.OnBackInvokedDispatcher
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.apps.homeApps
import com.luncher.launcher.R
import com.luncher.launcher.graph
import com.luncher.launcher.settings.SettingsActivity

/**
 * The home screen: the time, the date and a settings entry above the TV apps as tiles; OK on a
 * tile opens its app, OK on the settings entry (or the Menu key) opens the settings panel.
 */
class HomeActivity : Activity() {

    private val installedApps by lazy { graph.installedApps }
    private val banners by lazy { graph.bannerImages }
    private val clock by lazy { graph.clock }
    private lateinit var topBar: View
    private lateinit var tiles: AppTilesView
    private lateinit var clockView: ClockView
    private lateinit var empty: View

    /** What the tiles show; null until the first [onResume]. */
    private var shown: List<InstalledApp>? = null

    private val openApp = View.OnClickListener { open((it as AppTileView).app) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_activity)
        topBar = findViewById(R.id.home_top_bar)
        tiles = findViewById(R.id.home_apps)
        empty = findViewById(R.id.home_empty)
        clockView = findViewById(R.id.home_clock)
        findViewById<View>(R.id.home_settings).setOnClickListener { openSettings() }
        // A home activity must not finish on Back. From Android 16 (API 36) on, Back no longer
        // calls onBackPressed in apps targeting it, and closes the activity unless a callback
        // takes it; before that, the empty onBackPressed below does.
        if (Build.VERSION.SDK_INT >= 36) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {}
        }
    }

    // Visible from onStart to onStop; paused only, e.g. behind a dialog, it still shows.
    override fun onStart() {
        super.onStart()
        clockView.start(clock)
    }

    override fun onStop() {
        super.onStop()
        clockView.stop()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** Reads the apps again; apps may have been installed or removed since the home screen was last shown. */
    private fun refresh() {
        val apps = homeApps(installedApps.tvApps(), packageName)
        // Unchanged, as on most returns to the home screen: keep the tiles, their images and focus.
        if (apps != shown) show(apps)
    }

    private fun show(apps: List<InstalledApp>) {
        val focused = (tiles.focusedChild as? AppTileView)?.app?.launchable
        // Focus in the top bar (on the settings entry) stays there; the tiles don't take it.
        val topBarFocused = topBar.hasFocus()
        // Apps still there keep their tile and its image: installing one app draws one banner.
        val kept = (0 until tiles.childCount).map { tiles.getChildAt(it) as AppTileView }.associateBy { it.app }
        tiles.removeAllViews()
        for (app in apps) {
            tiles.addView(kept[app] ?: AppTileView(this, app, banners).apply { setOnClickListener(openApp) })
        }
        empty.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        shown = apps
        val index = apps.indexOfFirst { it.launchable == focused }.coerceAtLeast(0)
        if (!topBarFocused) tiles.getChildAt(index)?.requestFocus()
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
    // (OnBackInvokedCallback).
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = Unit
}
