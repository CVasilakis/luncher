package com.luncher.launcher.testing;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.OpenableColumns;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Another app whose picture never comes, as a cloud app's on a dead network: a content provider of
 * the test package that answers its query (the name), then either opens a stream it never sends
 * anything on ({@link #silent}), or never answers the open at all ({@link #unopened}; it gives up
 * after a minute, long after the test).
 *
 * In Java, as {@link SamplePictures}: it runs in the test package's own process, which has no
 * Kotlin standard library.
 */
public class StalledPictures extends ContentProvider {

    private static final long UNOPENED_MS = 60_000;

    // The ends it would send on, kept open, so the stream never ends either.
    private static final List<ParcelFileDescriptor> NEVER_WRITTEN = new ArrayList<>();

    /** A picture {@code name} whose app opens it, then sends nothing. */
    public static Uri silent(String name) {
        return uri("silent/" + name);
    }

    /** A picture {@code name} whose app never answers the open. */
    public static Uri unopened(String name) {
        return uri("unopened/" + name);
    }

    private static Uri uri(String path) {
        String testPackage = InstrumentationRegistry.getInstrumentation().getContext().getPackageName();
        return Uri.parse("content://" + testPackage + ".stalled/" + path);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME});
        cursor.addRow(new Object[] {uri.getLastPathSegment()});
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (uri.getPath() != null && uri.getPath().startsWith("/unopened/")) {
            SystemClock.sleep(UNOPENED_MS);
            throw new FileNotFoundException("never opened " + uri);
        }
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            synchronized (NEVER_WRITTEN) {
                NEVER_WRITTEN.add(pipe[1]);
            }
            return pipe[0];
        } catch (IOException e) {
            throw new FileNotFoundException(e.toString());
        }
    }

    @Override
    public String getType(Uri uri) {
        return "image/*";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
