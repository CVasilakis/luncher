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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // to make and decode pictures
class FilesPicturesTest {

    private val context = RuntimeEnvironment.getApplication()
    private val jobs = ManualJobs()
    private val folder = File(context.filesDir, "pictures")
    private val images = File(context.cacheDir, "pictures")
    private var time = 1_000L

    /** The drop folder on the TV's storage (Robolectric has no storage volumes to ask). */
    private val dropFolder = File(context.getExternalFilesDir(null)!!.parentFile!!.parentFile, "../media/${context.packageName}")
        .canonicalFile.apply { mkdirs() }
    private var library = FilesPictures(context, jobs, now = { time }, dropFolders = { listOf(dropFolder) })

    private val sunset = Picture(PictureId("0123456789abcdef0123456789abcdef"), "sunset.jpg", 1920, 1080, 3_100_000, 1_700_000_000_000)
    private val beach = Picture(PictureId("fedcba9876543210fedcba9876543210"), "beach.png", 1080, 1920, 250_000, 1_700_000_001_000)

    // The stored format, written out: a TV keeps the index across updates, so a change of it has to
    // read this one too. The other tests add and read back, which a change of both sides would pass.
    private val storedIndex = """{"format":1,"pictures":[""" +
        """{"id":"0123456789abcdef0123456789abcdef","name":"sunset.jpg","width":1920,"height":1080,"bytes":3100000,"added":1700000000000},""" +
        """{"id":"fedcba9876543210fedcba9876543210","name":"beach.png","width":1080,"height":1920,"bytes":250000,"added":1700000001000}]}"""

    /** As the next start: a new process, with what the files hold. */
    private fun restart() {
        library = FilesPictures(context, jobs, now = { time }, dropFolders = { listOf(dropFolder) })
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

    /** A picture another app shares, as content://, named by the Uri's last part (the tests' resolver answers no query). */
    private fun shared(name: String, stream: () -> InputStream): Uri {
        val uri = Uri.parse("content://com.example.files/$name")
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri, stream)
        return uri
    }

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

        val result = add(shared("holiday.png") { red.inputStream() })

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
        val first = (add(shared("a.png") { red.inputStream() }) as ImportResult.Added).picture

        val again = add(dropped("copy of a.png", red))

        assertEquals(ImportResult.AlreadyThere(first), again)
        assertEquals(listOf(first), library.read())
        assertEquals(listOf(first.id.value, "index.json"), folder.list()!!.sorted())
    }

    @Test
    fun `the same picture added twice at once is stored once`() {
        val results = mutableListOf<ImportResult>()
        library.add(shared("a.png") { red.inputStream() }) { results += it }
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
        assertEquals(ImportResult.NotAPicture, add(shared("notes.txt") { "not a picture".byteInputStream() }))
        assertEquals(ImportResult.NotAPicture, add(shared("empty.png") { ByteArray(0).inputStream() }))
        assertEquals(ImportResult.NotAPicture, add(shared("header only.png") { red.copyOf(8).inputStream() }))
        assertEquals(emptyList<Picture>(), library.read())
    }

    @Test
    fun `names a format this TV can't show`() {
        assertEquals(ImportResult.UnsupportedFormat(PictureFormat.HEIF), add(shared("IMG_0001.HEIC") { TestPictureFiles.heic().inputStream() }))
    }

    @Test
    fun `refuses a file over 50 MB, without reading it all`() {
        var read = 0L
        val huge = object : InputStream() {
            override fun read(): Int = if (read++ < PictureLimits.MAX_BYTES * 2) 0 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (read >= PictureLimits.MAX_BYTES * 2) return -1
                read += len
                return len
            }
        }

        assertEquals(ImportResult.TooLarge, add(shared("huge.png") { huge }))
        assertTrue(read <= PictureLimits.MAX_BYTES + 64 * 1024)
        assertEquals(listOf("index.json"), folder.list().orEmpty().filter { !it.endsWith(".new") }.ifEmpty { listOf("index.json") })
    }

    @Test
    fun `refuses a picture of more than 64 megapixels`() {
        assertEquals(ImportResult.TooLarge, add(shared("huge.png") { TestPictureFiles.pngHeader(8001, 8000).inputStream() }))
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
        add(shared("notes.txt") { "not a picture".byteInputStream() })
        add(shared("huge.png") { TestPictureFiles.pngHeader(9000, 9000).inputStream() })

        assertEquals(emptyList<String>(), folder.list().orEmpty().toList())
    }

    @Test
    fun `deletes pictures, their files and their drawn images, telling the listeners`() {
        val a = (add(shared("a.png") { red.inputStream() }) as ImportResult.Added).picture
        val b = (add(shared("b.png") { blue.inputStream() }) as ImportResult.Added).picture
        File(images, "${a.id.value}/384x216").apply { parentFile!!.mkdirs(); writeText("drawn") }
        var heard = 0
        library.addListener { heard++ }

        library.delete(setOf(a.id))
        assertEquals("at once", listOf(b), library.read())
        settle()

        assertEquals(1, heard)
        assertFalse(File(folder, a.id.value).exists())
        assertFalse(File(images, a.id.value).exists())
        assertTrue(File(folder, b.id.value).exists())
        restart()
        assertEquals(listOf(b), library.read())
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
        val kept = (add(shared("a.png") { red.inputStream() }) as ImportResult.Added).picture
        File(folder, "deadbeef").writeText("left by a killed process")
        File(folder, "add123.new").writeText("an unfinished copy")
        File(images, "deadbeef/384x216").apply { parentFile!!.mkdirs(); writeText("drawn") }
        File(images, "${kept.id.value}/384x216").apply { parentFile!!.mkdirs(); writeText("drawn") }
        restart()

        library.read()
        settle()

        assertEquals(listOf(kept.id.value, "index.json"), folder.list()!!.sorted())
        assertEquals(listOf(kept.id.value), images.list()!!.toList())
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
}
