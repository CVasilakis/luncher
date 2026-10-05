package com.luncher.launcher

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * When [waitForHomeScreen] takes the device for its home screen, decided on dumps the emulators
 * produced (`cmd package resolve-activity`, `dumpsys window`, `dumpsys activity activities`),
 * trimmed to the lines [HomeLook] reads. Here rather than on the JVM because the helper is in
 * androidTest, like [SystemTierFilterTest]; it needs no particular device. Also which states count
 * as on the way to the home screen, and which as another app's screen ([HomeLook.anotherApp]);
 * the dumps of those marked "made up" are put together in the format of the emulators' dumps. And
 * whether API 22's "choose home app" dialog has begun to finish ([homeChooserNotFinishing]).
 */
@RunWith(AndroidJUnit4::class)
class HomeLookTest {

    @Test
    fun stockLauncherInFront_isTheHomeScreen() {
        val look = HomeLook(
            36,
            "com.google.android.tvlauncher/.MainActivity",
            """
            mCurrentFocus=Window{e3c58b2 u0 com.google.android.tvlauncher/com.google.android.tvlauncher.MainActivity}
            mFocusedApp=ActivityRecord{53779537 u0 com.google.android.tvlauncher/.MainActivity t268}
            """,
            """
            * Hist  #0: ActivityRecord{53779537 u0 com.google.android.tvlauncher/.MainActivity t268}
              state=RESUMED delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true
            * Hist  #0: ActivityRecord{267761530 u0 com.android.systemui/.tv.sensorprivacy.TvSensorPrivacyChangedActivity t2}
              state=INITIALIZING delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=false
            """,
        )
        assertTrue(look.toString(), look.isSettled)
    }

    /** API 36's stock launcher, cold after HomeKeyTest: focused for 5 s before its main thread went idle. */
    @Test
    fun stockLauncherStillStarting_isNotTheHomeScreen() {
        val look = HomeLook(
            36,
            "com.google.android.tvlauncher/.MainActivity",
            """
            mCurrentFocus=Window{1bd8757 u0 com.google.android.tvlauncher/com.google.android.tvlauncher.MainActivity}
            mFocusedApp=ActivityRecord{110065534 u0 com.google.android.tvlauncher/.MainActivity t270}
            """,
            """
            * Hist  #0: ActivityRecord{110065534 u0 com.google.android.tvlauncher/.MainActivity t270}
              state=RESUMED delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=false
            * Hist  #0: ActivityRecord{155463753 u0 com.luncher.launcher/.home.HomeActivity t269}
              state=PAUSED delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true
            """,
        )
        assertFalse(look.toString(), look.isSettled)
    }

    /** HomeKeyTest's: the stock launcher disabled. */
    @Test
    fun luncherAsTheHomeApp_isTheHomeScreen() {
        val look = HomeLook(
            36,
            "com.luncher.launcher/.home.HomeActivity",
            """
            mCurrentFocus=Window{2f81404 u0 com.luncher.launcher/com.luncher.launcher.home.HomeActivity}
            mFocusedApp=ActivityRecord{155463753 u0 com.luncher.launcher/.home.HomeActivity t269}
            """,
            """
            * Hist  #0: ActivityRecord{155463753 u0 com.luncher.launcher/.home.HomeActivity t269}
              state=RESUMED delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true
            * Hist  #0: ActivityRecord{247676207 u0 com.android.tv.settings/.MainSettings t272}
              state=STOPPED delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true
            """,
        )
        assertTrue(look.toString(), look.isSettled)
    }

