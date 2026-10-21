package com.luncher.domain.pictures

import com.luncher.domain.pictures.FakePictures.Companion.picture
import com.luncher.domain.pictures.ReceivedPictures.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceivedPicturesTest {

    private val beach = picture("beach.jpg")
    private val forest = picture("forest.jpg")

    /** The library: each source a name, its answer set by the test; answers wait for [FakePictures.finishAdding]. */
    private val library = FakePictures<String>().apply {
        results["beach"] = ImportResult.Added(beach)
        results["forest"] = ImportResult.Added(forest)
        results["known"] = ImportResult.AlreadyThere(beach)
        results["text"] = ImportResult.NotAPicture
        results["same name"] = ImportResult.SameFile(beach)
        results["name taken"] = ImportResult.NameTaken(forest)
        results["folder"] = ImportResult.Folder
        results["other folder"] = ImportResult.Folder
        answerLater = true
    }
    private val received = ReceivedPictures(library)

    /** How many times the listeners were told. */
    private var told = 0

    init {
        received.addListener { told++ }
    }

    @Test
    fun `adds the pictures one at a time, in the order they came`() {
        received.receive(listOf("beach", "forest", "text"))
        assertEquals(listOf("beach"), library.added)

        library.finishAdding()
        assertEquals(listOf("beach", "forest"), library.added)
        assertEquals(1, received.answered)
        assertEquals(3, received.total)

        library.finishAdding()
        library.finishAdding()
        assertEquals(listOf("beach", "forest", "text"), library.added)
        assertEquals(3, received.answered)
        assertEquals(listOf(beach, forest), library.pictures)
    }

    @Test
    fun `says how it went only once every picture has its answer`() {
        received.receive(listOf("beach", "forest"))
        library.finishAdding()

        assertNull(received.outcome)

        library.finishAdding()
        assertEquals(Outcome.Several(listOf(ImportResult.Added(beach), ImportResult.Added(forest))), received.outcome)
    }

    @Test
    fun `one picture ends with its own answer, as the window always said it`() {
        library.results["text"] = ImportResult.NotAPicture
        received.receive(listOf("text"))
        library.finishAdding()

        assertEquals(Outcome.One(ImportResult.NotAPicture), received.outcome)
    }

    @Test
    fun `says each picture's answer, in the order they came`() {
        received.receive(listOf("beach", "known", "text", "unknown"))
        repeat(4) { library.finishAdding() }

        val outcome = received.outcome as Outcome.Several
        assertEquals(listOf(ImportResult.Added(beach), ImportResult.AlreadyThere(beach), ImportResult.NotAPicture, ImportResult.NotAPicture), outcome.results)
        assertEquals(1, outcome.added)
    }

    @Test
    fun `counts only the pictures added now as added, not those the library had`() {
        received.receive(listOf("beach", "known", "same name", "name taken"))
        repeat(4) { library.finishAdding() }

        assertEquals(1, (received.outcome as Outcome.Several).added)
        assertEquals(1, received.added)
    }

    @Test
    fun `a share that comes while it adds joins the session, its pictures after the others`() {
        received.receive(listOf("beach", "text"))
        val session = received.session
        received.receive(listOf("forest"))

        assertEquals(session, received.session)
        assertEquals(3, received.total)
        repeat(3) { library.finishAdding() }
        assertEquals(listOf("beach", "text", "forest"), library.added)
        assertEquals(Outcome.Several(listOf(ImportResult.Added(beach), ImportResult.NotAPicture, ImportResult.Added(forest))), received.outcome)
    }

    @Test
    fun `a share that comes while it says how it went joins it too, and is added`() {
        received.receive(listOf("beach"))
        library.finishAdding()

        received.receive(listOf("forest"))

        assertNull(received.outcome)
        library.finishAdding()
        assertEquals(Outcome.Several(listOf(ImportResult.Added(beach), ImportResult.Added(forest))), received.outcome)
    }

    @Test
    fun `adds a picture the session has already only once`() {
        received.receive(listOf("beach", "beach"))
        received.receive(listOf("beach", "forest"))

        assertEquals(2, received.total)
        library.finishAdding()
        library.finishAdding()
        assertEquals(listOf("beach", "forest"), library.added)
    }

    @Test
    fun `ending it cancels the picture being added and those after it, and keeps those added`() {
        val session = received.receive(listOf("beach", "forest", "text"))
        library.finishAdding()

        received.end(session)

        assertEquals(listOf("forest"), library.cancelled)
        assertEquals(listOf("beach", "forest"), library.added)
        assertEquals(listOf(beach), library.pictures)
        assertEquals(0, received.session)
        assertEquals(0, received.total)
        assertNull(received.outcome)
    }

    @Test
    fun `the next share after the end opens a session of its own`() {
        val first = received.receive(listOf("beach"))
        library.finishAdding()
        received.end(first)

        val second = received.receive(listOf("forest"))

        assertNotEquals(first, second)
        assertEquals(1, received.total)
        library.finishAdding()
        assertEquals(Outcome.One(ImportResult.Added(forest)), received.outcome)
    }

    @Test
    fun `a window that ends its session late doesn't stop the session after it`() {
        val first = received.receive(listOf("beach"))
        received.end(first)
        val second = received.receive(listOf("forest"))

        received.end(first)

        assertEquals(second, received.session)
        assertEquals(listOf("forest"), library.added.drop(1))
        library.finishAdding()
        assertEquals(Outcome.One(ImportResult.Added(forest)), received.outcome)
    }

    @Test
    fun `a window that took the session over holds it, and the window before can't end it`() {
        val older = received.receive(listOf("beach"))
        val newer = received.receive(listOf("forest"))

        received.end(older)

        assertEquals(newer, received.holder)
        library.finishAdding()
        library.finishAdding()
        assertEquals(Outcome.Several(listOf(ImportResult.Added(beach), ImportResult.Added(forest))), received.outcome)
        received.end(newer)
        assertEquals(0, received.session)
    }

    @Test
    fun `a window made anew takes the session over with hold, and only it ends it then`() {
        val before = received.receive(listOf("beach", "forest"))

        val after = received.hold()
        received.end(before)

        assertEquals(2, received.total)
        received.end(after)
        assertEquals(listOf("beach"), library.cancelled)
        assertEquals(0, received.hold())
    }

    @Test
    fun `tells the pictures still to add, the one being added first`() {
        received.receive(listOf("beach", "forest", "text"))
        library.finishAdding()

        assertEquals(listOf("forest", "text"), received.pending)
    }

    @Test
    fun `a share of too many pictures adds none, and says so`() {
        received.refuse()

        assertEquals(emptyList<String>(), library.added)
        assertEquals(Outcome.Several(emptyList(), tooMany = true), received.outcome)
    }

    @Test
    fun `a share of too many pictures while others are added leaves them be, and the end says so`() {
        received.receive(listOf("beach"))

        received.refuse()

        assertNull(received.outcome)
        library.finishAdding()
        assertEquals(Outcome.Several(listOf(ImportResult.Added(beach)), tooMany = true), received.outcome)
    }

    @Test
    fun `tells the listeners of each change`() {
        received.receive(listOf("beach", "forest"))
        assertEquals("beach copied, both received", 2, told)

        library.finishAdding()
        assertEquals("beach's answer, forest copied", 4, told)
        library.finishAdding()
        assertEquals(5, told)

        received.end(received.holder)
        assertEquals(6, told)
    }

    @Test
    fun `says a picture is copied once its import says so, until its answer`() {
        received.receive(listOf("beach"))
        assertEquals(true, received.copying)

        library.finishAdding()

        assertEquals(false, received.copying)
    }

    @Test
    fun `a picture refused for its name or size, before any copying, is never said to be copied`() {
        library.refusedAtOnce += "same name"

        received.receive(listOf("same name"))

        assertEquals(false, received.copying)
        library.finishAdding()
        assertEquals(Outcome.One(ImportResult.SameFile(beach)), received.outcome)
    }

    @Test
    fun `an import that answers at once adds a hundred pictures without nesting a call per picture`() {
        library.answerLater = false
        val sources = (1..100).map { "picture $it" }

        received.receive(sources)

        assertEquals(sources, library.added)
        assertEquals(Outcome.Several(List(100) { ImportResult.NotAPicture }), received.outcome)
    }

    @Test
    fun `stopping cancels the picture being added and those after it, keeps those added, and says how it went`() {
        val hold = received.receive(listOf("beach", "text", "forest", "known"))
        library.finishAdding()
        library.finishAdding()

        received.stop()

        assertEquals(listOf("forest"), library.cancelled)
        assertEquals(listOf(beach), library.pictures)
        assertEquals(hold, received.holder)
        assertEquals(emptyList<String>(), received.pending)
        assertEquals(false, received.copying)
        assertEquals(Outcome.Several(listOf(ImportResult.Added(beach), ImportResult.NotAPicture), cancelled = 2), received.outcome)
    }

    @Test
    fun `stopping one picture says it was cancelled, not how it went alone`() {
        received.receive(listOf("beach"))

        received.stop()

        assertEquals(Outcome.Several(emptyList(), cancelled = 1), received.outcome)
    }

    @Test
    fun `a share that comes after stopping is added, and the end counts both`() {
        received.receive(listOf("beach", "text"))
        library.finishAdding()
        received.stop()

        received.receive(listOf("forest"))

        assertNull(received.outcome)
        library.finishAdding()
        assertEquals(listOf("beach", "text", "forest"), library.added)
        assertEquals(Outcome.Several(listOf(ImportResult.Added(beach), ImportResult.Added(forest)), cancelled = 1), received.outcome)
    }

    @Test
    fun `stopping with no session does nothing`() {
        received.stop()

        assertEquals(0, received.session)
        assertEquals(0, told)
    }

    @Test
    fun `a folder shared with the pictures is left out, neither added nor counted`() {
        received.receive(listOf("beach", "folder", "forest"))
        assertEquals(3, received.total)

        library.finishAdding()
        library.finishAdding()
        assertEquals(2, received.total)
        assertEquals(1, received.answered)
        assertEquals(listOf("forest"), received.pending)

        library.finishAdding()
        assertEquals(Outcome.Several(listOf(ImportResult.Added(beach), ImportResult.Added(forest))), received.outcome)
    }

    @Test
    fun `one picture shared with folders ends with its own answer`() {
        received.receive(listOf("folder", "text", "other folder"))
        repeat(3) { library.finishAdding() }

        assertEquals(Outcome.One(ImportResult.NotAPicture), received.outcome)
    }

    @Test
    fun `a share of folders only has no picture to add`() {
        received.receive(listOf("folder", "other folder"))
        library.finishAdding()

        assertNull(received.outcome)

        library.finishAdding()
        assertEquals(Outcome.Nothing, received.outcome)
        assertEquals(0, received.total)
    }

    @Test
    fun `a folder answered at once doesn't stop the adding`() {
        val atOnce = FakePictures<String>().apply {
            results["folder"] = ImportResult.Folder
            results["beach"] = ImportResult.Added(beach)
        }
        val received = ReceivedPictures(atOnce)

        received.receive(listOf("folder", "beach"))

        assertEquals(listOf("folder", "beach"), atOnce.added)
        assertEquals(Outcome.One(ImportResult.Added(beach)), received.outcome)
    }

    @Test
    fun `folders and a share of too many say the share was too many`() {
        received.receive(listOf("folder"))
        library.finishAdding()
        received.refuse()

        assertEquals(Outcome.Several(emptyList(), tooMany = true), received.outcome)
    }
}
