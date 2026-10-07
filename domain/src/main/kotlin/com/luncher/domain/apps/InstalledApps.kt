package com.luncher.domain.apps

/** Port: the apps installed on the device. Implemented in :platform on top of PackageManager. */
interface InstalledApps {

    /** Apps with a TV launcher entry (`MAIN` + `LEANBACK_LAUNCHER`), in no particular order. */
    fun tvApps(): List<InstalledApp>
}