    @Test
    fun anotherAppInFront_isNotTheHomeScreen() {
        val look = HomeLook(
            36,
            "com.google.android.tvlauncher/.MainActivity",
            """
            mCurrentFocus=Window{af46328 u0 com.android.tv.settings/com.android.tv.settings.MainSettings}
            mFocusedApp=ActivityRecord{247676207 u0 com.android.tv.settings/.MainSettings t272}
            """,
            """
            * Hist  #0: ActivityRecord{247676207 u0 com.android.tv.settings/.MainSettings t272}
              state=RESUMED delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true
            * Hist  #0: ActivityRecord{171127362 u0 com.google.android.tvlauncher/.MainActivity t273}
              state=PAUSED delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true
            """,
        )
        assertFalse(look.toString(), look.isSettled)
    }

    /** Google TV (API 31) after a starved boot: the launcher's trampoline, in a task of its own. */
    @Test
    fun googleTvDispatchActivity_isNotTheHomeScreen() {
        val look = HomeLook(
            31,
            LAUNCHERX_HOME,
            """
            mCurrentFocus=Window{b99fc88 u0 com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.coreservices.bootmode.DispatchActivity}
            mFocusedApp=ActivityRecord{d2732d0 u0 com.google.android.apps.tv.launcherx/.coreservices.bootmode.DispatchActivity t4}
            """,
            """
            * Hist #0: ActivityRecord{d2732d0 u0 com.google.android.apps.tv.launcherx/.coreservices.bootmode.DispatchActivity t4}
              state=RESUMED stopped=false delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true mStartingWindowState=STARTING_WINDOW_SHOWN
            * Hist #0: ActivityRecord{97a36c0 u0 com.google.android.apps.tv.launcherx/.home.HomeActivity t3}
              state=STOPPING stopped=false delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true mStartingWindowState=STARTING_WINDOW_NOT_SHOWN
            """,
        )
        assertFalse(look.toString(), look.isSettled)
    }

    /** Google TV (API 33, starved), back from Settings: the launcher's main thread is still busy, the chooser to come. */
    @Test
    fun googleTvHomeActivityStillResuming_isNotTheHomeScreen() {
        val look = HomeLook(
            33,
            LAUNCHERX_HOME,
            """
            mCurrentFocus=Window{667f478 u0 com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.home.HomeActivity}
            mFocusedApp=ActivityRecord{af5a038 u0 com.google.android.apps.tv.launcherx/.home.HomeActivity} t54}
            """,
            """
            * Hist  #0: ActivityRecord{af5a038 u0 com.google.android.apps.tv.launcherx/.home.HomeActivity} t54}
              state=RESUMED stopped=false delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=false
            * Hist  #0: ActivityRecord{5bce39 u0 com.android.tv.settings/.MainSettings} t56}
              state=PAUSED stopped=false delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true
            * Hist  #0: ActivityRecord{adc0b13 u0 com.google.android.apps.tv.launcherx/.coreservices.bootmode.DispatchActivity} t55}
              state=STOPPED stopped=true delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true
            """,
        )
        assertFalse(look.toString(), look.isSettled)
    }

    /** Google TV (API 31, starved): the chooser is already on top, but HomeActivity still has the focus. */
    @Test
    fun googleTvChooserComingUp_isNotTheHomeScreen() {
        val look = HomeLook(
            31,
            LAUNCHERX_HOME,
            """
            mCurrentFocus=Window{92bd30e u0 com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.home.HomeActivity}
            mFocusedApp=ActivityRecord{4c189ad u0 com.google.android.apps.tv.launcherx/.home.HomeActivity t123}
            """,
            """
            * Hist #1: ActivityRecord{17486f9 u0 com.google.android.apps.tv.launcherx/.profile.chooser.ProfileChooserActivity t123}
              state=RESUMED stopped=false delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=false mStartingWindowState=STARTING_WINDOW_NOT_SHOWN
            * Hist #0: ActivityRecord{4c189ad u0 com.google.android.apps.tv.launcherx/.home.HomeActivity t123}
              state=PAUSED stopped=false delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=false mStartingWindowState=STARTING_WINDOW_NOT_SHOWN
            * Hist #0: ActivityRecord{5ef996 u0 com.android.tv.settings/.MainSettings t127}
              state=PAUSED stopped=false delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true mStartingWindowState=STARTING_WINDOW_REMOVED
            """,
        )
        assertFalse(look.toString(), look.isSettled)
    }

