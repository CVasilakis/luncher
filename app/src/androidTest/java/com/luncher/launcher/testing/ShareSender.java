package com.luncher.launcher.testing;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Another app's screen sharing pictures, as a gallery does: it makes a {@code SEND_MULTIPLE} of
 * the pictures it's given ({@link #EXTRA_PICTURES}) and sends it with {@code startActivity} to
 * {@link #EXTRA_TARGET}, from its own process, where Android adds the read access to each picture
 * of the stream as it sends it. The tests' own shares come from Luncher's process, which can't
 * grant access to another app's pictures.
 *
 * It makes the share itself: Android adds the access only to a share whose extras it can read as
 * it sends it, not one passed in still packed, as an extra of another intent. And it finishes only
 * once it's covered ({@link #onStop}), as a gallery stays: a task emptied while in front returns to
 * the home app on API 24, which would come over Luncher's window.
 *
 * To try a share of several pictures by hand, as {@code am start} can't send Uris, the pictures
 * may also be strings, an array ({@code --esa}): the settings README shows the command.
 *
 * In Java: it runs in the test package's own process, which has no Kotlin standard library
 * (SamplePictures says why).
 */
public class ShareSender extends Activity {

    /** The pictures to share: an ArrayList of Uris, or their strings (an array or a list). */
    public static final String EXTRA_PICTURES = "com.luncher.launcher.testing.PICTURES";

    /** Where to send the share, a ComponentName. */
    public static final String EXTRA_TARGET = "com.luncher.launcher.testing.TARGET";

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ArrayList<Uri> pictures = new ArrayList<>();
        Object given = getIntent().getExtras() == null ? null : getIntent().getExtras().get(EXTRA_PICTURES);
        Iterable<?> items = given instanceof Object[] ? Arrays.asList((Object[]) given) : given instanceof Iterable ? (Iterable<?>) given : Collections.emptyList();
        for (Object item : items) pictures.add(item instanceof Uri ? (Uri) item : Uri.parse(String.valueOf(item)));
        ComponentName target = getIntent().getParcelableExtra(EXTRA_TARGET);
        startActivity(new Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, pictures).setComponent(target));
    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }
}
