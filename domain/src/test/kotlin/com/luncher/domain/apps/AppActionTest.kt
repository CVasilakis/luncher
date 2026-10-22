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
    fun `offers moving and hiding the app`() {
        assertEquals(listOf(AppAction.Move, AppAction.Hide), appActions(arranged("a", "b"), app("a")))
    }

    // Hidden apps aren't among those it moves between.
    @Test
    fun `offers no move with no other app shown`() {
        assertEquals(listOf(AppAction.Hide), appActions(arranged("a", hidden = listOf("b")), app("a")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `is for a shown app only`() {
        appActions(arranged("a", hidden = listOf("b")), app("b"))
    }
}