    /** Google TV (API 33): the profile chooser HomeActivity opened over itself, in its task, is the home screen. */
    @Test
    fun googleTvChooserOverItsHomeActivity_isTheHomeScreen() {
        val look = HomeLook(
            33,
            LAUNCHERX_HOME,
            """
            mCurrentFocus=Window{436542c u0 com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.profile.chooser.ProfileChooserActivity}
            mFocusedApp=ActivityRecord{5e6ffcc u0 com.google.android.apps.tv.launcherx/.profile.chooser.ProfileChooserActivity} t88}
            """,
            """
            * Hist  #1: ActivityRecord{5e6ffcc u0 com.google.android.apps.tv.launcherx/.profile.chooser.ProfileChooserActivity} t88}
              state=RESUMED stopped=false delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true
            * Hist  #0: ActivityRecord{d04d35a u0 com.google.android.apps.tv.launcherx/.home.HomeActivity} t88}
              state=STOPPED stopped=true delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true
            * Hist  #0: ActivityRecord{c1960b2 u0 com.android.tv.settings/.MainSettings} t84}
              state=STOPPED stopped=true delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true
            """,
        )
        assertTrue(look.toString(), look.isSettled)
    }

    /**
     * Google TV (API 31, starved), after an ANR: `dumpsys window` starts with the state at that time
     * (WINDOW MANAGER LAST ANR), focus included; the current one comes later.
     */
    @Test
    fun googleTvChooserAfterAnAnr_isTheHomeScreen() {
        val look = HomeLook(
            31,
            LAUNCHERX_HOME,
            """
            mCurrentFocus=Window{59570 u0 com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.home.HomeActivity}
            mFocusedApp=ActivityRecord{97a36c0 u0 com.google.android.apps.tv.launcherx/.home.HomeActivity t3}
            mCurrentFocus=Window{1947a15 u0 com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.profile.chooser.ProfileChooserActivity}
            mFocusedApp=ActivityRecord{8d251c4 u0 com.google.android.apps.tv.launcherx/.profile.chooser.ProfileChooserActivity t3}
            """,
            """
            * Hist #1: ActivityRecord{8d251c4 u0 com.google.android.apps.tv.launcherx/.profile.chooser.ProfileChooserActivity t3}
              state=RESUMED stopped=false delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true mStartingWindowState=STARTING_WINDOW_NOT_SHOWN
            * Hist #0: ActivityRecord{97a36c0 u0 com.google.android.apps.tv.launcherx/.home.HomeActivity t3}
              state=STOPPING stopped=false delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true mStartingWindowState=STARTING_WINDOW_NOT_SHOWN
            """,
        )
        assertTrue(look.toString(), look.isSettled)
        assertEquals("com.google.android.apps.tv.launcherx/.profile.chooser.ProfileChooserActivity", look.focus.activity)
    }

    /** API 22 with Luncher installed: Home asks which home app to use, in a task the HOME intent started. */
    @Test
    fun chooseHomeAppDialog_isTheHomeScreen() {
        val look = HomeLook(
            22,
            null,
            """
            mFocusedApp=Token{af0e5cd ActivityRecord{179a7f64 u0 android/com.android.internal.app.ResolverActivity t5}}
            mCurrentFocus=Window{25456fc u0 android/com.android.internal.app.ResolverActivity}
            mFocusedApp=AppWindowToken{18247e82 token=Token{af0e5cd ActivityRecord{179a7f64 u0 android/com.android.internal.app.ResolverActivity t5}}}
            """,
            """
            * TaskRecord{83f5bda #5 I=android/com.android.internal.app.ResolverActivity U=0 sz=1}
              intent={act=android.intent.action.MAIN cat=[android.intent.category.HOME] flg=0x10a00000 cmp=android/com.android.internal.app.ResolverActivity}
              * Hist #0: ActivityRecord{179a7f64 u0 android/com.android.internal.app.ResolverActivity t5}
                state=RESUMED stopped=false delayedResume=false finishing=false
                keysPaused=false inHistory=true visible=true sleeping=false idle=true
            * TaskRecord{287db0af #2 A=com.android.tv.settings U=0 sz=1}
              intent={act=android.settings.SETTINGS flg=0x10000000 cmp=com.android.tv.settings/.MainSettings}
              * Hist #0: ActivityRecord{1971a648 u0 com.android.tv.settings/.MainSettings t2}
                state=STOPPED stopped=true delayedResume=false finishing=false
                keysPaused=false inHistory=true visible=true sleeping=false idle=true
            """,
        )
        assertTrue(look.toString(), look.isSettled)
    }

