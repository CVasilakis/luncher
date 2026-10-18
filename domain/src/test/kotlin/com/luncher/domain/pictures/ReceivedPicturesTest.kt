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
        assertEquals(Outcome.Several(saved = 2, notAdded = 0, tooMany = false), received.outcome)
    }

    @Test
    fun `one picture ends with its own answer, as the window always said it`() {
        library.results["text"] = ImportResult.NotAPicture
        received.receive(listOf("text"))
        library.finishAdding()

        assertEquals(Outcome.One(ImportResult.NotAPicture), received.outcome)
    }

    @Test
    fun `counts a picture the library had already as saved, and the others as not added`() {
        received.receive(listOf("beach", "known", "text", "unknown"))
        repeat(4) { library.finishAdding() }

        assertEquals(Outcome.Several(saved = 2, notAdded = 2, tooMany = false), received.outcome)
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
        assertEquals(Outcome.Several(saved = 2, notAdded = 1, tooMany = false), received.outcome)
    }

    @Test
    fun `a share that comes while it says how it went joins it too, and is added`() {
        received.receive(listOf("beach"))
        library.finishAdding()

        received.receive(listOf("forest"))

        assertNull(received.outcome)
        library.finishAdding()
        assertEquals(Outcome.Several(saved = 2, notAdded = 0, tooMany = false), received.outcome)
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
        assertEquals(Outcome.Several(saved = 2, notAdded = 0, tooMany = false), received.outcome)
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
        assertEquals(Outcome.Several(saved = 0, notAdded = 0, tooMany = true), received.outcome)
    }

    @Test
    fun `a share of too many pictures while others are added leaves them be, and the end says so`() {
        received.receive(listOf("beach"))

        received.refuse()

        assertNull(received.outcome)
        library.finishAdding()
        assertEquals(Outcome.Several(saved = 1, notAdded = 0, tooMany = true), received.outcome)
    }

    @Test
    fun `tells the listeners of each change`() {
        received.receive(listOf("beach", "forest"))
        assertEquals(1, told)

        library.finishAdding()
        library.finishAdding()
        assertEquals(3, told)

        received.end(received.holder)
        assertEquals(4, told)
    }

    @Test
    fun `an import that answers at once adds a hundred pictures without nesting a call per picture`() {
        library.answerLater = false
        val sources = (1..100).map { "picture $it" }

        received.receive(sources)

        assertEquals(sources, library.added)
        assertEquals(Outcome.Several(saved = 0, notAdded = 100, tooMany = false), received.outcome)
    }
}
