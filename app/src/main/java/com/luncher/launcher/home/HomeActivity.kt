package com.luncher.launcher.home

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import com.luncher.launcher.R
import com.luncher.launcher.graph

/** The home screen. Placeholder: shows how many TV apps are installed. */
class HomeActivity : Activity() {

    private val installedApps by lazy { graph.installedApps }
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_activity)
        status = findViewById(R.id.home_status)
        // A home activity must not finish on Back. From Android 16 (API 36) on, Back no longer
        // calls onBackPressed in apps targeting it, and closes the activity unless a callback
        // takes it; before that, the empty onBackPressed below does.
        if (Build.VERSION.SDK_INT >= 36) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {}
        }
    }

    override fun onResume() {
        super.onResume()
        status.text = getString(R.string.home_status_apps_found, installedApps.tvApps().size)
    }

    // Back on Android 15 (API 35) and older; onCreate handles Android 16, which lint doesn't see.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = Unit
}