    /** API 22: the stock launcher, just started by a HOME intent, before its main thread went idle. */
    @Test
    fun stockLauncherStillStarting_beforeApi24_isNotTheHomeScreen() {
        val look = HomeLook(
            22,
            null,
            """
            mFocusedApp=Token{10d155f9 ActivityRecord{27eb24c0 u0 com.google.android.leanbacklauncher/.MainActivity t3}}
            mCurrentFocus=Window{38c69fb4 u0 com.google.android.leanbacklauncher/com.google.android.leanbacklauncher.MainActivity}
            mFocusedApp=AppWindowToken{28e9a93e token=Token{10d155f9 ActivityRecord{27eb24c0 u0 com.google.android.leanbacklauncher/.MainActivity t3}}}
            """,
            """
            * TaskRecord{b796e5a #3 A=com.google.android.leanbacklauncher U=0 sz=1}
              intent={act=android.intent.action.MAIN cat=[android.intent.category.HOME] flg=0x10000000 cmp=com.google.android.leanbacklauncher/.MainActivity}
              * Hist #0: ActivityRecord{27eb24c0 u0 com.google.android.leanbacklauncher/.MainActivity t3}
                state=RESUMED stopped=false delayedResume=false finishing=false
                keysPaused=false inHistory=true visible=true sleeping=false idle=false
            * TaskRecord{287db0af #2 A=com.android.tv.settings U=0 sz=1}
              intent={act=android.settings.SETTINGS flg=0x10000000 cmp=com.android.tv.settings/.MainSettings}
              * Hist #0: ActivityRecord{1971a648 u0 com.android.tv.settings/.MainSettings t2}
                state=PAUSED stopped=false delayedResume=false finishing=false
                keysPaused=false inHistory=true visible=false sleeping=false idle=true
            """,
        )
        assertFalse(look.toString(), look.isSettled)
    }

    /** API 23: the stock launcher, in a task a HOME intent started, over "USB drive connected". */
    @Test
    fun stockLauncherInFront_beforeApi24_isTheHomeScreen() {
        val look = HomeLook(
            23,
            null,
            """
            mFocusedApp=Token{4e9b05d ActivityRecord{ac49a34 u0 com.google.android.leanbacklauncher/.MainActivity t2}}
            mCurrentFocus=Window{44b339a u0 com.google.android.leanbacklauncher/com.google.android.leanbacklauncher.MainActivity}
            mFocusedApp=AppWindowToken{2a9dd2 token=Token{4e9b05d ActivityRecord{ac49a34 u0 com.google.android.leanbacklauncher/.MainActivity t2}}}
            """,
            """
            * TaskRecord{78919e4 #2 A=com.google.android.leanbacklauncher U=0 sz=1}
              intent={act=android.intent.action.MAIN cat=[android.intent.category.HOME] flg=0x10000000 cmp=com.google.android.leanbacklauncher/.MainActivity}
              * Hist #0: ActivityRecord{ac49a34 u0 com.google.android.leanbacklauncher/.MainActivity t2}
                state=RESUMED stopped=false delayedResume=false finishing=false
                keysPaused=false inHistory=true visible=true sleeping=false idle=true
            * TaskRecord{59bae4d #1 I=com.android.tv.settings/.device.storage.NewStorageActivity U=0 sz=1}
              intent={act=com.android.tv.settings.device.storage.NewStorageActivity.NEW_STORAGE flg=0x10008000 cmp=com.android.tv.settings/.device.storage.NewStorageActivity}
              * Hist #0: ActivityRecord{6572a0 u0 com.android.tv.settings/.device.storage.NewStorageActivity t1}
                state=STOPPED stopped=true delayedResume=false finishing=false
                keysPaused=false inHistory=true visible=false sleeping=false idle=true
            """,
        )
        assertTrue(look.toString(), look.isSettled)
    }

