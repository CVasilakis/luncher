package com.luncher.domain.apps

import com.luncher.domain.apps.PreparedTileImages.State
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.ManualJobs
import com.luncher.domain.pictures.FakePreparedPictures
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PreparingCause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparedTileImagesTest {

    private val sunset = PictureId("sunset")
    private val forest = PictureId("forest")
    private val sea = PictureId("sea")

    private val prepared = FakePreparedPictures()
    private val jobs = ManualJobs()

    /** Results the main thread hasn't run yet, as a Handler would queue them. */
    private val onMain = mutableListOf<() -> Unit>()
    private val tiles = PreparedTileImages(prepared, jobs) { onMain += it }

    /** Every state the listeners were told of, in order. */
    private val told = mutableListOf<State>().also { told -> tiles.addListener { told += tiles.state } }

    /** The next job run, then its result on the main thread. */
    private fun prepareNext() {
        jobs.runNext()
        val queued = onMain.toList()
        onMain.clear()
        queued.forEach { it() }
    }

    private fun prepareAll() {
        while (jobs.pending > 0 || onMain.isNotEmpty()) prepareNext()
    }

    @Test
    fun `with every picture prepared, nothing is done and nothing told`() {
        tiles.prepare(setOf(sunset, forest), 1920, 1080)

        assertEquals(State.None, tiles.state)
        assertEquals(0, jobs.pending)
        assertEquals(emptyList<State>(), told)
    }

    @Test
    fun `prepares those that aren't, one job each, in the order given, saying why until the last is done`() {
        prepared.missing += listOf(sea, sunset)

        tiles.prepare(linkedSetOf(sunset, forest, sea), 1920, 1080)
        assertEquals(State.Preparing(PreparingCause.OUTDATED), tiles.state)
        assertEquals(listOf(JobPriority.PICTURE_SCREENS, JobPriority.PICTURE_SCREENS), jobs.pendingPriorities())
        assertTrue(tiles.preparing(sunset))
        assertFalse(tiles.preparing(forest))
        assertTrue(tiles.preparing(sea))

        prepareNext()
        assertEquals(listOf(sunset), prepared.prepared)
        assertFalse(tiles.preparing(sunset))
        assertTrue(tiles.preparing(sea))
        assertEquals(State.Preparing(PreparingCause.OUTDATED), tiles.state)

        prepareNext()
        assertEquals(listOf(sunset, sea), prepared.prepared)
        assertFalse(tiles.preparing(sea))
        assertEquals(State.None, tiles.state)
        // Told as it started, and as each was prepared: the home screen draws that one's tiles again.
        assertEquals(listOf(State.Preparing(PreparingCause.OUTDATED), State.Preparing(PreparingCause.OUTDATED), State.None), told)
    }

    @Test
    fun `a screen of another size makes every picture due again, saying so`() {
        tiles.prepare(setOf(sunset, forest), 1920, 1080)
        prepared.missing += listOf(sunset, forest)
        prepared.cause = PreparingCause.SCREEN_CHANGED

        tiles.prepare(setOf(sunset, forest), 1280, 720)

        assertEquals(State.Preparing(PreparingCause.SCREEN_CHANGED), tiles.state)
        prepareAll()
        assertEquals(listOf(sunset, forest), prepared.prepared)
        assertEquals(State.None, tiles.state)
    }

    @Test
    fun `asks nothing again for the same pictures on the same screen, as most returns to the home screen do`() {
        tiles.prepare(setOf(sunset, forest), 1920, 1080)
        val asked = prepared.asked

        tiles.prepare(setOf(sunset, forest), 1920, 1080)

        assertEquals(asked, prepared.asked)
    }

    @Test
    fun `a picture chosen for another tile is prepared, the others not again`() {
        tiles.prepare(setOf(sunset), 1920, 1080)
        prepared.missing += sea

        tiles.prepare(setOf(sunset, sea), 1920, 1080)

        assertTrue(tiles.preparing(sea))
        prepareAll()
        assertEquals(listOf(sea), prepared.prepared)
    }

    @Test
    fun `one that can't be prepared is given up, the rest go on, and it isn't tried again`() {
        prepared.missing += listOf(sunset, forest)
        prepared.failing += sunset

        tiles.prepare(linkedSetOf(sunset, forest), 1920, 1080)
        prepareNext()
        assertFalse(tiles.preparing(sunset))
        assertEquals(State.Preparing(PreparingCause.OUTDATED), tiles.state)
        prepareNext()
        assertEquals(listOf(forest), prepared.prepared)
        assertEquals(State.None, tiles.state)

        // Asked again, on another screen even: it stays as it is, the tile with its app's image.
        tiles.prepare(setOf(sunset, forest), 1280, 720)
        assertEquals(0, jobs.pending)
        assertEquals(State.None, tiles.state)
    }

    @Test
    fun `asked again while preparing, a picture isn't prepared twice`() {
        prepared.missing += sunset
        tiles.prepare(setOf(sunset), 1920, 1080)

        tiles.prepare(setOf(sunset, forest), 1920, 1080)

        assertEquals(1, jobs.pending)
    }

    @Test
    fun `with some for another screen and some outdated, it says they're outdated`() {
        prepared.missing += sunset
        prepared.cause = PreparingCause.SCREEN_CHANGED
        tiles.prepare(setOf(sunset), 1920, 1080)
        prepared.missing += forest
        prepared.cause = PreparingCause.OUTDATED

        tiles.prepare(setOf(sunset, forest), 1920, 1080)
        assertEquals(State.Preparing(PreparingCause.OUTDATED), tiles.state)

        prepareNext()   // the one for another screen; the outdated one is left
        assertEquals(State.Preparing(PreparingCause.OUTDATED), tiles.state)
    }

    @Test
    fun `a picture found damaged is prepared again at once, after the library's jobs, the one deleting it among them`() {
        tiles.prepare(setOf(sunset), 1920, 1080)
        jobs.run(JobPriority.PICTURE_LIBRARY) { prepared.missing += sunset }   // the damaged copy deleted

        tiles.damaged(sunset)

        assertEquals(State.Preparing(PreparingCause.OUTDATED), tiles.state)
        assertTrue(tiles.preparing(sunset))
        prepareAll()
        assertEquals(listOf(sunset), prepared.prepared)
        assertEquals(State.None, tiles.state)
    }

    @Test
    fun `a picture found damaged again after it was prepared again is given up, so it isn't made in a loop`() {
        tiles.damaged(sunset)
        prepareAll()

        tiles.damaged(sunset)

        assertEquals(0, jobs.pending)
        assertEquals(State.None, tiles.state)
        tiles.prepare(setOf(sunset), 1280, 720)
        assertEquals(0, jobs.pending)
    }

    @Test
    fun `a listener removed isn't told`() {
        val calls = mutableListOf<Unit>()
        val listener = { calls += Unit }
        tiles.addListener(listener)
        tiles.removeListener(listener)
        prepared.missing += sunset

        tiles.prepare(setOf(sunset), 1920, 1080)

        assertEquals(emptyList<Unit>(), calls)
    }
}
