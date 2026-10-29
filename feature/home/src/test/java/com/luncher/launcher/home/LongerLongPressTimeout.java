package com.luncher.launcher.home;

import android.view.ViewConfiguration;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowViewConfiguration;

/**
 * Android's long-press timeout lengthened to 1 s, as in the accessibility settings: Robolectric's
 * is 500 ms, and can't be set. In Java, as Kotlin can't hide the static method it shadows.
 */
@Implements(ViewConfiguration.class)
public class LongerLongPressTimeout extends ShadowViewConfiguration {

    @Implementation
    protected static int getLongPressTimeout() {
        return 1000;
    }
}
