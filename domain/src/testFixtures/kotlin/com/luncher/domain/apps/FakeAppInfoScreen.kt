package com.luncher.domain.apps

/** [AppInfoScreen] whose answer the test sets: [exists], true at first, as on most devices. */
class FakeAppInfoScreen(var exists: Boolean = true) : AppInfoScreen {

    override fun exists(): Boolean = exists
}
