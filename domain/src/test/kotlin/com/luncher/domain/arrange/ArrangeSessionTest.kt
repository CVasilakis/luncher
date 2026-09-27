package com.luncher.domain.arrange

import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.apps.homeApps
import com.luncher.domain.layout.Direction.DOWN
import com.luncher.domain.layout.Direction.LEFT
import com.luncher.domain.layout.Direction.RIGHT
import com.luncher.domain.layout.Direction.UP
import com.luncher.domain.layout.TileGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangeSessionTest {

    // Rows of three. Apps "a" to "h" by label; "x", "y" hidden unless a test says otherwise.
    private fun session(shown: String = "abcdefgh", hidden: String = "xy"): ArrangeSession {
        val names = (shown + hidden).map { it.toString() }
        val arrangement = AppArrangement(
            order = shown.map { app(it.toString()).launchable },
            hidden = hidden.map { app(it.toString()).launchable },
        )
        val arranged = homeApps(names.map { app(it) }, "com.luncher.launcher", arrangement)
        return ArrangeSession(arranged) { TileGrid(it, columns = 3, width = 300, gap = 0) }
    }

    private fun names(apps: List<InstalledApp>) = apps.joinToString("") { it.label.lowercase() }

    private fun ArrangeSession.holding(name: String) = apply { pickUp(app(name)) }

    @Test
    fun `holds nothing at first`() {
        val session = session()

        assertNull(session.held)
        assertEquals(-1, session.heldPlace)
        assertFalse(session.move(RIGHT))
    }

    @Test
    fun `left and right move the held app along the order, across rows`() {
        val session = session().holding("c")

        session.move(RIGHT)

        assertEquals("abdcefgh", names(session.shown))
        assertEquals(3, session.heldPlace)
    }

    @Test
    fun `up and down move the held app a row, shifting the others`() {
        val session = session().holding("b")

        session.move(DOWN)

        assertEquals("acdebfgh", names(session.shown))
        assertEquals(4, session.heldPlace)
    }

    @Test
    fun `down from the last shown row hides the app, in the shelf's first row`() {
        val session = session().holding("h")   // row 3, column 1

        assertTrue(session.move(DOWN))

        assertEquals("abcdefg", names(session.shown))
        assertEquals("xhy", names(session.hidden))   // column 1
        assertEquals(7 + 1, session.heldPlace)
    }

    @Test
    fun `down from the last shown row takes the column, or as near as the shelf allows`() {
        val session = session(hidden = "xyz").holding("g")   // row 3, column 0

        session.move(DOWN)

        assertEquals("gxyz", names(session.hidden))
    }

    @Test
    fun `down from the last shown row into an empty shelf`() {
        val session = session(hidden = "").holding("h")

        session.move(DOWN)

        assertEquals("h", names(session.hidden))
        assertEquals(7, session.heldPlace)
    }

    @Test
    fun `up from the shelf's first row shows the app again, in the last shown row`() {
        val session = session().holding("y")   // shelf column 1

        assertTrue(session.move(UP))

        assertEquals("abcdefgyh", names(session.shown))   // last row starts at g: g, y, h
        assertEquals("x", names(session.hidden))
        assertEquals(7, session.heldPlace)
    }

    @Test
    fun `up from the shelf when every app is hidden`() {
        val session = session(shown = "", hidden = "xy").holding("y")

        session.move(UP)

        assertEquals("y", names(session.shown))
        assertEquals("x", names(session.hidden))
    }

    @Test
    fun `the last shown app can be hidden`() {
        val session = session(shown = "a", hidden = "").holding("a")

        session.move(DOWN)

        assertEquals("", names(session.shown))
        assertEquals("a", names(session.hidden))
    }

    @Test
    fun `left and right don't cross between shown and hidden apps`() {
        val last = session().holding("h")
        val first = session().holding("x")

        assertFalse(last.move(RIGHT))
        assertFalse(first.move(LEFT))
        assertEquals("abcdefgh", names(last.shown))
        assertEquals("xy", names(first.hidden))
    }

    @Test
    fun `up from the first shown row doesn't move`() {
        val session = session().holding("b")

        assertFalse(session.move(UP))
        assertEquals(1, session.heldPlace)
    }

    @Test
    fun `hidden apps move among themselves`() {
        val session = session(hidden = "xyz").holding("x")

        session.move(RIGHT)

        assertEquals("yxz", names(session.hidden))
        assertEquals(8 + 1, session.heldPlace)
    }

    @Test
    fun `picks up a hidden app where it is`() {
        val session = session().holding("y")

        assertEquals(8 + 1, session.heldPlace)
        assertEquals("xy", names(session.hidden))
    }

    @Test
    fun `drop says whether the app moved since it was picked up`() {
        val session = session().holding("a")
        assertFalse(session.drop())
        assertNull(session.held)

        session.pickUp(app("a"))
        session.move(RIGHT)
        assertTrue(session.drop())

        session.pickUp(app("a"))
        session.move(UP)   // can't
        assertFalse(session.drop())
    }

    @Test
    fun `moving across and back leaves the apps as they were`() {
        val session = session().holding("h")

        session.move(DOWN)
        session.move(UP)

        assertEquals("abcdefgh", names(session.shown))
        assertEquals("xy", names(session.hidden))
    }

    @Test
    fun `the arrangement keeps the order the user gave`() {
        val session = session(shown = "cab", hidden = "").holding("c")
        session.move(RIGHT)
        session.drop()

        val arrangement = session.arranged().arrangement

        assertEquals(listOf("a", "c", "b").map { app(it).launchable }, arrangement.order)
    }

    @Test
    fun `fixes the order by label once anything moved`() {
        val start = homeApps(listOf(app("b"), app("a"), app("c")), "com.luncher.launcher", AppArrangement.NONE)
        val session = ArrangeSession(start) { TileGrid(it, columns = 3, width = 300, gap = 0) }
        assertSame(start, session.arranged())      // nothing moved: the order stays by label

        session.pickUp(app("c"))
        session.move(DOWN)                         // hidden

        assertEquals(listOf("a", "b").map { app(it).launchable }, session.arranged().arrangement.order)
        assertEquals(listOf(app("c").launchable), session.arranged().arrangement.hidden)
    }

    @Test(expected = IllegalStateException::class)
    fun `holds one app at a time`() {
        session().holding("a").holding("b")
    }
}
