package com.luncher.launcher.pictures

import android.graphics.Color
import android.net.Uri
import android.os.Looper
import com.luncher.domain.jobs.ManualJobs
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PictureFormat
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureLimits
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.pictures.SizeLimit
import com.luncher.domain.pictures.isPrepared
import com.luncher.launcher.ui.ScreenSize
import com.luncher.launcher.outside.AnotherApp
import com.luncher.launcher.outside.OutsideCalls
import com.luncher.launcher.outside.OutsideReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // to make and decode pictures
class FilesPicturesTest {

    private val context = RuntimeEnvironment.getApplication()
    private val jobs = ManualJobs()
    private val folder = File(context.filesDir, "pictures")
    private val images = File(context.cacheDir, "thumbnails")
    private val copies = File(context.filesDir, "picture-copies")

    /** The screen the working copies are made for: half of it is 960 x 540 px. */
    private val screen = ScreenSize(1920, 1080)
    private var time = 1_000L

    /** The drop folder on the TV's storage (Robolectric has no storage volumes to ask). */
    private val dropFolder = File(context.getExternalFilesDir(null)!!.parentFile!!.parentFile, "../media/${context.packageName}")
        .canonicalFile.apply { mkdirs() }
    /** The time another app takes, in ms, apart from [time]: it passes only as the tests say. */
    private var waited = 0L

    /** What waiting [ms] for another app's data does: by default, the data is there. */
    private var waitForData: (ms: Int) -> Boolean = { true }

    private val outsideCalls = OutsideCalls(now = { waited }, waitForData = { _, ms -> waitForData(ms) })
    private val anotherApp = AnotherApp.install()
    private var library = newLibrary()

    /** How often a working copy's size was asked for: once for each picture decoded into one. */
    private var decodes = 0

    private fun newLibrary() = FilesPictures(context, jobs, outsideCalls, now = { time }, dropFolders = { listOf(dropFolder) }, copies = copies, screen = { decodes++; screen })

    /** The screens' images on the library's working copies, as AppGraph makes them. */
    private val prepared = WorkingCopyImages(context, folder, copies) { screen }

    private val sunset = Picture(PictureId("0123456789abcdef0123456789abcdef"), "sunset.jpg", 1920, 1080, 3_100_000, 1_700_000_000_000)
    private val beach = Picture(PictureId("fedcba9876543210fedcba9876543210"), "beach.png", 1080, 1920, 250_000, 1_700_000_001_000)

    // The stored format, written out: a TV keeps the index across updates, so a change of it has to
    // read this one too. The other tests add and read back, which a change of both sides would pass.
    private val storedIndex = """{"format":1,"pictures":[""" +
        """{"id":"0123456789abcdef0123456789abcdef","name":"sunset.jpg","width":1920,"height":1080,"bytes":3100000,"added":1700000000000},""" +
        """{"id":"fedcba9876543210fedcba9876543210","name":"beach.png","width":1080,"height":1920,"bytes":250000,"added":1700000001000}]}"""

    /** As the next start: a new process, with what the files hold. */
    private fun restart() {
        library = newLibrary()
    }

