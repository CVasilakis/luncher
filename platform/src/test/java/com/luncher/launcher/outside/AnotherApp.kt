package com.luncher.launcher.outside

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import org.robolectric.Robolectric
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Another app's content provider, as the tests' sharing app: what it [sends] for a name, from a
 * file. Robolectric's pipes are files too, which a reader that catches up finds ended, so whether
 * data comes, or none ([sendsNothing]), is up to the test's wait for data
 * ([OutsideCalls.waitForData]). Its query answers the name, no size. A test can make its query or
 * open wait ([holdQueries], [holdOpens]) until [letGo], or until the caller's signal is cancelled,
 * which it then counts ([cancelled]).
 */
class AnotherApp : ContentProvider() {

    private val files = mutableMapOf<String, File>()
    private val declaredLengths = mutableMapOf<String, Long>()

    @Volatile var holdQueries = false
    @Volatile var holdOpens = false
    private val held = CountDownLatch(1)

    /** How many times it was opened. */
    @Volatile var opens = 0
        private set

    /** How many waits the caller's signal ended. */
    @Volatile var cancelled = 0
        private set

    /** What a held open answered once let go, to see the caller closed it. */
    @Volatile var openedLate: AssetFileDescriptor? = null
        private set

    /** The content Uri of [name]. */
    fun uri(name: String): Uri = Uri.parse("content://$AUTHORITY/$name")

    /** [name] sends [bytes], then ends; its declared length [declaredLength] if given. */
    fun sends(name: String, bytes: ByteArray, declaredLength: Long? = null): Uri =
        sends(name, File.createTempFile("sent", null, context!!.cacheDir).apply { writeBytes(bytes) }, declaredLength)

    /** [name] sends what [file] has. */
    fun sends(name: String, file: File, declaredLength: Long? = null): Uri {
        files[name] = file
        declaredLength?.let { declaredLengths[name] = it }
        return uri(name)
    }

    /** [name] sends nothing, as far as the app goes: the test's wait for data says none comes. */
    fun sendsNothing(name: String): Uri = sends(name, ByteArray(0))

    /** Lets a held query or open go on. */
    fun letGo() = held.countDown()

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? =
        query(uri, projection, selection, selectionArgs, sortOrder, null)

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
        cancellationSignal: CancellationSignal?,
    ): Cursor {
        if (holdQueries) hold(cancellationSignal)
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(uri.lastPathSegment)) }
    }

    override fun openAssetFile(uri: Uri, mode: String, signal: CancellationSignal?): AssetFileDescriptor {
        opens++
        val name = uri.lastPathSegment.orEmpty()
        val file = files[name] ?: throw java.io.FileNotFoundException(name)
        val descriptor = AssetFileDescriptor(
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
            0,
            declaredLengths[name] ?: AssetFileDescriptor.UNKNOWN_LENGTH,
        )
        if (holdOpens) {
            hold(signal)
            openedLate = descriptor
        }
        return descriptor
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor = openAssetFile(uri, mode, null)

    /** Waits until [letGo], or [signal] is cancelled (then throws, as an app that heeds it does), 10 s at most. */
    private fun hold(signal: CancellationSignal?) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < end) {
            if (held.await(10, TimeUnit.MILLISECONDS)) return
            if (signal?.isCanceled == true) {
                cancelled++
                signal.throwIfCanceled()
            }
        }
    }

    override fun getType(uri: Uri) = "application/octet-stream"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val AUTHORITY = "com.example.files"

        /** Another app, answering for [AUTHORITY] in this test. */
        fun install(): AnotherApp = Robolectric.setupContentProvider(AnotherApp::class.java, AUTHORITY)
    }
}
