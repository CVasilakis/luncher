package com.luncher.domain.arrange

import com.luncher.domain.appearance.HorizontalAlignment
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.apps.homeApps
import com.luncher.domain.layout.Direction.DOWN
import com.luncher.domain.layout.Direction.LEFT
import com.luncher.domain.layout.Direction.RIGHT
import com.luncher.domain.layout.Direction.UP
import com.luncher.domain.layout.TileGrid
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveSessionTest {

    // Rows of three. Apps "a" to "h" shown in that order; "x", "y" hidden unless a test says otherwise.
    private fun moving(
        name: String,
        shown: String = "abcdefgh",
        hidden: String = "xy",
        rows: HorizontalAlignment = HorizontalAlignment.LEFT,
    ): MoveSession {
        val names = (shown + hidden).map { it.toString() }
        val arrangement = AppArrangement(
            order = shown.map { app(it.toString()).launchable },
            hidden = hidden.map { app(it.toString()).launchable },
        )
        val arranged = homeApps(names.map { app(it) }, "com.luncher.launcher", arrangement, Locale.ENGLISH)
        return MoveSession(arranged, app(name), TileGrid(arranged.shown.size, columns = 3, width = 300, gap = 0, rows))
    }

    private fun names(apps: List<InstalledApp>) = apps.joinToString("") { it.label.lowercase() }

    @Test
    fun `holds the app where it is`() {
        val session = moving("c")

        assertEquals(app("c"), session.held)
        assertEquals(2, session.heldIndex)
        assertFalse(session.moved)
    }

    @Test
    fun `left and right move the held app along the order, across rows`() {
        val session = moving("c")

        session.move(RIGHT)

        assertEquals("abdcefgh", names(session.shown))
        assertEquals(3, session.heldIndex)
    }

    @Test
    fun `up and down move the held app a row, shifting the others`() {
        val session = moving("b")

        session.move(DOWN)

        assertEquals("acdebfgh", names(session.shown))
        assertEquals(4, session.heldIndex)
    }

    // Centered, the last row "gh" is half a column off the others: g between d and e, h between e and f.

    @Test
    fun `centered, down then up puts the held app back where it was`() {
        val session = moving("e", rows = HorizontalAlignment.CENTER)

        session.move(DOWN)
        assertEquals("abcdfgeh", names(session.shown))   // e between g and h, the left of two as near
        session.move(UP)

        assertEquals("abcdefgh", names(session.shown))   // not between c and d, the nearest
        assertEquals(4, session.heldIndex)
    }

    @Test
    fun `a move left or right forgets the way back`() {
        val session = moving("e", rows = HorizontalAlignment.CENTER)

        session.move(DOWN)   // abcdfgeh
        session.move(LEFT)   // abcdfegh: e at the end of the second row
        session.move(UP)

        assertEquals("abecdfgh", names(session.shown))   // above, not back to where it started
        assertEquals(2, session.heldIndex)
    }

    @Test
    fun `doesn't move past the edges`() {
        assertFalse(moving("b").move(UP))
        assertFalse(moving("a").move(LEFT))
        assertFalse(moving("h").move(RIGHT))
        assertFalse(moving("h").move(DOWN))
        assertFalse(moving("h").moved)
    }

    @Test
    fun `the hidden apps stay as they were`() {
        val session = moving("h")

        session.move(LEFT)

        assertEquals(listOf("x", "y").map { app(it).launchable }, session.arranged().arrangement.hidden)
    }

    @Test
    fun `says whether the app moved`() {
        val session = moving("a")
        assertFalse(session.move(UP))   // can't
        assertFalse(session.moved)

        session.move(RIGHT)

        assertTrue(session.moved)
    }

    @Test
    fun `the arrangement keeps the order the user gave`() {
        val session = moving("c", shown = "cab", hidden = "")
        session.move(RIGHT)

        assertEquals(listOf("a", "c", "b").map { app(it).launchable }, session.arranged().arrangement.order)
    }

    @Test
    fun `fixes the order by label once the app moved`() {
        val start = homeApps(listOf(app("b"), app("a"), app("c")), "com.luncher.launcher", AppArrangement.NONE, Locale.ENGLISH)
        val session = MoveSession(start, app("c"), TileGrid(3, columns = 3, width = 300, gap = 0))
        assertSame(start, session.arranged())      // nothing moved: the order stays by label

        session.move(LEFT)

        assertEquals(listOf("a", "c", "b").map { app(it).launchable }, session.arranged().arrangement.order)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `moves only a shown app`() {
        moving("x")
    }
}
