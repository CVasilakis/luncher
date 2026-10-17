package com.luncher.launcher.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle

/**
 * Where another app's Share (`SEND`) or "Set as" (`ATTACH_DATA`) of a picture arrives, with no
 * screen of its own: it opens Luncher's home screen, and over it [PictureReceivedActivity] with
 * the picture's Uri and the read access the other app gave with it, which adds the picture and
 * says how that went; then it finishes, before it's ever shown. Both open in the home screen's
 * task, so Back from the window is the home screen.
 *
 * The home screen is opened as the TV's app list opens Luncher, with the intent Android gives for
 * its `LEANBACK_LAUNCHER` entry (`getLeanbackLaunchIntentForPackage`): Luncher's, whether or not
 * it's the device's home app, and this feature needs nothing of :feature:home's. Not a `HOME`
 * intent, which would make its task a home screen's: closed, Android would show the device's home
 * app, and up to Android 5.1 ask which one when there's no default. Nor `MAIN` +
 * `LEANBACK_LAUNCHER` itself: an implicit start needs a filter with `DEFAULT`, which that one has
 * not. Should Android give no such intent, the window opens alone.
 *
 * Exported, so any app can send it anything: it reads nothing itself, only takes the Uri out of
 * the intent, and anything it can't take one from (extras that don't unparcel, no Uri at all) or
 * can't pass on (a Uri the other app gave no access to) becomes the window's message that Luncher
 * can't read it, never a crash. What the Uri points to is the library's to judge
 * ([PictureImport][com.luncher.domain.pictures.PictureImport]).
 */
class SharePictureActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val home = packageManager.getLeanbackLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val nothing = Intent(this, PictureReceivedActivity::class.java).putExtra(PictureReceivedActivity.EXTRA_NOTHING_READABLE, true)
        val picture = sharedPicture(intent)
        if (picture == null) {
            open(home, nothing)
        } else {
            try {
                open(home, Intent(this, PictureReceivedActivity::class.java).setData(picture).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (e: SecurityException) {
                // The other app gave no access to pass on (nor could Luncher read it): Android
                // refuses the grant.
                open(home, nothing)
            }
        }
        finish()   // before onResume, as its theme (windowNoDisplay) requires
    }

    /** [window] over the [home] screen, in the home screen's task; alone, in a task of its own, without one. */
    private fun open(home: Intent?, window: Intent) {
        if (home != null) startActivities(arrayOf(home, window)) else startActivity(window.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun sharedPicture(intent: Intent): Uri? =
        try {
            when (intent.action) {
                Intent.ACTION_SEND -> stream(intent) ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                Intent.ACTION_ATTACH_DATA -> intent.data
                else -> null
            }
        } catch (e: RuntimeException) {
            null   // extras another app made that don't unparcel here
        }

    private fun stream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
}
