package com.luncher.launcher

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Which crash [crashSince] names, decided on the crash log as an emulator printed it: Android TV
 * 16's Settings app crashing on a starved tv_api36 (docs/TESTING.md). Here rather than on the JVM
 * because the helper is in androidTest, like [HomeLookTest]; it needs no particular device.
 */
@RunWith(AndroidJUnit4::class)
class CrashesTest {

    @Test
    fun crashOfTheAppSinceTheTime_isNamedByItsFirstLine() {
        assertEquals(SETTINGS_NPE, crashIn(LOG, SETTINGS, CRASHED_AT - 1_000))
        assertEquals(SETTINGS_NPE, crashIn(LOG, SETTINGS, CRASHED_AT))
    }

    @Test
    fun crashBeforeTheTime_isNotNamed() {
        assertNull(crashIn(LOG, SETTINGS, CRASHED_AT + 1))
    }

    @Test
    fun crashOfAnotherApp_isNotNamed() {
        assertNull(crashIn(LOG, "com.android.tv", 0))
        assertNull(crashIn(LOG, "com.android.tv.settings.extra", 0))
    }

    @Test
    fun crashOfAnotherProcessOfTheApp_isNamed() {
        val log = LOG.replace("Process: $SETTINGS, PID", "Process: $SETTINGS:remote, PID")
        assertEquals(SETTINGS_NPE, crashIn(log, SETTINGS, 0))
    }

    @Test
    fun theLastOfSeveralCrashes_isNamed() {
        val later = """
              1790971100.000  1002  1002 E AndroidRuntime: FATAL EXCEPTION: main
              1790971100.000  1002  1002 E AndroidRuntime: Process: com.android.tv.settings, PID: 1002
              1790971100.000  1002  1002 E AndroidRuntime: java.lang.IllegalStateException: later
            """.trimIndent()
        assertEquals("java.lang.IllegalStateException: later", crashIn("$LOG\n$later", SETTINGS, 0))
    }

    @Test
    fun noCrashLogged_isNull() {
        assertNull(crashIn("", SETTINGS, 0))
        assertNull(crashIn("--------- beginning of crash\n", SETTINGS, 0))
    }

    private companion object {
        const val SETTINGS = "com.android.tv.settings"
        const val CRASHED_AT = 1_790_971_043_892L
        const val SETTINGS_NPE = "java.lang.NullPointerException: Attempt to invoke virtual method " +
            "'java.lang.Class java.lang.Object.getClass()' on a null object reference"

        // The crash a starved tv_api36 logged, with its time in the -v epoch format the helper reads.
        val LOG = """
            --------- beginning of crash
                  1790971043.892   941   941 E AndroidRuntime: FATAL EXCEPTION: main
                  1790971043.892   941   941 E AndroidRuntime: Process: com.android.tv.settings, PID: 941
                  1790971043.892   941   941 E AndroidRuntime: $SETTINGS_NPE
                  1790971043.892   941   941 E AndroidRuntime: 	at com.android.tv.settings.MainFragment.onSuggestionReady(Unknown Source:0)
                  1790971043.892   941   941 E AndroidRuntime: 	at com.android.tv.settings.TvSuggestionControllerMixinCompat.onLoadFinished(TvSuggestionControllerMixinCompat.java:136)
        """.trimIndent()
    }
}
