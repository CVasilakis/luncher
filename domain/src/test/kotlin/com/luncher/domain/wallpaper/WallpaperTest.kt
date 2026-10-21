package com.luncher.domain.wallpaper

import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.jobs.ManualJobs
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.wallpaper.Wallpaper.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WallpaperTest {

    private val sunset = PictureId("sunset")
    private val forest = PictureId("forest")
    private val background = Background(sunset, dim = 20)
    private val drawing = WallpaperDrawing(sunset, PictureStyle(dim = 20), 1920, 1080)

    private val stored = FakeStoredWallpaper<String>()
    private val images = FakePictureImages { picture, width, height, style -> "${picture.value} ${width}x$height dim ${style.dim}" }
    private val jobs = ManualJobs()
    private val settings = FakeAppearanceSettings(Appearance(background = background))

    /** Results the main thread hasn't run yet, as a Handler would queue them. */
    private val onMain = mutableListOf<() -> Unit>()
    private val wallpaper = Wallpaper(stored, images, settings, jobs) { onMain += it }

    /** Every state the listeners were told of, in order. */
    private val told = mutableListOf<State<String>>().also { told -> wallpaper.addListener { told += wallpaper.state } }

    /** The jobs run, then their results on the main thread. */
    private fun drawInTheBackground() {
        jobs.runAll()
        val queued = onMain.toList()
        onMain.clear()
        queued.forEach { it() }
    }

    @Test
    fun `shows the one kept at once, read on the main thread, when it was drawn from what's wanted`() {
        stored.kept = drawing to "kept"

        wallpaper.show(background, 1920, 1080)

        assertEquals(State.Ready("kept"), wallpaper.state)
        assertEquals(0, jobs.pending)
    }

    @Test
    fun `draws one not kept in the background, saying so, then keeps it and shows it`() {
        wallpaper.show(background, 1920, 1080)
        assertEquals(State.Drawing(DrawingCause.OUTDATED), wallpaper.state)
        assertNull(stored.kept)

        drawInTheBackground()

        assertEquals(State.Ready("sunset 1920x1080 dim 20"), wallpaper.state)
        assertEquals(drawing to "sunset 1920x1080 dim 20", stored.kept)
        assertEquals(listOf(State.Drawing(DrawingCause.OUTDATED), State.Ready("sunset 1920x1080 dim 20")), told)
    }

    @Test
    fun `draws one kept for another screen size again, saying the screen changed`() {
        stored.kept = drawing.copy(width = 1280, height = 720) to "small"

        wallpaper.show(background, 1920, 1080)
        assertEquals(State.Drawing(DrawingCause.SCREEN_CHANGED), wallpaper.state)
        drawInTheBackground()

        assertEquals(State.Ready("sunset 1920x1080 dim 20"), wallpaper.state)
        assertEquals(drawing, stored.kept?.first)
    }

    @Test
    fun `draws again one whose header reads but whose pixels don't`() {
        stored.kept = drawing to "damaged"
        stored.unreadable = true

        wallpaper.show(background, 1920, 1080)
        assertEquals(State.Drawing(DrawingCause.OUTDATED), wallpaper.state)
        drawInTheBackground()

        assertEquals(State.Ready("sunset 1920x1080 dim 20"), wallpaper.state)
    }

    @Test
    fun `without a picture shows none, and deletes the one kept`() {
        stored.kept = drawing to "kept"

        wallpaper.show(Background(), 1920, 1080)
        assertEquals(State.None, wallpaper.state)
        drawInTheBackground()

        assertNull(stored.kept)
        assertEquals(emptyList<Any>(), images.rendered)
    }

    @Test
    fun `a picture that's gone is no wallpaper, and nothing of it is kept`() {
        stored.kept = drawing.copy(style = PictureStyle()) to "older"
        images.missing += sunset

        wallpaper.show(background, 1920, 1080)
        drawInTheBackground()

        assertEquals(State.None, wallpaper.state)
        assertNull(stored.kept)
    }

    @Test
    fun `asking again for what it's showing or drawing changes nothing`() {
        wallpaper.show(background, 1920, 1080)
        wallpaper.show(background, 1920, 1080)
        assertEquals(1, jobs.pending)
        drawInTheBackground()
        val reads = stored.reads

        wallpaper.show(background, 1920, 1080)

        assertEquals(0, jobs.pending)
        assertEquals(reads, stored.reads)
        assertEquals(2, told.size)
    }

    @Test
    fun `the latest request wins, the one asked for meanwhile shown and kept`() {
        wallpaper.show(background, 1920, 1080)
        wallpaper.show(Background(forest), 1920, 1080)

        drawInTheBackground()

        assertEquals(listOf(forest), images.rendered.map { it.first })   // the first one never started
        assertEquals(State.Ready("forest 1920x1080 dim 0"), wallpaper.state)
        assertEquals(forest, stored.kept?.first?.picture)
    }

    @Test
    fun `a drawing that started runs to its end, and the latest request still wins after it`() {
        wallpaper.show(background, 1920, 1080)
        jobs.runAll()                                // sunset drawn and kept, its result not shown yet
        wallpaper.show(Background(forest), 1920, 1080)

        drawInTheBackground()

        assertEquals(State.Ready("forest 1920x1080 dim 0"), wallpaper.state)
        assertEquals(listOf(drawing, WallpaperDrawing(forest, PictureStyle(), 1920, 1080)), stored.stored)
        assertEquals(forest, stored.kept?.first?.picture)
    }

    @Test
    fun `no wallpaper asked for while one is drawn leaves none kept`() {
        wallpaper.show(background, 1920, 1080)
        jobs.runAll()
        wallpaper.show(Background(), 1920, 1080)

        drawInTheBackground()

        assertEquals(State.None, wallpaper.state)
        assertNull(stored.kept)
    }

    @Test
    fun `a listener removed is told nothing more`() {
        var calls = 0
        val listener = { calls++; Unit }
        wallpaper.addListener(listener)
        wallpaper.show(background, 1920, 1080)
        wallpaper.removeListener(listener)

        drawInTheBackground()

        assertEquals(1, calls)
    }

    // Saving, from the background editor.

    @Test
    fun `saving draws and keeps the picture first, then stores the background, then says it's done`() {
        val chosen = Background(forest, dim = 40)
        var done = 0

        wallpaper.save(chosen, 1920, 1080) { done++ }
        assertEquals(State.Drawing(DrawingCause.SAVING), wallpaper.state)
        assertEquals("not stored before it's drawn", background, settings.appearance.background)
        jobs.runAll()
        assertEquals("still not stored before its result is back", background, settings.appearance.background)
        assertEquals(WallpaperDrawing(forest, PictureStyle(dim = 40), 1920, 1080), stored.kept?.first)

        drawInTheBackground()

        assertEquals(chosen, settings.appearance.background)
        assertEquals(State.Ready("forest 1920x1080 dim 40"), wallpaper.state)
        assertEquals(1, done)
    }

    @Test
    fun `while saving, the home screen asking for the background it replaces stops nothing`() {
        val chosen = Background(forest)
        wallpaper.save(chosen, 1920, 1080) {}

        wallpaper.show(background, 1920, 1080)
        drawInTheBackground()

        assertEquals(listOf(forest), images.rendered.map { it.first })
        assertEquals(chosen, settings.appearance.background)
        wallpaper.show(chosen, 1920, 1080)
        assertEquals(State.Ready("forest 1920x1080 dim 0"), wallpaper.state)
    }

    @Test
    fun `saving no picture stores it at once, and deletes the one kept`() {
        stored.kept = drawing to "kept"
        var done = 0

        wallpaper.save(Background(dim = 40), 1920, 1080) { done++ }

        assertEquals(Background(dim = 40), settings.appearance.background)
        assertEquals(State.None, wallpaper.state)
        assertEquals(1, done)
        drawInTheBackground()
        assertNull(stored.kept)
    }

    @Test
    fun `saving what's kept already stores it at once, drawing nothing`() {
        stored.kept = WallpaperDrawing(forest, PictureStyle(), 1920, 1080) to "kept forest"
        var done = 0

        wallpaper.save(Background(forest), 1920, 1080) { done++ }

        assertEquals(Background(forest), settings.appearance.background)
        assertEquals(State.Ready("kept forest"), wallpaper.state)
        assertEquals(1, done)
        assertEquals(0, jobs.pending)
    }

    @Test
    fun `saving a picture that's gone meanwhile changes nothing, the stored background's showing, kept`() {
        stored.kept = drawing to "kept"
        images.missing += forest
        var done = 0

        wallpaper.save(Background(forest), 1920, 1080) { done++ }
        drawInTheBackground()

        assertEquals(background, settings.appearance.background)
        assertEquals(1, done)
        assertEquals(State.Ready("kept"), wallpaper.state)
        assertEquals(drawing to "kept", stored.kept)
    }

    // What a preview draws at once (readyFor).

    @Test
    fun `gives a preview the image it shows, for its background only`() {
        stored.kept = drawing to "kept"
        wallpaper.show(background, 1920, 1080)

        assertEquals("kept", wallpaper.readyFor(background))
        assertNull("another style", wallpaper.readyFor(background.copy(dim = 40)))
        assertNull("another picture", wallpaper.readyFor(background.copy(picture = forest)))
        assertNull("no picture", wallpaper.readyFor(Background()))
    }

    @Test
    fun `gives a preview nothing while it's drawn, nor before the home screen asked`() {
        assertNull(wallpaper.readyFor(background))

        wallpaper.show(background, 1920, 1080)
        assertNull(wallpaper.readyFor(background))

        drawInTheBackground()
        assertEquals("sunset 1920x1080 dim 20", wallpaper.readyFor(background))
    }

    @Test
    fun `gives a preview the background saved once it's drawn, not the one it replaces meanwhile`() {
        stored.kept = drawing to "kept"
        wallpaper.show(background, 1920, 1080)
        val forestBackground = Background(forest)

        wallpaper.save(forestBackground, 1920, 1080) {}
        assertNull(wallpaper.readyFor(background))
        assertNull(wallpaper.readyFor(forestBackground))

        drawInTheBackground()
        assertEquals("forest 1920x1080 dim 0", wallpaper.readyFor(forestBackground))
        assertNull(wallpaper.readyFor(background))
    }

    // Deleting pictures.

    @Test
    fun `forgetting the wallpaper's picture stores no picture at once, then deletes the one kept`() {
        stored.kept = drawing to "kept"
        wallpaper.show(background, 1920, 1080)

        wallpaper.forget(setOf(sunset))

        assertNull(settings.appearance.background.picture)
        assertEquals("the rest of the background stays", 20, settings.appearance.background.dim)
        assertEquals(State.None, wallpaper.state)
        jobs.runAll()
        assertNull(stored.kept)
    }

    @Test
    fun `forgetting other pictures changes nothing`() {
        stored.kept = drawing to "kept"
        wallpaper.show(background, 1920, 1080)

        wallpaper.forget(setOf(forest))

        assertEquals(background, settings.appearance.background)
        assertEquals(State.Ready("kept"), wallpaper.state)
        assertEquals(0, jobs.pending)
    }
}
