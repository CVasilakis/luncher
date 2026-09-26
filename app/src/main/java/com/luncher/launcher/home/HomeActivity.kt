package com.luncher.launcher.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.window.OnBackInvokedDispatcher
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.apps.homeApps
import com.luncher.launcher.R
import com.luncher.launcher.graph

/** The home screen: the TV apps as tiles; OK on one opens it. */
class HomeActivity : Activity() {

    private val installedApps by lazy { graph.installedApps }
    private val banners by lazy { graph.bannerImages }
    private lateinit var tiles: AppTilesView
    private lateinit var empty: View

    /** What the tiles show; null until the first [onResume]. */
    private var shown: List<InstalledApp>? = null

    private val openApp = View.OnClickListener { open((it as AppTileView).app) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_activity)
        tiles = findViewById(R.id.home_apps)
        empty = findViewById(R.id.home_empty)
        // A home activity must not finish on Back. From Android 16 (API 36) on, Back no longer
        // calls onBackPressed in apps targeting it, and closes the activity unless a callback
        // takes it; before that, the empty onBackPressed below does.
        if (Build.VERSION.SDK_INT >= 36) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {}
        }
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
        // Apps still there keep their tile and its image: installing one app draws one banner.
        val kept = (0 until tiles.childCount).map { tiles.getChildAt(it) as AppTileView }.associateBy { it.app }
        tiles.removeAllViews()
        for (app in apps) {
            tiles.addView(kept[app] ?: AppTileView(this, app, banners).apply { setOnClickListener(openApp) })
        }
        empty.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        shown = apps
        val index = apps.indexOfFirst { it.launchable == focused }.coerceAtLeast(0)
        tiles.getChildAt(index)?.requestFocus()
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

    // Back on Android 15 (API 35) and older; onCreate handles Android 16, which lint doesn't see.
    // Deprecated, but its replacements need AndroidX (OnBackPressedCallback) or API 33
    // (OnBackInvokedCallback).
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = Unit
}
