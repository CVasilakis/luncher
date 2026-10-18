package com.luncher.launcher.testing;

import android.net.Uri;

import androidx.test.platform.app.InstrumentationRegistry;

/**
 * Another app's pictures as a gallery keeps them: {@link SamplePictures}' samples, served by a
 * provider that isn't exported, so only the read access a share gives with a picture opens it.
 * Luncher must pass that access on to its window, which adds the pictures.
 *
 * In Java, as SamplePictures, for the same reason.
 */
public class GrantedPictures extends SamplePictures {

    /** The sample {@code name}, which only a grant opens. */
    public static Uri uri(String name) {
        String testPackage = InstrumentationRegistry.getInstrumentation().getContext().getPackageName();
        return Uri.parse("content://" + testPackage + ".granted/" + name);
    }
}
