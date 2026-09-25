package com.luncher.launcher.home

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
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
    }

    override fun onResume() {
        super.onResume()
        status.text = getString(R.string.home_status_apps_found, installedApps.tvApps().size)
    }

    // A home activity must not finish on Back.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = Unit
}
