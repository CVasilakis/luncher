package com.luncher.domain.apps

import com.luncher.domain.apps.PreparedTileImages.State
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.ManualJobs
import com.luncher.domain.layout.TileSize
import com.luncher.domain.pictures.FakePreparedPictures
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PreparingCause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparedTileImagesTest {

    private val sunset = PictureId("sunset")
    private val forest = PictureId("forest")
    private val sea = PictureId("sea")

    private val news = LaunchableApp("news", "Main")
    private val movies = LaunchableApp("movies", "Main")
    private val music = LaunchableApp("music", "Main")

    private val hd = TileSize(345, 194)
    private val small = TileSize(230, 129)

    private val jobs = ManualJobs()
    private val copies = FakePreparedPictures()
    private val kept = FakePreparedTiles(copies, jobs) { it }

    /** Results the main thread hasn't run yet, as a Handler would queue them. */
    private val onMain = mutableListOf<() -> Unit>()
    private val tiles = PreparedTileImages(copies, kept, jobs) { onMain += it }

    /** Every state the listeners were told of, in order. */
    private val told = mutableListOf<State>().also { told -> tiles.addListener { told += tiles.state } }

    private fun drawing(picture: PictureId, size: TileSize = hd, fit: Fit = Fit.FILL, focus: TileFocusShape = TileFocusShape.TILE) =
        TileDrawing(picture, Framing(fit), focus, size)

    /** The tiles as the home screen gives them: News shows the sunset, Movies the forest, Music the sea. */
    private fun all(size: TileSize = hd) = linkedMapOf(news to drawing(sunset, size), movies to drawing(forest, size), music to drawing(sea, size))

    /** The tiles of [drawings] kept already, as an earlier start left them. */
    private fun keptAlready(drawings: Map<LaunchableApp, TileDrawing>) = kept.kept.putAll(drawings)

    /** The next job run, then its result on the main thread. */
    private fun runNext() {
        jobs.runNext()
        val queued = onMain.toList()
        onMain.clear()
        queued.forEach { it() }
    }

    private fun runAll() {
        while (jobs.pending > 0 || onMain.isNotEmpty()) runNext()
    }

    @Test
    fun `with every tile prepared, nothing is prepared and nothing told`() {
        keptAlready(all())

        tiles.prepare(all())

        assertEquals(State.None, tiles.state)
        runAll()
        assertEquals(emptyList<Pair<LaunchableApp, TileDrawing>>(), kept.prepared)
        assertEquals(emptyList<State>(), told)
    }

    @Test
    fun `prepares those that aren't, one job each, the copy first where it isn't, saying why until the last is done`() {
        keptAlready(mapOf(news to drawing(sunset)))
        copies.missing += sea
        tiles.prepare(all())
        assertEquals(State.Preparing(PreparingCause.OUTDATED), tiles.state)
        runNext()   // what no tile names deleted, first
        assertFalse(tiles.preparing(news))
        assertTrue(tiles.preparing(movies))
        assertTrue(tiles.preparing(music))

        runNext()
        assertEquals(listOf(movies to drawing(forest)), kept.prepared)
        assertFalse(tiles.preparing(movies))
        assertTrue(tiles.preparing(music))
        assertEquals(State.Preparing(PreparingCause.OUTDATED), tiles.state)

        runNext()   // the sea's copy
        assertEquals(listOf(sea), copies.prepared)
        assertTrue(tiles.preparing(music))
        runNext()
        assertEquals(listOf(movies to drawing(forest), music to drawing(sea)), kept.prepared)
        assertEquals(State.None, tiles.state)
        assertEquals(0, jobs.pending)
        // Told as it started, and as each was prepared: the home screen draws that one's tile again.
        assertEquals(listOf(State.Preparing(PreparingCause.OUTDATED), State.Preparing(PreparingCause.OUTDATED), State.None), told)
    }

    @Test
    fun `each job is of the pictures' screens`() {
        copies.missing += sunset

        tiles.prepare(mapOf(news to drawing(sunset)))

        assertEquals(List(3) { JobPriority.PICTURE_SCREENS }, jobs.pendingPriorities())
    }

    @Test
    fun `a copy two tiles show is prepared once`() {
        copies.missing += sunset

        tiles.prepare(mapOf(news to drawing(sunset), movies to drawing(sunset, fit = Fit.STRETCH)))
        runAll()

        assertEquals(listOf(sunset), copies.prepared)
        assertEquals(listOf(news, movies), kept.prepared.map { it.first })
    }

    @Test
    fun `a screen of another size makes every tile due again, saying so`() {
        keptAlready(all())
        tiles.prepare(all())

        tiles.prepare(all(small))

        assertEquals(State.Preparing(PreparingCause.SCREEN_CHANGED), tiles.state)
        runAll()
        assertEquals(all(small).toList(), kept.prepared)
        assertEquals(State.None, tiles.state)
    }

    @Test
    fun `asks nothing again for the same drawings, as most returns to the home screen do`() {
        tiles.prepare(all())
        runAll()
        val asked = kept.asked

        tiles.prepare(all())

        assertEquals(asked, kept.asked)
        assertEquals(0, jobs.pending)
    }

    @Test
    fun `another framing or focus on the same pictures is prepared again, the other tiles not`() {
        keptAlready(all())
        tiles.prepare(all())
        runAll()

        tiles.prepare(all() + mapOf(news to drawing(sunset, fit = Fit.STRETCH), music to drawing(sea, focus = TileFocusShape.SHAPE)))
        runAll()

        assertEquals(listOf(news to drawing(sunset, fit = Fit.STRETCH), music to drawing(sea, focus = TileFocusShape.SHAPE)), kept.prepared)
    }

    @Test
    fun `asked again while preparing, a tile isn't prepared twice`() {
        tiles.prepare(mapOf(news to drawing(sunset)))
        runNext()   // the sweep

        tiles.prepare(mapOf(news to drawing(sunset), movies to drawing(forest)))
        runAll()

        assertEquals(listOf(news, movies), kept.prepared.map { it.first })
    }

    @Test
    fun `with some for another screen and some outdated, it says they're outdated`() {
        keptAlready(mapOf(news to drawing(sunset, small)))

        tiles.prepare(mapOf(news to drawing(sunset), movies to drawing(forest)))
        assertEquals(State.Preparing(PreparingCause.OUTDATED), tiles.state)

        runNext()
        runNext()   // the one for another screen; the outdated one is left
        assertEquals(State.Preparing(PreparingCause.OUTDATED), tiles.state)
    }

    @Test
    fun `one that can't be kept is given up, the rest go on, and it isn't tried again, on another screen either`() {
        kept.failing += news

        tiles.prepare(all())
        runAll()
        assertEquals(listOf(movies, music), kept.prepared.map { it.first })
        assertFalse(tiles.preparing(news))
        assertEquals(State.None, tiles.state)

        kept.failing -= news   // it could be now: it isn't tried
        tiles.prepare(all(small))
        runAll()
        assertEquals(listOf(movies, music, movies, music), kept.prepared.map { it.first })
    }

    @Test
    fun `a picture whose copy can't be prepared gives up its tiles, the rest go on`() {
        copies.missing += sunset
        copies.failing += sunset

        tiles.prepare(mapOf(news to drawing(sunset), movies to drawing(forest)))
        runAll()
        assertEquals(listOf(movies), kept.prepared.map { it.first })
        assertEquals(State.None, tiles.state)

        // Another tile of it, later: given up as well, its copy not tried again.
        tiles.prepare(mapOf(news to drawing(sunset), movies to drawing(forest), music to drawing(sunset)))
        assertEquals(0, jobs.pending)
        assertEquals(State.None, tiles.state)
    }

    @Test
    fun `a copy whose job came too late for a tile is prepared for it, the tile after it`() {
        // The copy was there as the tile was queued, and gone by the time it ran.
        tiles.prepare(mapOf(news to drawing(sunset)))
        copies.missing += sunset

        runAll()

        assertEquals(listOf(sunset), copies.prepared)
        assertEquals(listOf(news), kept.prepared.map { it.first })
    }

    @Test
    fun `a copy said prepared that its tile doesn't find gives the tile up after one more try`() {
        val copyless = FakePreparedTiles(FakePreparedPictures(sunset).apply { failing += sunset }, jobs) { it }
        val tiles = PreparedTileImages(copies, copyless, jobs) { onMain += it }

        tiles.prepare(mapOf(news to drawing(sunset)))
        runAll()

        assertFalse(tiles.preparing(news))
        assertEquals(State.None, tiles.state)
        assertEquals(emptyList<Pair<LaunchableApp, TileDrawing>>(), copyless.prepared)
    }

    @Test
    fun `a copy found damaged as its tile is prepared is made again, then the tile, once`() {
        kept.damagedCopies += sunset

        tiles.prepare(mapOf(news to drawing(sunset)))
        runAll()

        assertEquals(listOf(sunset), copies.prepared)
        assertEquals(listOf(news), kept.prepared.map { it.first })
        assertEquals(State.None, tiles.state)
    }

    @Test
    fun `a copy found damaged again after it was made again gives its tile up, so it isn't made in a loop`() {
        kept.damagedCopies += sunset
        tiles.prepare(mapOf(news to drawing(sunset)))
        runNext()
        runNext()   // damaged: made again
        kept.damagedCopies += sunset

        runAll()

        assertEquals(emptyList<Pair<LaunchableApp, TileDrawing>>(), kept.prepared)
        assertFalse(tiles.preparing(news))
        assertEquals(State.None, tiles.state)
    }

    @Test
    fun `a tile found damaged as it's read is prepared again at once, and given up if it is again`() {
        keptAlready(mapOf(news to drawing(sunset)))
        tiles.prepare(mapOf(news to drawing(sunset)))
        kept.damaged += news

        assertNull(tiles.read(news, drawing(sunset)))
        assertEquals(State.Preparing(PreparingCause.OUTDATED), tiles.state)
        assertTrue(tiles.preparing(news))
        runAll()
        assertNotNull(tiles.read(news, drawing(sunset)))

        kept.damaged += news
        assertNull(tiles.read(news, drawing(sunset)))
        assertEquals(State.None, tiles.state)
        assertEquals(0, jobs.pending)
    }

    @Test
    fun `reads a tile prepared for its drawing, and none for another`() {
        keptAlready(mapOf(news to drawing(sunset)))

        assertEquals(drawing(sunset), tiles.read(news, drawing(sunset))?.image)
        assertNull(tiles.read(news, drawing(sunset, small)))
        assertNull(tiles.read(movies, drawing(forest)))
        assertEquals(0, jobs.pending)
    }

    @Test
    fun `the first preparing of a process deletes the tiles no tile names, then each that leaves`() {
        tiles.prepare(all())
        runAll()
        assertEquals(listOf(setOf(news, movies, music)), kept.keptOnly)

        tiles.prepare(mapOf(news to drawing(sunset), music to drawing(sea)))
        runAll()
        assertEquals(listOf(movies), kept.deleted)
        assertEquals(setOf(news, music), kept.kept.keys)
        assertEquals(1, kept.keptOnly.size)
    }

    @Test
    fun `a tile saved is prepared while it says it's saving, before any other cause, then done`() {
        keptAlready(mapOf(movies to drawing(forest, small)))
        tiles.prepare(mapOf(movies to drawing(forest)))
        runNext()   // the sweep
        var done = 0

        assertTrue(tiles.save(news, drawing(sunset)) { done++ })

        assertEquals(State.Saving, tiles.state)
        assertTrue(tiles.preparing(news))
        runNext()   // Movies, for this screen
        assertEquals(State.Saving, tiles.state)
        assertEquals(0, done)
        runAll()
        assertEquals(1, done)
        assertEquals(State.None, tiles.state)
        assertEquals(listOf(movies, news), kept.prepared.map { it.first })
    }

    @Test
    fun `the home screen back while a tile is saved prepares it no second time`() {
        tiles.save(news, drawing(sunset)) {}

        tiles.prepare(mapOf(news to drawing(sunset)))

        assertEquals(State.Saving, tiles.state)
        runAll()
        assertEquals(listOf(news), kept.prepared.map { it.first })
        assertEquals(State.None, tiles.state)
    }

    @Test
    fun `a tile saved as it's prepared already has nothing to wait for`() {
        keptAlready(mapOf(news to drawing(sunset)))
        var done = 0

        assertFalse(tiles.save(news, drawing(sunset)) { done++ })

        assertEquals(State.None, tiles.state)
        assertEquals(0, jobs.pending)
        assertEquals(0, done)
        assertEquals(emptyList<State>(), told)
    }

    @Test
    fun `a tile saved that can't be prepared is done all the same, and given up for the home screen`() {
        kept.failing += news
        var done = 0

        tiles.save(news, drawing(sunset)) { done++ }
        runAll()

        assertEquals(1, done)
        assertEquals(State.None, tiles.state)
        tiles.prepare(mapOf(news to drawing(sunset)))
        runAll()
        assertTrue(kept.prepared.isEmpty())
    }

    @Test
    fun `a tile saved is tried again though it was given up`() {
        kept.failing += news
        tiles.prepare(mapOf(news to drawing(sunset)))
        runAll()
        kept.failing -= news

        tiles.save(news, drawing(sunset)) {}
        runAll()
        assertEquals(listOf(news), kept.prepared.map { it.first })

        // No longer given up: the home screen prepares it for another screen too.
        tiles.prepare(mapOf(news to drawing(sunset, small)))
        runAll()
        assertEquals(listOf(news to drawing(sunset), news to drawing(sunset, small)), kept.prepared)
    }

    @Test
    fun `a tile forgotten has what's kept of it deleted, and isn't waited for`() {
        tiles.prepare(mapOf(news to drawing(sunset)))
        assertTrue(tiles.preparing(news))

        tiles.forget(news)

        assertFalse(tiles.preparing(news))
        assertEquals(State.None, tiles.state)
        runAll()
        assertEquals(listOf(news), kept.deleted)
        assertNull(tiles.read(news, drawing(sunset)))
    }

    @Test
    fun `nothing slow is done but in a job`() {
        copies.missing += sunset

        // The fake fails if asked to prepare or delete outside a job.
        tiles.prepare(all())
        tiles.save(news, drawing(sunset, small)) {}
        tiles.forget(movies)
        runAll()
        tiles.read(news, drawing(sunset, small))
    }

    @Test
    fun `a listener removed isn't told`() {
        val calls = mutableListOf<Unit>()
        val listener = { calls += Unit }
        tiles.addListener(listener)
        tiles.removeListener(listener)

        tiles.prepare(all())

        assertEquals(emptyList<Unit>(), calls)
    }
}