    @Test
    fun anotherAppInFront_isAnotherAppsScreen() {
        val look = HomeLook(
            36,
            "com.google.android.tvlauncher/.MainActivity",
            """
            mCurrentFocus=Window{af46328 u0 com.android.tv.settings/com.android.tv.settings.MainSettings}
            mFocusedApp=ActivityRecord{247676207 u0 com.android.tv.settings/.MainSettings t272}
            """,
            """
            * Hist  #0: ActivityRecord{247676207 u0 com.android.tv.settings/.MainSettings t272}
              state=RESUMED delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true
            * Hist  #0: ActivityRecord{171127362 u0 com.google.android.tvlauncher/.MainActivity t273}
              state=PAUSED delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true
            """,
        )
        assertEquals("com.android.tv.settings/.MainSettings", look.anotherApp?.activity)
    }

    /** Luncher's home screen left in front of the stock launcher, the home app: another app's (made up). */
    @Test
    fun luncherInFrontOfTheStockLauncher_isAnotherAppsScreen() {
        val look = HomeLook(
            36,
            "com.google.android.tvlauncher/.MainActivity",
            """
            mCurrentFocus=Window{2f81404 u0 com.luncher.launcher/com.luncher.launcher.home.HomeActivity}
            mFocusedApp=ActivityRecord{155463753 u0 com.luncher.launcher/.home.HomeActivity t269}
            """,
            """
            * Hist  #0: ActivityRecord{155463753 u0 com.luncher.launcher/.home.HomeActivity t269}
              state=RESUMED delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true
            * Hist  #0: ActivityRecord{110065534 u0 com.google.android.tvlauncher/.MainActivity t270}
              state=STOPPED delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true
            """,
        )
        assertEquals("com.luncher.launcher/.home.HomeActivity", look.anotherApp?.activity)
    }

    /** A window that isn't an activity's, like a dialog of the system over the home app, is another app's (made up). */
    @Test
    fun aDialogOfTheSystemOverTheHomeApp_isAnotherAppsScreen() {
        val look = HomeLook(
            33,
            LAUNCHERX_HOME,
            """
            mCurrentFocus=Window{5e1b0d2 u0 Application Not Responding: com.google.android.apps.tv.launcherx}
            mFocusedApp=ActivityRecord{af5a038 u0 com.google.android.apps.tv.launcherx/.home.HomeActivity} t54}
            """,
            """
            * Hist  #0: ActivityRecord{af5a038 u0 com.google.android.apps.tv.launcherx/.home.HomeActivity} t54}
              state=RESUMED stopped=false delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true
            """,
        )
        assertFalse(look.toString(), look.isSettled)
        assertEquals("Application Not Responding: com.google.android.apps.tv.launcherx", look.anotherApp?.window)
    }

