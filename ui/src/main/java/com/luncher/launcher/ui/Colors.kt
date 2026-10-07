package com.luncher.launcher.ui

import android.content.Context
import android.os.Build

/** The color resource [id], on every API level: `Context.getColor` exists only from API 23. */
fun Context.color(id: Int): Int =
    if (Build.VERSION.SDK_INT >= 23) getColor(id) else @Suppress("DEPRECATION") resources.getColor(id)
