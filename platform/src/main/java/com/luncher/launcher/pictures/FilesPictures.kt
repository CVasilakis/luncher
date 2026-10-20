package com.luncher.launcher.pictures

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import com.luncher.domain.jobs.Cancellable
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.Jobs
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureImport
import com.luncher.domain.pictures.PictureLimits
import com.luncher.domain.pictures.Pictures
import com.luncher.domain.pictures.SizeLimit
import com.luncher.domain.pictures.nameClash
import com.luncher.domain.pictures.pictureName
import com.luncher.launcher.outside.OutsideCallEnded
import com.luncher.launcher.outside.OutsideCalls
import com.luncher.launcher.outside.OutsideReader
import com.luncher.launcher.outside.Stop
import com.luncher.launcher.ui.ScreenSize
import com.luncher.launcher.ui.color
import com.luncher.launcher.ui.screenSize
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import com.luncher.launcher.ui.R as UiR

/**
 * [Pictures] and [PictureImport] on files: each picture's original file, unchanged, in [folder],
 * named after a hash of its content (the same picture added twice is stored once), and an index
 * of them ([INDEX]: name, size, dimensions as shown, when added) written whole, to a new file that
 * then replaces the old one at once. A name is the index's only, so renaming a picture writes
 * the index and nothing else. Its working copy, from which the screens draw it, is made
 * as it's added, kept in [copies]/<id> ([WorkingCopies]), and deleted with it, as is its
 * thumbnail, kept in [images]/<id> ([CachedPictureImages]).
 *
 * The index is read once per process, on the main thread, then kept there. Everything slow is a
 * job of [JobPriority.PICTURE_LIBRARY], one priority so they keep their order: adding a picture
 * (copying it, checking it), moving its file into place, deleting files, writing the index; and,
 * once per process, deleting what no index entry names (files a killed process left). A source
 * other apps give is read as a stream, through [ContentResolver], never by a path it names: a
 * `file://` Uri is [ImportResult.Unreadable] (only an app with storage permission could read
 * it), except one of Luncher's own drop folders ([dropFolders]). It's asked and read through
 * [OutsideReader], as the app may never answer: the job then gives up, after
 * [OutsideReader.LIMIT_MS] without an answer ([ImportResult.Unreadable]), or once the import is
 * cancelled.
 *
 * A picture whose name the library has ([nameClash]) isn't added: refused as soon as its name
 * and size are known, before it's copied when the source says its size, and again before it's
 * named, in case one of its name was added meanwhile.
 *
 * A cancelled import ([add]'s handle) is dropped wherever it is: its job taken out of the queue if
 * it hasn't started, its reading stopped if it's reading, its copy deleted if it's made; on the
 * main thread, the steps that follow see it and stop; its decode, too, at its next read. A file already moved into place but not yet
 * named stays, for the cleanup of the next start: deleting it could delete the same picture,
 * added again meanwhile.
 */