    /** Google TV's DispatchActivity: in a task of its own, not the home screen, but on the way there. */
    @Test
    fun googleTvDispatchActivity_isOnTheWayToTheHomeScreen() {
        val look = HomeLook(
            31,
            LAUNCHERX_HOME,
            """
            mCurrentFocus=Window{b99fc88 u0 com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.coreservices.bootmode.DispatchActivity}
            mFocusedApp=ActivityRecord{d2732d0 u0 com.google.android.apps.tv.launcherx/.coreservices.bootmode.DispatchActivity t4}
            """,
            """
            * Hist #0: ActivityRecord{d2732d0 u0 com.google.android.apps.tv.launcherx/.coreservices.bootmode.DispatchActivity t4}
              state=RESUMED stopped=false delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true mStartingWindowState=STARTING_WINDOW_SHOWN
            """,
        )
        assertNull(look.toString(), look.anotherApp)
    }

    /** Before the user is unlocked: Settings' FallbackHome in front, and what HOME resolves to (made up). */
    @Test
    fun fallbackHome_isOnTheWayToTheHomeScreen() {
        val look = HomeLook(
            30,
            "com.android.tv.settings/.system.FallbackHome",
            """
            mCurrentFocus=Window{3b1a7e2 u0 com.android.tv.settings/com.android.tv.settings.system.FallbackHome}
            mFocusedApp=ActivityRecord{7d0c9a1 u0 com.android.tv.settings/.system.FallbackHome t2}
            """,
            """
            * Hist #0: ActivityRecord{7d0c9a1 u0 com.android.tv.settings/.system.FallbackHome t2}
              state=RESUMED stopped=false delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=true
            """,
        )
        assertFalse(look.toString(), look.isSettled)
        assertNull(look.toString(), look.anotherApp)
    }

    /** Right after a boot, or while one screen hands over to the next: no window has the focus (made up). */
    @Test
    fun noFocusedWindow_isOnTheWayToTheHomeScreen() {
        val look = HomeLook(
            36,
            "com.google.android.tvlauncher/.MainActivity",
            """
            mCurrentFocus=null
            mFocusedApp=ActivityRecord{247676207 u0 com.android.tv.settings/.MainSettings t272}
            """,
            """
            * Hist  #0: ActivityRecord{247676207 u0 com.android.tv.settings/.MainSettings t272}
              state=RESUMED delayedResume=false finishing=false
              keysPaused=false inHistory=true idle=false
            """,
        )
        assertNull(look.toString(), look.anotherApp)
    }

    /** API 22: Settings in front of the launcher, in the task Home started (made up). */
    @Test
    fun anotherAppInFront_beforeApi24_isAnotherAppsScreen() {
        val look = HomeLook(
            22,
            null,
            """
            mFocusedApp=Token{3a1f6c2 ActivityRecord{1971a648 u0 com.android.tv.settings/.MainSettings t2}}
            mCurrentFocus=Window{2e9d1b7 u0 com.android.tv.settings/com.android.tv.settings.MainSettings}
            mFocusedApp=AppWindowToken{1c0e2f5 token=Token{3a1f6c2 ActivityRecord{1971a648 u0 com.android.tv.settings/.MainSettings t2}}}
            """,
            """
            * TaskRecord{287db0af #2 A=com.android.tv.settings U=0 sz=1}
              intent={act=android.settings.SETTINGS flg=0x10000000 cmp=com.android.tv.settings/.MainSettings}
              * Hist #0: ActivityRecord{1971a648 u0 com.android.tv.settings/.MainSettings t2}
                state=RESUMED stopped=false delayedResume=false finishing=false
                keysPaused=false inHistory=true visible=true sleeping=false idle=true
            * TaskRecord{b796e5a #3 A=com.google.android.leanbacklauncher U=0 sz=1}
              intent={act=android.intent.action.MAIN cat=[android.intent.category.HOME] flg=0x10000000 cmp=com.google.android.leanbacklauncher/.MainActivity}
              * Hist #0: ActivityRecord{27eb24c0 u0 com.google.android.leanbacklauncher/.MainActivity t3}
                state=STOPPED stopped=true delayedResume=false finishing=false
                keysPaused=false inHistory=true visible=false sleeping=false idle=true
            """,
        )
        assertEquals("com.android.tv.settings/.MainSettings", look.anotherApp?.activity)
    }

