package com.luncher.launcher.settings

import android.app.Activity
import android.app.ActivityManager
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
 * can't pass a much larger share (1000 on API 22 to 28), which never arrives. Back from the
 * window is the home screen: both open in the home screen's task, but on API 24 to 27 while
 * Luncher is the home app, where the window has a task of its own, over it. This activity has no task affinity
 * (`taskAffinity=""`, the manifest says why), and the window is sent to Luncher's task by its
 * affinity ([openOver]), so a share never leaves a task of its own behind that the next share would
 * be taken for.
 *
 * While a window shows pictures being added (a session of
 * [ReceivedPictures][com.luncher.domain.pictures.ReceivedPictures]), on top of one of Luncher's
 * tasks ([windowShows]), it sends the window only: Android brings Luncher's task back, and the
 * window, `singleTop`, gets the share, whose pictures join the others. Opening the home screen
 * would close that window instead (it's `singleTask`), and the pictures it was adding with it.
 * With a session open but no window showing, it opens the home screen and a new window, which
 * takes the session over ([PictureReceivedActivity.EXTRA_TAKE_OVER]); this activity holds the
 * session meanwhile, so the old window, destroyed only later, can't end it. That's API 29, while
 * Luncher isn't the home app: Android closes Luncher's task as soon as another app's opens, as an
 * activity excluded from Recents (the home screen) keeps its task there only while it's the
 * latest, and the window's session stays open until it's destroyed.
 *
 * The read access passed on is the access Luncher was given: the clip carries only the Uris it
 * holds a grant for, so passing them on can't fail; while a session is open, also those of the
 * pictures it still has to add, as the window that gets the share may be a new one that takes the
 * session over (on API 29, where Android closes Luncher's task, and the old window with its
 * access, when another app's task opens). Any other Uri goes with the pictures without
 * one, for the library to judge: an exported provider's, which Luncher may read anyway, or one it
 * can't read, which ends as the message that it can't.
 *
 * While Luncher is the device's home app (a `HOME` intent resolves to it), the home screen is
 * opened as Home opens it: with a `HOME` intent to Luncher's package, naming no component. On API
 * 24 to 27 a home screen opened any other way, even by a `HOME` intent naming the component, is a
 * second one, in a task of its own beside the home app's; Android 7 to 8.1 also keep every other
 * activity out of the home screen's task, so the window opens in a task of its own there. Else
 * the home screen is opened as the TV's app list opens Luncher, with the intent Android gives for
 * its `LEANBACK_LAUNCHER` entry (`getLeanbackLaunchIntentForPackage`): Luncher's, whether or not
 * it's the device's home app. Not a `HOME` intent then, which would make its task a home screen's:
 * closed, Android would show the device's home app, and up to Android 5.1 ask which one when
 * there's no default. Nor `MAIN` + `LEANBACK_LAUNCHER` itself: an implicit start needs a filter
 * with `DEFAULT`, which that one has not. Either way this feature needs nothing of
 * :feature:home's. Should Android give no such intent, the window opens alone. Which home app is
 * the default needs every home app visible from API 30 on (the manifest's `<queries>`).
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
        val received = graph.receivedPictures
        // The window alone, to the window that shows: Android brings its task back, and it gets the
        // share. Else the home screen and a new window; one that's gone still holding the session
        // (on API 29 Android closes Luncher's task when another app's opens, the session left open
        // until the window is destroyed, later), the new one takes it over, this one holding it
        // meanwhile, so the old one can't end it.
        val joining = received.session != 0 && windowShows()
        val takingOver = received.session != 0 && !joining
        if (takingOver) received.hold()
        val home = if (joining) null else homeScreen()
        val pictures = sharedPictures(intent)
        val window = Intent(this, PictureReceivedActivity::class.java).putExtra(PictureReceivedActivity.EXTRA_TAKE_OVER, takingOver)
        val passed = when {
            pictures.isEmpty() -> emptyList<Uri>().also { window.putExtra(PictureReceivedActivity.EXTRA_NOTHING_READABLE, true) }
            !PictureLimits.allowsShare(pictures.size) -> emptyList<Uri>().also { window.putExtra(PictureReceivedActivity.EXTRA_TOO_MANY, true) }
            else -> pictures.also { window.putParcelableArrayListExtra(PictureReceivedActivity.EXTRA_PICTURES, ArrayList(it)) }
        }
        try {
            // The pictures still to add too: the window that gets this may be a new one, which
            // takes the session over (API 29), and the old one's access goes with it.
            openOver(home, withAccess(window, (received.pending + passed).distinct()))
        } catch (e: SecurityException) {
            // Android refused to pass on a grant after all: nothing to read.
            openOver(home, Intent(this, PictureReceivedActivity::class.java).putExtra(PictureReceivedActivity.EXTRA_NOTHING_READABLE, true))
        }
        finish()   // before onResume, as its theme (windowNoDisplay) requires
    }

    /**
     * Luncher's home screen, in a task of its own: as Home opens it while Luncher is the device's
     * home app, a `HOME` intent to its package, naming no component; else as the TV's app list
     * opens it. Null if Android gives no such intent.
     */
    private fun homeScreen(): Intent? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val default = packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        val screen = if (default == packageName) home.setPackage(packageName) else packageManager.getLeanbackLaunchIntentForPackage(packageName)
        return screen?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** [window] with the read access to those of [pictures] Luncher holds a grant for, in its clip: the only access it may pass on. */
    private fun withAccess(window: Intent, pictures: List<Uri>): Intent {
        val granted = pictures.filter { checkUriPermission(it, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED }
        if (granted.isEmpty()) return window
        val clip = ClipData.newRawUri("", granted[0])
        for (uri in granted.drop(1)) clip.addItem(ClipData.Item(uri))
        window.clipData = clip
        return window.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /**
     * [window] over the [home] screen, in the home screen's task (on API 24 to 27 while Luncher is
     * the home app, a task of its own); alone without one (no entry in the TV's app list, or a
     * window shows already), in Luncher's task or one of its own. The
     * window too is sent to a task of its own, which Android finds by Luncher's affinity, the home
     * screen's: without, it would open in this activity's task, which has no affinity, and there
     * the home screen's task, brought back, could come over it (API 28).
     */
    private fun openOver(home: Intent?, window: Intent) {
        window.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (home != null) startActivities(arrayOf(home, window)) else startActivity(window)
    }

    /**
     * Whether one of Luncher's tasks has the window on top. Up to API 22 a task can't tell its top
     * activity, nor does Android close Luncher's task as on API 29: the open session says.
     */
    private fun windowShows(): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        val tasks = getSystemService(ActivityManager::class.java)?.appTasks.orEmpty()
        return tasks.any { task ->
            try {
                task.taskInfo.topActivity?.className == PictureReceivedActivity::class.java.name
            } catch (e: IllegalArgumentException) {
                false   // the task went meanwhile
            }
        }
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
