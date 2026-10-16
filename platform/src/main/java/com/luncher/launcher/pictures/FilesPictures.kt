package com.luncher.launcher.pictures

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.Jobs
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureImport
import com.luncher.domain.pictures.PictureLimits
import com.luncher.domain.pictures.Pictures
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * [Pictures] and [PictureImport] on files: each picture's original file, unchanged, in [folder],
 * named after a hash of its content (the same picture added twice is stored once), and an index
 * of them ([INDEX]: name, size, dimensions as shown, when added) written whole, to a new file that
 * then replaces the old one at once. The images drawn of a picture are kept in [images]/<id>/
 * (`CachedPictureImages`), and deleted with it.
 *
 * The index is read once per process, on the main thread, then kept there. Everything slow is a
 * job of [JobPriority.PICTURE_LIBRARY], one priority so they keep their order: adding a picture
 * (copying it, checking it), moving its file into place, deleting files, writing the index; and,
 * once per process, deleting what no index entry names (files a killed process left). A source
 * other apps give is read as a stream, through [ContentResolver], never by a path it names: a
 * `file://` Uri is [ImportResult.Unreadable] (only an app with storage permission could read
 * it), except one of Luncher's own drop folders ([dropFolders]).
 */
class FilesPictures(
    context: Context,
    private val jobs: Jobs,
    private val folder: File = File(context.filesDir, FOLDER),
    private val images: File = File(context.cacheDir, FOLDER),
    private val now: () -> Long = System::currentTimeMillis,
    private val dropFolders: () -> List<File> = { dropFolders(context.applicationContext) },
) : Pictures, PictureImport<Uri> {

    /** What adding found, in a job: the result, or the picture copied to [file], not in the library yet. */
    private sealed interface Copied {
        class Failed(val result: ImportResult) : Copied
        class Copy(val file: File, val id: PictureId, val name: String, val bytes: Long, val width: Int, val height: Int) : Copied
    }

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
                File(images, id.value).deleteRecursively()
            }
            writeIndex(entries)
        }
        changed()
    }

    override fun add(source: Uri, done: (ImportResult) -> Unit) {
        index()   // its cleaning first, before this picture's files exist
        jobs.run(JobPriority.PICTURE_LIBRARY) {
            val copied = copy(source)
            main.post { added(copied, done) }
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
    private fun added(copied: Copied, done: (ImportResult) -> Unit) {
        if (copied is Copied.Failed) return done(copied.result)
        copied as Copied.Copy
        index()[copied.id]?.let { known ->
            jobs.run(JobPriority.PICTURE_LIBRARY) { copied.file.delete() }
            return done(ImportResult.AlreadyThere(known))
        }
        // Into place before the library names it, so whatever then draws it finds its file.
        jobs.run(JobPriority.PICTURE_LIBRARY) {
            val target = File(folder, copied.id.value)
            // There already: a file a killed process left, or the same picture added meanwhile.
            val placed = if (target.exists()) {
                copied.file.delete()
                true
            } else {
                copied.file.renameTo(target)
            }
            if (!placed) copied.file.delete()
            main.post { placed(copied, placed, done) }
        }
    }

    private fun placed(copied: Copied.Copy, placed: Boolean, done: (ImportResult) -> Unit) {
        if (!placed) return done(ImportResult.Unreadable)
        val index = index()
        index[copied.id]?.let { return done(ImportResult.AlreadyThere(it)) }   // the same picture, added meanwhile
        val picture = Picture(copied.id, copied.name, copied.width, copied.height, copied.bytes, now())
        index[picture.id] = picture
        val entries = index.values.toList()
        jobs.run(JobPriority.PICTURE_LIBRARY) { writeIndex(entries) }
        changed()
        done(ImportResult.Added(picture))
    }

    /**
     * A job: [source]'s content copied into a new file of [folder], its hash computed on the way,
     * stopping past [PictureLimits.MAX_BYTES]; then checked to be a picture this TV decodes whole,
     * within [PictureLimits.MAX_PIXELS].
     */
    private fun copy(source: Uri): Copied {
        val described = describe(source) ?: return Copied.Failed(ImportResult.Unreadable)
        if (described.bytes != null && !PictureLimits.allowsBytes(described.bytes)) return Copied.Failed(ImportResult.TooLarge)
        folder.mkdirs()
        val file = try {
            File.createTempFile("add", TEMPORARY, folder)
        } catch (e: IOException) {
            return Copied.Failed(if (noSpace(e)) ImportResult.NoSpace else ImportResult.Unreadable)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        val failed = try {
            open(source).use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        bytes += n
                        if (!PictureLimits.allowsBytes(bytes)) return@use ImportResult.TooLarge
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
        val shape = PictureFiles.shape(file)
        val unfit = when {
            shape == null -> unsupportedOrNot(file)
            !PictureLimits.allowsSize(shape.width, shape.height) -> ImportResult.TooLarge
            !PictureFiles.decodes(file, shape) -> unsupportedOrNot(file)
            else -> null
        }
        if (unfit != null || shape == null) {
            file.delete()
            return Copied.Failed(unfit ?: ImportResult.NotAPicture)
        }
        val id = PictureId(digest.digest().joinToString("") { "%02x".format(it) }.take(ID_LENGTH))
        return Copied.Copy(file, id, described.name, bytes, shape.width, shape.height)
    }

    /** A file BitmapFactory can't decode: in a format this TV can't show, which is said, or not a picture at all. */
    private fun unsupportedOrNot(file: File): ImportResult =
        PictureFiles.format(file)?.let { ImportResult.UnsupportedFormat(it) } ?: ImportResult.NotAPicture

    private class Described(val name: String, val bytes: Long?)

    /** [source]'s name and size, as far as it says; null if it's a path Luncher may not read. */
    private fun describe(source: Uri): Described? {
        if (source.scheme == ContentResolver.SCHEME_FILE) {
            val file = File(source.path ?: return null)
            if (!inDropFolder(file)) return null
            return Described(clean(file.name), file.length())
        }
        if (source.scheme != ContentResolver.SCHEME_CONTENT) return null
        var name: String? = null
        var bytes: Long? = null
        try {
            resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { row ->
                if (row.moveToFirst()) {
                    val nameColumn = row.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeColumn = row.getColumnIndex(OpenableColumns.SIZE)
                    if (nameColumn >= 0 && !row.isNull(nameColumn)) name = row.getString(nameColumn)
                    if (sizeColumn >= 0 && !row.isNull(sizeColumn)) bytes = row.getLong(sizeColumn)
                }
            }
        } catch (e: RuntimeException) {
            // A provider that answers no query (SecurityException, a bad column): the stream may still open.
        }
        return Described(clean(name ?: source.lastPathSegment.orEmpty()), bytes)
    }

    private fun inDropFolder(file: File): Boolean {
        val path = file.canonicalPath
        return dropFolders().any { path.startsWith(it.canonicalPath + File.separator) }
    }

    private fun open(source: Uri): InputStream =
        resolver.openInputStream(source) ?: throw IOException("no stream for $source")

    /**
     * A name another app gave, as a label only (never a path): without control characters, and
     * not longer than a name can sensibly be. Empty when it had none.
     */
    private fun clean(name: String): String =
        name.filterNot { it.isISOControl() }.trim().take(MAX_NAME)

    private fun noSpace(e: IOException): Boolean = (e.cause as? ErrnoException)?.errno == OsConstants.ENOSPC

    /** A job: deletes the files and drawn images no entry of the index names ([kept]), and unfinished copies. */
    private fun deleteUnlisted(kept: Set<String>) {
        for (file in folder.listFiles().orEmpty()) {
            if (file.name != INDEX && file.name !in kept) file.delete()
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

        /** Hex digits of the content's SHA-256 a picture's id keeps: 128 bits. */
        const val ID_LENGTH = 32
        const val MAX_NAME = 120
        const val BUFFER = 64 * 1024
    }
}