    /** API 22's "choose home app" dialog, of package android, in the task Home started: the home app's. */
    @Test
    fun chooseHomeAppDialog_isNotAnotherAppsScreen() {
        val look = HomeLook(
            22,
            null,
            """
            mFocusedApp=Token{af0e5cd ActivityRecord{179a7f64 u0 android/com.android.internal.app.ResolverActivity t5}}
            mCurrentFocus=Window{25456fc u0 android/com.android.internal.app.ResolverActivity}
            """,
            """
            * TaskRecord{83f5bda #5 I=android/com.android.internal.app.ResolverActivity U=0 sz=1}
              intent={act=android.intent.action.MAIN cat=[android.intent.category.HOME] flg=0x10a00000 cmp=android/com.android.internal.app.ResolverActivity}
              * Hist #0: ActivityRecord{179a7f64 u0 android/com.android.internal.app.ResolverActivity t5}
                state=RESUMED stopped=false delayedResume=false finishing=false
                keysPaused=false inHistory=true visible=true sleeping=false idle=false
            """,
        )
        assertNull(look.toString(), look.anotherApp)
    }

    /** API 22, the dialog in front after a boot with Luncher installed. */
    @Test
    fun homeChooserInFront_isNotFinishing() {
        assertTrue(
            homeChooserNotFinishing(
                """
                * Hist #0: ActivityRecord{1920a499 u0 android/com.android.internal.app.ResolverActivity t1}
                  state=RESUMED stopped=false delayedResume=false finishing=false
                  keysPaused=false inHistory=true visible=true sleeping=false idle=true
                """,
            ),
        )
    }

    /** API 22, 0.03 s after the test's screen covered the dialog: a HOME intent now would be lost. */
    @Test
    fun homeChooserJustCovered_isNotFinishing() {
        assertTrue(
            homeChooserNotFinishing(
                """
                * Hist #0: ActivityRecord{42f7dee u0 com.luncher.launcher/.home.HomeActivity t2}
                  state=RESUMED stopped=false delayedResume=false finishing=false
                  keysPaused=false inHistory=true visible=true sleeping=false idle=false
                * Hist #0: ActivityRecord{1920a499 u0 android/com.android.internal.app.ResolverActivity t1}
                  state=PAUSED stopped=false delayedResume=false finishing=false
                  keysPaused=false inHistory=true visible=false sleeping=false idle=true
                """,
            ),
        )
    }

    /** API 22, 0.7 s later: the dialog finishing, still listed; Home now opens a new one. */
    @Test
    fun homeChooserFinishing_isFinishing() {
        assertFalse(
            homeChooserNotFinishing(
                """
                * Hist #0: ActivityRecord{42f7dee u0 com.luncher.launcher/.home.HomeActivity t2}
                  state=RESUMED stopped=false delayedResume=false finishing=false
                  keysPaused=false inHistory=true visible=true sleeping=false idle=true
                * Hist #0: ActivityRecord{1920a499 u0 android/com.android.internal.app.ResolverActivity t1 f}
                  state=FINISHING stopped=false delayedResume=false finishing=true
                  keysPaused=false inHistory=true visible=false sleeping=false idle=true
                """,
            ),
        )
    }

    /** From API 23 on Home never asks: no dialog to wait for. */
    @Test
    fun noHomeChooser_isNotFinishing() {
        assertFalse(
            homeChooserNotFinishing(
                """
                * Hist  #0: ActivityRecord{53779537 u0 com.google.android.tvlauncher/.MainActivity t268}
                  state=RESUMED delayedResume=false finishing=false
                  keysPaused=false inHistory=true idle=true
                """,
            ),
        )
    }

    private companion object {
        const val LAUNCHERX_HOME = "com.google.android.apps.tv.launcherx/.home.HomeActivity"
    }
}