    /** Everything queued done: the jobs, and the main thread's answers, until nothing's left. */
    private fun settle() {
        repeat(5) {
            jobs.runAll()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    /** Adds [source] and settles: what the library answered. */
    private fun add(source: Uri): ImportResult {
        var result: ImportResult? = null
        library.add(source) { result = it }
        settle()
        return checkNotNull(result) { "no answer" }
    }

    /** A file named [name] with [bytes] in the drop folder on the TV's storage, as a file manager puts it there. */
    private fun dropped(name: String, bytes: ByteArray): Uri = Uri.fromFile(File(dropFolder, name).apply { writeBytes(bytes) })

    /** A picture another app shares, as content://, named [name] by its query. */
    private fun shared(name: String, bytes: ByteArray): Uri = anotherApp.sends(name, bytes)

    private val red = TestPictureFiles.png(TestPictureFiles.halves(64, 36, Color.RED, Color.RED))
    private val blue = TestPictureFiles.png(TestPictureFiles.halves(64, 36, Color.BLUE, Color.BLUE))

    @Test
    fun `reads the stored format`() {
        folder.mkdirs()
        File(folder, "index.json").writeText(storedIndex)

        assertEquals(listOf(sunset, beach), library.read())
    }

    @Test
    fun `writes the stored format`() {
        folder.mkdirs()
        File(folder, "index.json").writeText(storedIndex.replace("}]}", "},{\"id\":\"gone\",\"name\":\"\",\"width\":1,\"height\":1,\"bytes\":1,\"added\":1}]}"))

        library.delete(setOf(PictureId("gone")))
        settle()

        assertEquals(storedIndex, File(folder, "index.json").readText())
    }

    @Test
    fun `leaves out an entry it can't read, keeping the others`() {
        folder.mkdirs()
        File(folder, "index.json").writeText(storedIndex.replace("\"name\":\"beach.png\",", "") .replace("}]}", "},{\"id\":\"x\",\"future\":true}]}"))

        assertEquals(listOf(sunset), library.read())
    }

    @Test
    fun `has no pictures at first`() {
        assertEquals(emptyList<Picture>(), library.read())
    }

    @Test
    fun `adds a shared picture, its size, name and file, and keeps it after a restart`() {
        val heard = mutableListOf<Int>()
        library.addListener { heard += library.read().size }
        time = 42

        val result = add(shared("holiday.png", red))

        val picture = (result as ImportResult.Added).picture
        assertEquals("holiday.png", picture.name)
        assertEquals(64, picture.width)
        assertEquals(36, picture.height)
        assertEquals(red.size.toLong(), picture.bytes)
        assertEquals(42L, picture.added)
        assertEquals(listOf(1), heard)
        assertTrue(File(folder, picture.id.value).readBytes().contentEquals(red))
        restart()
        assertEquals(listOf(picture), library.read())
    }

    @Test
    fun `adds a picture from the drop folder`() {
        val result = add(dropped("blue.png", blue))

        assertEquals("blue.png", (result as ImportResult.Added).picture.name)
    }

    @Test
    fun `stores the same picture once, saying it was there`() {
        val first = (add(shared("a.png", red)) as ImportResult.Added).picture

        val again = add(dropped("copy of a.png", red))

        assertEquals(ImportResult.AlreadyThere(first), again)
        assertEquals(listOf(first), library.read())
        assertEquals(listOf(first.id.value, "index.json"), folder.list()!!.sorted())
    }

    @Test
    fun `refuses a file named as one of the library, saying whether it's the same file, before copying it`() {
        val first = (add(dropped("a.png", red)) as ImportResult.Added).picture

        assertEquals(ImportResult.SameFile(first), add(dropped("a.png", red)))
        val other = TestPictureFiles.png(TestPictureFiles.halves(80, 40, Color.GREEN, Color.GREEN))
        assertEquals(ImportResult.NameTaken(first), add(dropped("a.png", other)))

        assertEquals(listOf(first), library.read())
        assertEquals(listOf(first.id.value, "index.json"), folder.list()!!.sorted())
    }

    @Test
    fun `refuses a shared picture named as one of the library once copied, when its app doesn't say its size`() {
        val first = (add(dropped("a.png", red)) as ImportResult.Added).picture

        assertEquals(ImportResult.SameFile(first), add(shared("a.png", red)))
        assertEquals(ImportResult.NameTaken(first), add(shared("a.png", TestPictureFiles.png(TestPictureFiles.halves(80, 40, Color.GREEN, Color.GREEN)))))
        assertEquals(listOf(first.id.value, "index.json"), folder.list()!!.sorted())
    }

    @Test
    fun `names differing only in case are different pictures`() {
        add(dropped("a.png", red))

        assertTrue(add(shared("A.PNG", blue)) is ImportResult.Added)
    }

    @Test
    fun `of two pictures of one name added at once, the second is refused`() {
        val results = mutableListOf<ImportResult>()
        library.add(shared("a.png", red)) { results += it }
        library.add(dropped("a.png", TestPictureFiles.png(TestPictureFiles.halves(80, 40, Color.GREEN, Color.GREEN)))) { results += it }
        settle()

        val added = (results[0] as ImportResult.Added).picture
        assertEquals(listOf(ImportResult.Added(added), ImportResult.NameTaken(added)), results)
        assertEquals(listOf(added), library.read())
    }

    @Test
    fun `the same picture added twice at once is stored once`() {
        val results = mutableListOf<ImportResult>()
        library.add(shared("a.png", red)) { results += it }
        library.add(dropped("a.png", red)) { results += it }
        settle()

        val added = (results[0] as ImportResult.Added).picture
        assertEquals(listOf(ImportResult.Added(added), ImportResult.AlreadyThere(added)), results)
        assertEquals(1, library.read().size)
    }

    @Test
    fun `turns a photo taken on its side upright`() {
        val photo = TestPictureFiles.jpeg(TestPictureFiles.halves(80, 40, Color.RED, Color.BLUE), orientation = 6)

        val picture = (add(dropped("photo.jpg", photo)) as ImportResult.Added).picture

        assertEquals(40, picture.width)
        assertEquals(80, picture.height)
    }

    @Test
    fun `refuses what isn't a picture`() {
        assertEquals(ImportResult.NotAPicture, add(shared("notes.txt", "not a picture".toByteArray())))
        assertEquals(ImportResult.NotAPicture, add(shared("empty.png", ByteArray(0))))
        assertEquals(ImportResult.NotAPicture, add(shared("header only.png", red.copyOf(8))))
        assertEquals(emptyList<Picture>(), library.read())
    }

    @Test
    fun `refuses a picture cut short or damaged as not a picture, which its decoder would draw in part`() {
        val photo = TestPictureFiles.jpeg(TestPictureFiles.stacked(320, 180, Color.RED, Color.BLUE), orientation = 1)
        val damaged = red.copyOf().apply { this[size - 20] = (this[size - 20].toInt() xor 0x10).toByte() }   // in the image data

        assertEquals(ImportResult.NotAPicture, add(shared("cut.jpg", photo.copyOf(photo.size * 2 / 3))))
        assertEquals(ImportResult.NotAPicture, add(dropped("damaged.png", damaged)))
        assertEquals(emptyList<Picture>(), library.read())
    }

    @Test
    fun `names a format this TV can't show`() {
        assertEquals(ImportResult.UnsupportedFormat(PictureFormat.HEIF), add(shared("IMG_0001.HEIC", TestPictureFiles.heic())))
    }

    @Test
    fun `refuses a file over 50 MB, without reading it all`() {
        // Twice the limit, of nothing: a sparse file takes no room.
        val huge = File.createTempFile("huge", null, context.cacheDir).apply { RandomAccessFile(this, "rw").use { it.setLength(PictureLimits.MAX_BYTES * 2) } }
        var reads = 0
        waitForData = {
            reads++   // one wait before each read
            true
        }

        assertEquals(ImportResult.TooLarge(SizeLimit.BYTES), add(anotherApp.sends("huge.png", huge)))
        assertTrue("$reads reads", reads <= PictureLimits.MAX_BYTES / (64 * 1024) + 2)
        assertEquals(emptyList<String>(), copiesLeft())
    }

    @Test
    fun `refuses a picture of more than 64 megapixels`() {
        assertEquals(ImportResult.TooLarge(SizeLimit.PIXELS), add(shared("huge.png", TestPictureFiles.pngHeader(8001, 8000))))
    }

    @Test
    fun `refuses a path other than its own drop folders'`() {
        val elsewhere = File(context.filesDir, "elsewhere.png").apply { writeBytes(red) }

        assertEquals(ImportResult.Unreadable, add(Uri.fromFile(elsewhere)))
        assertEquals(ImportResult.Unreadable, add(Uri.parse("file:///sdcard/Pictures/sunset.jpg")))
    }

    @Test
    fun `says it can't read what no app gives`() {
        assertEquals(ImportResult.Unreadable, add(Uri.parse("content://com.example.gone/1")))
        assertEquals(ImportResult.Unreadable, add(Uri.parse("http://example.com/a.png")))
    }

    @Test
    fun `leaves no copy behind when a picture is refused`() {
        add(shared("notes.txt", "not a picture".toByteArray()))
        add(shared("huge.png", TestPictureFiles.pngHeader(9000, 9000)))

        assertEquals(emptyList<String>(), folder.list().orEmpty().toList())
        assertEquals(emptyList<String>(), copies.list().orEmpty().toList())
    }

    @Test
    fun `makes a picture's working copy as it adds it, covering half the screen, so the screens needn't decode it`() {
        val big = TestPictureFiles.png(TestPictureFiles.halves(3200, 1200, Color.RED, Color.BLUE))

        val picture = (add(dropped("wide.png", big)) as ImportResult.Added).picture

        assertTrue(prepared.isPrepared(picture.id))
        assertEquals(listOf(picture.id.value), copies.list()!!.toList())
        val preview = prepared.render(picture.id, 96, 54, PictureStyle()) { false }!!
        assertTrue(TestPictureFiles.isRed(preview.getPixel(10, 27)))
        assertTrue(TestPictureFiles.isBlue(preview.getPixel(86, 27)))
    }

    @Test
    fun `a picture refused, or cancelled, leaves no working copy`() {
        val first = (add(dropped("a.png", red)) as ImportResult.Added).picture
        add(dropped("copy of a.png", red))                       // the same content
        add(shared("a.png", TestPictureFiles.png(TestPictureFiles.halves(80, 40, Color.GREEN, Color.GREEN))))   // its name
        var answered = false
        library.add(dropped("c.png", blue)) { answered = true }.cancel()
        settle()

        assertFalse(answered)
        assertEquals(listOf(first.id.value), copies.list()!!.toList())
    }

    @Test
    fun `refuses a picture whose working copy can't be written, as for a full disk, leaving nothing behind`() {
        val first = (add(dropped("a.png", red)) as ImportResult.Added).picture
        val index = File(folder, "index.json").readText()
        // EACCES, not ENOSPC, which Robolectric can't make: the copy's writing fails alike.
        copies.setWritable(false)
        val result = try {
            add(shared("b.png", blue))
        } finally {
            copies.setWritable(true)
        }

        assertEquals(ImportResult.NoSpace, result)
        assertEquals(listOf(first), library.read())
        assertEquals(index, File(folder, "index.json").readText())
        assertEquals(listOf(first.id.value, "index.json"), folder.list()!!.sorted())
        assertEquals(listOf(first.id.value), copies.list()!!.toList())
    }

    @Test
    fun `refuses a picture whose working copy can't be put in place, leaving nothing behind`() {
        val obstacle = obstructCopy(blue)

        assertEquals(ImportResult.Unreadable, add(shared("b.png", blue)))
        assertEquals(emptyList<Picture>(), library.read())
        assertEquals(emptyList<String>(), folder.list().orEmpty().filter { it != "index.json" })
        assertEquals(listOf(obstacle.name), copies.list()!!.toList())
    }

    @Test
    fun `a picture whose working copy can't be put in place leaves its file if it was there before`() {
        library.read()
        settle()   // the start's cleanup done
        val id = idOf(blue)
        File(folder, id).apply { parentFile!!.mkdirs() }.writeBytes(blue)   // as the same picture added meanwhile
        obstructCopy(blue)

        assertEquals(ImportResult.Unreadable, add(shared("b.png", blue)))
        assertEquals(emptyList<Picture>(), library.read())
        assertEquals(listOf(id), folder.list().orEmpty().filter { it != "index.json" })
    }

    @Test
    fun `deletes pictures, their files and their drawn images, telling the listeners`() {
        val a = (add(shared("a.png", red)) as ImportResult.Added).picture
        val b = (add(shared("b.png", blue)) as ImportResult.Added).picture
        File(images, a.id.value).apply { parentFile!!.mkdirs(); writeText("drawn") }
        assertTrue(prepared.isPrepared(a.id))
        var heard = 0
        library.addListener { heard++ }

        library.delete(setOf(a.id))
        assertEquals("at once", listOf(b), library.read())
        settle()

        assertEquals(1, heard)
        assertFalse(File(folder, a.id.value).exists())
        assertFalse(File(images, a.id.value).exists())
        assertFalse(File(copies, a.id.value).exists())
        assertTrue(File(folder, b.id.value).exists())
        assertTrue(prepared.isPrepared(b.id))
        restart()
        assertEquals(listOf(b), library.read())
    }

    @Test
    fun `renames a picture in the index only, keeping its files, working copy and thumbnail for good`() {
        val a = (add(dropped("a.png", red)) as ImportResult.Added).picture
        File(images, a.id.value).apply { parentFile!!.mkdirs(); writeText("drawn") }
        val files = listOf(File(folder, a.id.value), File(copies, a.id.value), File(images, a.id.value))
        val written = files.map { it.lastModified() to it.length() }
        var heard = 0
        library.addListener { heard++ }

        library.rename(a.id, "Red wall")
        assertEquals("at once", listOf(a.copy(name = "Red wall")), library.read())
        settle()
        restart()
        library.read()
        settle()   // the start's cleanup

        assertEquals(1, heard)
        assertEquals(listOf(a.copy(name = "Red wall")), library.read())
        assertEquals(written, files.map { it.lastModified() to it.length() })
        assertTrue(prepared.isPrepared(a.id))
    }

    @Test
    fun `renaming to the same name, or what isn't there, changes nothing`() {
        val a = (add(dropped("a.png", red)) as ImportResult.Added).picture
        var heard = 0
        library.addListener { heard++ }

        library.rename(a.id, "a.png")
        library.rename(PictureId("nothing"), "b.png")

        assertEquals(0, heard)
        assertEquals(0, jobs.pending)
    }

    @Test
    fun `a renamed picture's file added again is found by its content, without being decoded again`() {
        val a = (add(dropped("a.png", red)) as ImportResult.Added).picture
        library.rename(a.id, "Red wall")
        settle()
        val decoded = decodes

        val again = add(dropped("a.png", red))

        assertEquals(ImportResult.AlreadyThere(a.copy(name = "Red wall")), again)
        assertEquals(decoded, decodes)
        assertEquals(listOf(a.id.value, "index.json"), folder.list()!!.sorted())
    }

    @Test
    fun `deleting what isn't there changes nothing`() {
        library.read()
        settle()
        var heard = 0
        library.addListener { heard++ }

        library.delete(setOf(PictureId("nothing")))

        assertEquals(0, heard)
        assertEquals(0, jobs.pending)
    }

    @Test
    fun `deletes once per process what the index doesn't name`() {
        val kept = (add(shared("a.png", red)) as ImportResult.Added).picture
        File(folder, "deadbeef").writeText("left by a killed process")
        File(folder, "add123.new").writeText("an unfinished copy")
        File(images, "deadbeef").apply { parentFile!!.mkdirs(); writeText("drawn") }
        File(images, kept.id.value).apply { parentFile!!.mkdirs(); writeText("drawn") }
        File(copies, "deadbeef").writeText("a working copy")
        File(copies, "add123.new.copy").writeText("an unfinished working copy")
        restart()

        library.read()
        settle()

        assertEquals(listOf(kept.id.value, "index.json"), folder.list()!!.sorted())
        assertEquals(listOf(kept.id.value), images.list()!!.toList())
        assertEquals(listOf(kept.id.value), copies.list()!!.toList())
    }

    @Test
    fun `keeps every file when the index can't be read`() {
        folder.mkdirs()
        File(folder, "index.json").writeText("{\"format\":1,\"pictures\":[{\"id\"")
        File(folder, "0123456789abcdef0123456789abcdef").writeText("a picture")

        assertEquals(emptyList<Picture>(), library.read())
        settle()

        assertTrue(File(folder, "0123456789abcdef0123456789abcdef").exists())
    }

    @Test
    fun `reads the index once`() {
        library.read()
        folder.mkdirs()
        File(folder, "index.json").writeText(storedIndex)

        assertEquals(emptyList<Picture>(), library.read())
        assertNull(library.read().firstOrNull())
    }

    @Test
    fun `says it can't read a picture its app stops sending, and goes on with the next`() {
        waitForData = { ms ->
            waited += ms
            false
        }

        assertEquals(ImportResult.Unreadable, add(anotherApp.sendsNothing("stuck.png")))
        assertEquals(OutsideReader.LIMIT_MS, waited)
        waitForData = { true }

        assertTrue(add(shared("a.png", red)) is ImportResult.Added)
        assertEquals(1, library.read().size)
        assertEquals(emptyList<String>(), copiesLeft())
    }

    @Test
    fun `a picture cancelled before its job starts is neither read nor added`() {
        var answered = false
        val adding = library.add(shared("a.png", red)) { answered = true }

        adding.cancel()
        settle()

        assertFalse(answered)
        assertEquals(0, anotherApp.opens)
        assertEquals(emptyList<Picture>(), library.read())
    }

    @Test
    fun `a picture cancelled while it's read stops being read at once, leaving nothing`() {
        waitForData = {
            Thread.sleep(5)   // nothing comes, and no time passes: only the cancel ends it
            false
        }
        var answered = false
        val adding = library.add(anotherApp.sendsNothing("stuck.png")) { answered = true }
        val job = runJobsAside()
        waitUntil { anotherApp.opens == 1 }

        adding.cancel()

        job.join(2_000)
        assertFalse("still reading", job.isAlive)
        settle()
        assertFalse(answered)
        assertEquals(emptyList<Picture>(), library.read())
        assertEquals(emptyList<String>(), copiesLeft())
    }

    @Test
    fun `a picture cancelled once copied is deleted, not added`() {
        var answered = false
        val adding = library.add(shared("a.png", red)) { answered = true }
        jobs.runAll()   // copied

        adding.cancel()
        settle()

        assertFalse(answered)
        assertEquals(emptyList<Picture>(), library.read())
        assertEquals(emptyList<String>(), copiesLeft())
        assertEquals(emptyList<String>(), folder.list().orEmpty().filter { it != "index.json" })
    }

    @Test
    fun `a picture cancelled once in place isn't named, and its file serves when it's added again`() {
        var answered = false
        val adding = library.add(shared("a.png", red)) { answered = true }
        jobs.runAll()                               // copied
        shadowOf(Looper.getMainLooper()).idle()     // a new picture: its move queued
        jobs.runAll()                               // moved into place

        adding.cancel()
        settle()

        assertFalse(answered)
        assertEquals(emptyList<Picture>(), library.read())
        assertEquals(1, folder.list().orEmpty().count { it != "index.json" })
        val again = add(shared("b.png", red))
        assertEquals(listOf((again as ImportResult.Added).picture), library.read())
    }

    @Test
    fun `an unnamed picture's file is deleted at the next start`() {
        val adding = library.add(shared("a.png", red)) {}
        jobs.runAll()
        shadowOf(Looper.getMainLooper()).idle()
        jobs.runAll()
        adding.cancel()
        settle()
        restart()

        library.read()
        settle()

        assertEquals(emptyList<String>(), folder.list().orEmpty().filter { it != "index.json" })
    }

    @Test
    fun `cancelling a picture already added changes nothing`() {
        var result: ImportResult? = null
        val adding = library.add(shared("a.png", red)) { result = it }
        settle()

        adding.cancel()
        settle()

        assertEquals(listOf((result as ImportResult.Added).picture), library.read())
    }

    /** A picture's id: its content's hash, as the library names its files. */
    private fun idOf(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(32)

    /** A folder, not empty, where [bytes]' working copy goes, so that moving the copy into place fails. */
    private fun obstructCopy(bytes: ByteArray): File =
        File(copies, idOf(bytes)).apply { mkdirs(); File(this, "x").createNewFile() }

    /** The unfinished copies in the library's folder. */
    private fun copiesLeft(): List<String> = folder.list().orEmpty().filter { it.endsWith(".new") }

    /** Runs the queued jobs on a thread of their own, as the TV does, while the test goes on. */
    private fun runJobsAside(): Thread = Thread { jobs.runAll() }.apply { start() }

    private fun waitUntil(condition: () -> Boolean) {
        val end = System.nanoTime() + 5_000_000_000
        while (!condition()) {
            if (System.nanoTime() > end) fail("not within 5 s")
            Thread.sleep(5)
        }
    }
}