class FilesPictures(
    context: Context,
    private val jobs: Jobs,
    private val outsideCalls: OutsideCalls,
    private val folder: File = File(context.filesDir, FOLDER),
    private val images: File = File(context.cacheDir, CachedPictureImages.FOLDER),
    private val now: () -> Long = System::currentTimeMillis,
    private val dropFolders: () -> List<File> = { dropFolders(context.applicationContext) },
    copies: File = File(context.filesDir, WorkingCopies.FOLDER),
    screen: () -> ScreenSize = { context.applicationContext.screenSize() },
) : Pictures, PictureImport<Uri> {

    /**
     * What adding found, in a job: the result, or the picture copied to [file], not in the library
     * yet, and its working copy made in [copy], unless it couldn't be written.
     */
    private sealed interface Copied {
        class Failed(val result: ImportResult) : Copied
        class Copy(val file: File, val copy: File?, val id: PictureId, val name: String, val bytes: Long, val width: Int, val height: Int) : Copied {
            /** Deletes what was made, for a picture that isn't added. */
            fun delete() {
                file.delete()
                copy?.delete()
            }
        }
    }

    private val workingCopies = WorkingCopies(copies, context.color(UiR.color.background), screen)

    private val resolver = context.contentResolver
    private val main = Handler(Looper.getMainLooper())

    // The main thread's; jobs get copies.
    private var index: LinkedHashMap<PictureId, Picture>? = null
    private val listeners = mutableListOf<() -> Unit>()

    override fun read(): List<Picture> = index().values.toList()

    override fun delete(ids: Set<PictureId>) {
        val index = index()
        if (ids.none { it in index }) return
        ids.forEach { index.remove(it) }
        val entries = index.values.toList()
        jobs.run(JobPriority.PICTURE_LIBRARY) {
            for (id in ids) {
                File(folder, id.value).delete()
                workingCopies.fileOf(id.value).delete()
                File(images, id.value).deleteRecursively()
            }
            writeIndex(entries)
        }
        changed()
    }

    override fun rename(id: PictureId, name: String) {
        val index = index()
        val picture = index[id] ?: return
        if (picture.name == name) return
        index[id] = picture.copy(name = name)
        val entries = index.values.toList()
        // The index only: its file, working copy and thumbnail are named by its id.
        jobs.run(JobPriority.PICTURE_LIBRARY) { writeIndex(entries) }
        changed()
    }

    override fun add(source: Uri, copying: () -> Unit, done: (ImportResult) -> Unit): Cancellable {
        index()   // its cleaning first, before this picture's files exist
        val import = Import(copying, done)
        val library = index().values.toList()
        val queued = jobs.run(JobPriority.PICTURE_LIBRARY) {
            val copied = copy(source, import.stop, library) { main.post(import::copying) }
            main.post { added(copied, import) }
        }
        return Cancellable {
            queued.cancel()
            import.cancel()
        }
    }

    /**
     * One picture being added, on the main thread: [stop] tells its job to give up reading,
     * [copying] says the copying started, and [answer] answers, each unless it was cancelled,
     * [answer] once.
     */
    private class Import(private val started: () -> Unit, private val done: (ImportResult) -> Unit) {
        val stop = Stop()
        var cancelled = false
            private set
        private var answered = false

        fun cancel() {
            if (answered) return
            cancelled = true
            stop.stop()
        }

        fun copying() {
            if (!cancelled && !answered) started()
        }

        fun answer(result: ImportResult) {
            if (cancelled || answered) return
            answered = true
            done(result)
        }
    }

    override fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    override fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    private fun changed() = listeners.toList().forEach { it() }

    /** The index, read on first use; with a job, once per process, that deletes what it doesn't name. */
    private fun index(): LinkedHashMap<PictureId, Picture> {
        index?.let { return it }
        val file = File(folder, INDEX)
        val read = readIndex(file)
        val index = LinkedHashMap<PictureId, Picture>()
        read?.forEach { index[it.id] = it }
        this.index = index
        // An index that can't be read keeps every file: they may be the user's pictures.
        if (read != null || !file.exists()) {
            val kept = index.keys.mapTo(HashSet()) { it.value }
            jobs.run(JobPriority.PICTURE_LIBRARY) { deleteUnlisted(kept) }
        }
        return index
    }

    /** On the main thread: what [copy] found becomes a picture of the library, or the reason it doesn't. */
    private fun added(copied: Copied, import: Import) {
        if (copied is Copied.Failed) return import.answer(copied.result)
        copied as Copied.Copy
        if (import.cancelled) {
            jobs.run(JobPriority.PICTURE_LIBRARY) { copied.delete() }
            return
        }
        index()[copied.id]?.let { known ->
            jobs.run(JobPriority.PICTURE_LIBRARY) { copied.delete() }
            return import.answer(ImportResult.AlreadyThere(known))
        }
        // A picture of its name added meanwhile (another of the same share, say).
        nameClash(copied.name, copied.bytes, index().values)?.let { clash ->
            jobs.run(JobPriority.PICTURE_LIBRARY) { copied.delete() }
            return import.answer(clash)
        }
        // Into place before the library names it, so whatever then draws it finds its files.
        jobs.run(JobPriority.PICTURE_LIBRARY) {
            val target = File(folder, copied.id.value)
            // There already: a file a killed process left, or the same picture added meanwhile.
            val placed = if (target.exists()) {
                copied.file.delete()
                true
            } else {
                copied.file.renameTo(target)
            }
            // Its working copy replaces any there (made for another screen, say); without one, the editor makes it.
            if (!placed || copied.copy?.renameTo(workingCopies.fileOf(copied.id.value)) == false) copied.copy?.delete()
            if (!placed) copied.file.delete()
            main.post { placed(copied, placed, import) }
        }
    }

    private fun placed(copied: Copied.Copy, placed: Boolean, import: Import) {
        if (import.cancelled) return   // a file moved into place stays unnamed, for the next start's cleanup
        if (!placed) return import.answer(ImportResult.Unreadable)
        val index = index()
        index[copied.id]?.let { return import.answer(ImportResult.AlreadyThere(it)) }   // the same picture, added meanwhile
        nameClash(copied.name, copied.bytes, index.values)?.let { return import.answer(it) }   // one of its name, added meanwhile
        val picture = Picture(copied.id, copied.name, copied.width, copied.height, copied.bytes, now())
        index[picture.id] = picture
        val entries = index.values.toList()
        jobs.run(JobPriority.PICTURE_LIBRARY) { writeIndex(entries) }
        changed()
        import.answer(ImportResult.Added(picture))
    }

    /**
     * A job: refused at once if its name or size, as [source] says them, already tell
     * ([nameClash] with the [library] as it was when it was asked; a source that doesn't say its
     * size, once it's copied); else [source]'s content copied into a new file of [folder], its
     * hash computed on the way, stopping past [PictureLimits.MAX_BYTES] or once [stop] is set;
     * refused if the [library] has that content already, under any name, before it's decoded;
     * then checked to be within [PictureLimits.MAX_PIXELS], and decoded whole, once, into its
     * working copy ([WorkingCopies]): a size alone can decode where the picture can't (AVIF on
     * API 31), and the screens then show it without decoding it again.
     */
    private fun copy(source: Uri, stop: Stop, library: List<Picture>, copying: () -> Unit): Copied {
        val reader = OutsideReader(resolver, outsideCalls, stop)
        val described = describe(source, reader) ?: return Copied.Failed(ImportResult.Unreadable)
        if (described.bytes != null && !PictureLimits.allowsBytes(described.bytes)) return Copied.Failed(ImportResult.TooLarge(SizeLimit.BYTES))
        // Its size unknown (a provider may not say), the same name is judged once it's copied.
        if (described.bytes != null) nameClash(described.name, described.bytes, library)?.let { return Copied.Failed(it) }
        copying()
        folder.mkdirs()
        val file = try {
            File.createTempFile("add", TEMPORARY, folder)
        } catch (e: IOException) {
            return Copied.Failed(if (noSpace(e)) ImportResult.NoSpace else ImportResult.Unreadable)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        val failed = try {
            reader.open(source).use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        bytes += n
                        if (!PictureLimits.allowsBytes(bytes)) return@use ImportResult.TooLarge(SizeLimit.BYTES)
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                    null
                }
            }
        } catch (e: IOException) {
            if (noSpace(e)) ImportResult.NoSpace else ImportResult.Unreadable
        } catch (e: RuntimeException) {
            // Another app's provider: no access given (SecurityException), no such Uri
            // (IllegalArgumentException), or anything else it throws, which mustn't end Luncher.
            ImportResult.Unreadable
        }
        if (failed != null) {
            file.delete()
            return Copied.Failed(failed)
        }
        if (described.bytes == null) {
            nameClash(described.name, bytes, library)?.let {
                file.delete()
                return Copied.Failed(it)
            }
        }
        val id = PictureId(digest.digest().joinToString("") { "%02x".format(it) }.take(ID_LENGTH))
        // The same content in the library already (named otherwise, or renamed since): not decoded again.
        library.firstOrNull { it.id == id }?.let {
            file.delete()
            return Copied.Failed(ImportResult.AlreadyThere(it))
        }
        val shape = PictureFiles.shape(file)
        val copy = File(workingCopies.folder, file.name + WORKING_COPY)
        val unfit = when {
            shape == null -> unsupportedOrNot(file)
            !PictureLimits.allowsSize(shape.width, shape.height) -> ImportResult.TooLarge(SizeLimit.PIXELS)
            else -> when (workingCopies.make(file, shape, copy) { stop.isStopped }) {
                WorkingCopies.Made.MADE, WorkingCopies.Made.NOT_WRITTEN -> null
                WorkingCopies.Made.NOT_DECODED -> if (stop.isStopped) ImportResult.Unreadable else unsupportedOrNot(file)
            }
        }
        if (unfit != null || shape == null) {
            file.delete()
            copy.delete()
            return Copied.Failed(unfit ?: ImportResult.NotAPicture)
        }
        return Copied.Copy(file, copy.takeIf { it.isFile }, id, described.name, bytes, shape.width, shape.height)
    }

    /** A file BitmapFactory can't decode: in a format this TV can't show, which is said, or not a picture at all. */
    private fun unsupportedOrNot(file: File): ImportResult =
        PictureFiles.format(file)?.let { ImportResult.UnsupportedFormat(it) } ?: ImportResult.NotAPicture

    private class Described(val name: String, val bytes: Long?)

    /** [source]'s name and size, as far as it says; null if it's a path Luncher may not read, or its app didn't answer. */
    private fun describe(source: Uri, reader: OutsideReader): Described? {
        if (source.scheme == ContentResolver.SCHEME_FILE) {
            val file = File(source.path ?: return null)
            if (!inDropFolder(file)) return null
            return Described(pictureName(file.name), file.length())
        }
        if (source.scheme != ContentResolver.SCHEME_CONTENT) return null
        val said = try {
            reader.query(source, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)) { row ->
                if (row == null || !row.moveToFirst()) return@query null
                val nameColumn = row.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeColumn = row.getColumnIndex(OpenableColumns.SIZE)
                Pair(
                    if (nameColumn >= 0 && !row.isNull(nameColumn)) row.getString(nameColumn) else null,
                    if (sizeColumn >= 0 && !row.isNull(sizeColumn)) row.getLong(sizeColumn) else null,
                )
            }
        } catch (e: OutsideCallEnded) {
            return null
        } catch (e: RuntimeException) {
            null   // A provider that answers no query (SecurityException, a bad column): the stream may still open.
        }
        return Described(pictureName(said?.first ?: source.lastPathSegment.orEmpty()), said?.second)
    }

    private fun inDropFolder(file: File): Boolean {
        val path = file.canonicalPath
        return dropFolders().any { path.startsWith(it.canonicalPath + File.separator) }
    }

    private fun noSpace(e: IOException): Boolean = (e.cause as? ErrnoException)?.errno == OsConstants.ENOSPC

    /**
     * A job: deletes the files, working copies and drawn images no entry of the index names
     * ([kept]), and unfinished copies of either kind.
     */
    private fun deleteUnlisted(kept: Set<String>) {
        for (file in folder.listFiles().orEmpty()) {
            if (file.name != INDEX && file.name !in kept) file.delete()
        }
        for (copy in workingCopies.folder.listFiles().orEmpty()) {
            if (copy.name !in kept) copy.delete()
        }
        for (pictureImages in images.listFiles().orEmpty()) {
            if (pictureImages.name !in kept) pictureImages.deleteRecursively()
        }
    }

    /**
     * The index's pictures, or null if it can't be read (an empty library if there's none). The
     * format, `{"format": 1, "pictures": [{"id", "name", "width", "height", "bytes", "added"}, …]}`,
     * is kept on the TV across updates: a later version reads this one, and an entry it can't
     * read is left out, the others kept.
     */
    private fun readIndex(file: File): List<Picture>? {
        if (!file.exists()) return emptyList()
        return try {
            val pictures = JSONObject(file.readText()).getJSONArray("pictures")
            (0 until pictures.length()).mapNotNull { i ->
                try {
                    val entry = pictures.getJSONObject(i)
                    Picture(
                        PictureId(entry.getString("id")),
                        entry.getString("name"),
                        entry.getInt("width"),
                        entry.getInt("height"),
                        entry.getLong("bytes"),
                        entry.getLong("added"),
                    )
                } catch (e: JSONException) {
                    null
                }
            }
        } catch (e: IOException) {
            null
        } catch (e: JSONException) {
            null
        }
    }

    /** A job: the index, written whole to a new file that then replaces the old one at once. */
    private fun writeIndex(pictures: List<Picture>) {
        val entries = JSONArray()
        for (picture in pictures) {
            entries.put(
                JSONObject()
                    .put("id", picture.id.value)
                    .put("name", picture.name)
                    .put("width", picture.width)
                    .put("height", picture.height)
                    .put("bytes", picture.bytes)
                    .put("added", picture.added),
            )
        }
        val text = JSONObject().put("format", FORMAT).put("pictures", entries).toString()
        val file = File(folder, INDEX)
        val temporary = File(folder, INDEX + TEMPORARY)
        try {
            folder.mkdirs()
            temporary.writeText(text)
            if (!temporary.renameTo(file)) temporary.delete()
        } catch (e: IOException) {
            // A full disk: the index as it was; the next start deletes the files it doesn't name.
            temporary.delete()
        }
    }

    private companion object {
        const val FOLDER = "pictures"
        const val INDEX = "index.json"
        const val FORMAT = 1
        const val TEMPORARY = ".new"

        /** The ending of a picture's working copy while it's being added, before its id names it. */
        const val WORKING_COPY = ".copy"

        /** Hex digits of the content's SHA-256 a picture's id keeps: 128 bits. */
        const val ID_LENGTH = 32
        const val BUFFER = 64 * 1024
    }
}
