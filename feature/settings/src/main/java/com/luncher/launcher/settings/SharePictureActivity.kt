package com.luncher.launcher.settings

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import com.luncher.domain.pictures.PictureLimits

/**
 * Where another app's Share of pictures (`SEND`, or `SEND_MULTIPLE` for several) or "Set as"
 * (`ATTACH_DATA`) of one arrives, with no screen of its own: it opens Luncher's home screen, and
 * over it [PictureReceivedActivity] with the pictures' Uris and the read access the other app gave
 * with them, which adds them and says how that went; then it finishes, before it's ever shown. A
 * share of more than [PictureLimits.MAX_SHARED] pictures passes none: the window says that's
 * more than Luncher takes at a time, so the user shares again the ones they want. Android itself
 * can't pass a much larger share (1000 on API 22 to 28), which never arrives. Both open in the home screen's
 * task, so Back from the window is the home screen. This activity has no task affinity
 * (`taskAffinity=""`, the manifest says why), and the window is sent to Luncher's task by its
 * affinity ([open]), so a share never leaves a task of its own behind that the next share would
 * be taken for.
 *
 * While a window shows pictures being added (a session of
 * [ReceivedPictures][com.luncher.domain.pictures.ReceivedPictures]), it sends the window only:
 * Android brings Luncher's task back, and the window, `singleTop`, gets the share, whose pictures
 * join the others. Opening the home screen would close that window instead (it's `singleTask`),
 * and the pictures it was adding with it.
 *
 * The read access passed on is the access Luncher was given: the clip carries only the Uris it
 * holds a grant for, so passing them on can't fail. Any other Uri goes with the pictures without
 * one, for the library to judge: an exported provider's, which Luncher may read anyway, or one it
 * can't read, which ends as the message that it can't.
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
 * the intent, and anything it can't take one from (extras that don't unparcel, no Uri at all)
 * becomes the window's message that Luncher can't read it, never a crash; so does a Uri the other
 * app gave no access to, once the library has tried it. What the Uri points to is the library's
 * to judge ([PictureImport][com.luncher.domain.pictures.PictureImport]).
 */
class SharePictureActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val home = if (graph.receivedPictures.session != 0) null else packageManager.getLeanbackLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val nothing = Intent(this, PictureReceivedActivity::class.java).putExtra(PictureReceivedActivity.EXTRA_NOTHING_READABLE, true)
        val pictures = sharedPictures(intent)
        if (pictures.isEmpty()) {
            open(home, nothing)
        } else if (!PictureLimits.allowsShare(pictures.size)) {
            open(home, Intent(this, PictureReceivedActivity::class.java).putExtra(PictureReceivedActivity.EXTRA_TOO_MANY, true))
        } else {
            try {
                open(home, passOn(pictures))
            } catch (e: SecurityException) {
                // Android refused to pass on a grant after all: nothing to read.
                open(home, nothing)
            }
        }
        finish()   // before onResume, as its theme (windowNoDisplay) requires
    }

    /** The window's intent with [pictures], and the read access to those Luncher holds a grant for. */
    private fun passOn(pictures: List<Uri>): Intent {
        val window = Intent(this, PictureReceivedActivity::class.java).putParcelableArrayListExtra(PictureReceivedActivity.EXTRA_PICTURES, ArrayList(pictures))
        val granted = pictures.filter { checkUriPermission(it, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED }
        if (granted.isEmpty()) return window
        val clip = ClipData.newRawUri("", granted[0])
        for (uri in granted.drop(1)) clip.addItem(ClipData.Item(uri))
        window.clipData = clip
        return window.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /**
     * [window] over the [home] screen, in the home screen's task; alone without one (no entry in
     * the TV's app list, or a window shows already), in Luncher's task or one of its own. The
     * window too is sent to a task of its own, which Android finds by Luncher's affinity, the home
     * screen's: without, it would open in this activity's task, which has no affinity, and there
     * the home screen's task, brought back, could come over it (API 28).
     */
    private fun open(home: Intent?, window: Intent) {
        window.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (home != null) startActivities(arrayOf(home, window)) else startActivity(window)
    }

    /**
     * Every picture [intent] brings, in order, each once: a Share's stream (one, or a list for
     * several), else its clip's items, as an app may send them only there (a `SEND` with several
     * items too, each with its access); "Set as"'s data.
     */
    private fun sharedPictures(intent: Intent): List<Uri> =
        try {
            when (intent.action) {
                Intent.ACTION_SEND -> listOfNotNull(stream(intent)).ifEmpty { clip(intent) }
                Intent.ACTION_SEND_MULTIPLE -> streams(intent).ifEmpty { clip(intent) }
                Intent.ACTION_ATTACH_DATA -> listOfNotNull(intent.data)
                else -> emptyList()
            }.distinct()
        } catch (e: RuntimeException) {
            emptyList()   // extras another app made that don't unparcel here
        }

    private fun clip(intent: Intent): List<Uri> {
        val clip = intent.clipData ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    }

    /** A `SEND_MULTIPLE`'s list; none if its stream is anything else (a single Uri, which some apps send). */
    private fun streams(intent: Intent): List<Uri> =
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty().filterIsInstance<Uri>()
            }
        } catch (e: ClassCastException) {
            emptyList()
        }

    private fun stream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
}
