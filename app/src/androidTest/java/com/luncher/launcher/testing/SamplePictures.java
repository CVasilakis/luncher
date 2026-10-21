package com.luncher.launcher.testing;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Another app's pictures: a content provider of the test package, which serves the sample files
 * of its assets/pictures/ as a gallery or a file manager shares a picture, with its name and size.
 * Luncher reads them through it, never by a path. It serves a folder too ({@link #FOLDER}), as
 * Material Files shares one along with the pictures chosen: it opens, and reading it fails.
 *
 * In Java: it runs in the test package's own process, which has no Kotlin standard library (the
 * test APK leaves out what the app under test has, which runs in another process).
 */
public class SamplePictures extends ContentProvider {

    /** The name of the folder it serves: a folder, which no sample is named after. */
    public static final String FOLDER = "Holidays";

    /** The sample {@code name} (assets/pictures/name), shared by another app. */
    public static Uri uri(String name) {
        String testPackage = InstrumentationRegistry.getInstrumentation().getContext().getPackageName();
        return Uri.parse("content://" + testPackage + ".samples/" + name);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        try {
            cursor.addRow(new Object[] {uri.getLastPathSegment(), sample(uri).length()});
        } catch (FileNotFoundException e) {
            // No such sample: no row, as a provider says it has nothing.
        }
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(sample(uri), ParcelFileDescriptor.MODE_READ_ONLY);
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

    /** The asset {@code uri} names, copied to a file once: an asset may be stored compressed, with no file descriptor of its own. */
    private File sample(Uri uri) throws FileNotFoundException {
        Context context = getContext();
        String name = uri.getLastPathSegment();
        if (context == null || name == null) throw new FileNotFoundException(String.valueOf(uri));
        if (FOLDER.equals(name)) {
            File folder = new File(context.getCacheDir(), "folders/" + name);
            //noinspection ResultOfMethodCallIgnored
            folder.mkdirs();
            return folder;
        }
        File file = new File(context.getCacheDir(), "samples/" + name);
        if (file.exists()) return file;
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        try (InputStream input = context.getAssets().open("pictures/" + name); OutputStream output = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int n; (n = input.read(buffer)) > 0; ) output.write(buffer, 0, n);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            throw new FileNotFoundException("no sample " + name);
        }
        return file;
    }
}
