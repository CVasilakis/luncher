package com.luncher.domain.apps

import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppActionTest {

    private fun arranged(vararg names: String, hidden: List<String> = emptyList()) = homeApps(
        (names.toList() + hidden).map { app(it) }, "com.luncher.launcher",
        AppArrangement(order = null, hidden = hidden.map { app(it).launchable }), Locale.ENGLISH,
    )

    @Test
    fun `offers moving and hiding the app, and its info`() {
        assertEquals(
            listOf(AppMenuEntry(AppAction.Move), AppMenuEntry(AppAction.Hide), AppMenuEntry(AppAction.Info)),
            appActions(arranged("a", "b"), app("a"), hasAppInfo = true),
        )
    }

    // Hidden apps aren't among those it moves between.
    @Test
    fun `offers no move with no other app shown`() {
        assertEquals(
            listOf(AppMenuEntry(AppAction.Hide), AppMenuEntry(AppAction.Info)),
            appActions(arranged("a", hidden = listOf("b")), app("a"), hasAppInfo = true),
        )
    }

    @Test
    fun `lists the app's info, unavailable, where the device has no page for it`() {
        assertEquals(
            listOf(AppMenuEntry(AppAction.Move), AppMenuEntry(AppAction.Hide), AppMenuEntry(AppAction.Info, available = false)),
            appActions(arranged("a", "b"), app("a"), hasAppInfo = false),
        )
    }

    @Test
    fun `with no other app shown and no page for its info, only hiding it is available`() {
        assertEquals(
            listOf(AppMenuEntry(AppAction.Hide), AppMenuEntry(AppAction.Info, available = false)),
            appActions(arranged("a", hidden = listOf("b")), app("a"), hasAppInfo = false),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `is for a shown app only`() {
        appActions(arranged("a", hidden = listOf("b")), app("b"), hasAppInfo = true)
    }
}
