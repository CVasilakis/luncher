package com.luncher.launcher.settings

import android.app.ActivityManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAppTask
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TV_1080P)
class SharePictureActivityTest {

    private val context = RuntimeEnvironment.getApplication()
    private val picture = Uri.parse("content://com.example.files/sunset.jpg")

    /**
     * Sends [intent], as another app would: after finishing at once, it has opened Luncher's home
     * screen, in a task of its own; what it opened over it, the window, is returned.
     */
    private fun send(intent: Intent): Intent {
        val started = started(intent)
        assertEquals("the home screen and the window", 2, started.size)
        val home = started.single { it.component?.className == HOME }
        assertEquals("Luncher's own, whether or not it's the device's home app", context.packageName, home.component?.packageName)
        assertTrue(home.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        return started.single { it !== home }
    }

    /** What sending [intent] started, after finishing at once. */
    private fun started(intent: Intent): List<Intent> {
        val activity = Robolectric.buildActivity(SharePictureActivity::class.java, intent).create().get()
        assertTrue("finished before it's shown", activity.isFinishing)
        return generateSequence { shadowOf(activity).nextStartedActivity }.toList()
    }

    /** Luncher's entry in the TV's app list, which the app's manifest declares on the home screen. */
    @Before
    fun listLuncherAsATvApp() {
        val packages = shadowOf(context.packageManager)
        packages.addActivityIfNotPresent(home)
        packages.addIntentFilterForActivity(home, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) })
    }

    private val home get() = ComponentName(context.packageName, HOME)

    /** A window that shows, on top of one of Luncher's tasks. */
    private fun windowShows() {
        val task = ShadowAppTask.newInstance()
        shadowOf(task).setTaskInfo(ActivityManager.RecentTaskInfo().apply { topActivity = ComponentName(context, PictureReceivedActivity::class.java) })
        shadowOf(context.getSystemService(ActivityManager::class.java)).setAppTasks(listOf(task))
    }

    private fun assertPassesThePicture(started: Intent) {
        assertEquals(PictureReceivedActivity::class.java.name, started.component?.className)
        assertEquals(listOf(picture), pictures(started))
    }

    @Suppress("DEPRECATION")
    private fun pictures(started: Intent): List<Uri>? = started.getParcelableArrayListExtra(PictureReceivedActivity.EXTRA_PICTURES)

    @Test
    fun `opens the home screen, and over it the window with the picture and the access to read it`() {
        assertPassesThePicture(send(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, picture)))
    }

    @Test
    fun `takes the picture from the clip when the stream extra isn't there`() {
        val intent = Intent(Intent.ACTION_SEND).setType("image/jpeg")
        intent.clipData = ClipData.newRawUri("", picture)

        assertPassesThePicture(send(intent))
    }

    @Test
    fun `passes a picture to Set as`() {
        assertPassesThePicture(send(Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(picture, "image/jpeg")))
    }

    @Test
    fun `with no picture to pass, has the window say the app shared none`() {
        for (intent in listOf(Intent(Intent.ACTION_SEND).setType("image/jpeg"), Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, "not a Uri"), Intent("com.example.OTHER"))) {
            val started = send(intent)

            assertEquals(PictureReceivedActivity::class.java.name, started.component?.className)
            assertNull(pictures(started))
            assertTrue(started.getBooleanExtra(PictureReceivedActivity.EXTRA_NOTHING_SHARED, false))
            assertFalse(started.getBooleanExtra(PictureReceivedActivity.EXTRA_NOTHING_READABLE, false))
            assertFalse(started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        }
    }

    @Test
    fun `without an entry in the TV's app list, opens the window alone`() {
        shadowOf(context.packageManager).removeActivity(home)

        val started = started(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, picture)).single()

        assertPassesThePicture(started)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    /** [component] as a home app, the only one: what Android gives for Home, as the device's default. */
    private fun homeApp(component: ComponentName) {
        val packages = shadowOf(context.packageManager)
        packages.addActivityIfNotPresent(component)
        packages.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME); addCategory(Intent.CATEGORY_DEFAULT) })
    }

    // On API 24 to 27 a home screen opened any other way is a second one, beside the home app's.
    @Test
    fun `while Luncher is the device's home app, opens the home screen as Home does, naming no component`() {
        homeApp(home)

        val started = started(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, picture))

        assertEquals("the home screen and the window", 2, started.size)
        val screen = started.single { it.component == null }
        assertEquals(Intent.ACTION_MAIN, screen.action)
        assertEquals(setOf(Intent.CATEGORY_HOME), screen.categories)
        assertEquals(context.packageName, screen.`package`)
        assertTrue(screen.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertPassesThePicture(started.single { it !== screen })
    }

    @Test
    fun `while another app is the device's home app, opens the home screen as the TV's app list does`() {
        homeApp(ComponentName("com.example.tv", "com.example.tv.Home"))

        assertPassesThePicture(send(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, picture)))
    }

    private val more = listOf("beach.jpg", "forest.jpg").map { Uri.parse("content://com.example.files/$it") }

    @Test
    fun `passes every picture of a share of several, in order, with the access to each`() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(picture, *more.toTypedArray()))

        val started = send(intent)

        assertEquals(listOf(picture) + more, pictures(started))
        assertEquals(3, started.clipData?.itemCount)
        assertTrue(started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `takes a share of several from its clip when the stream extra isn't there`() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*")
        intent.clipData = ClipData.newRawUri("", picture).apply { more.forEach { addItem(ClipData.Item(it)) } }

        assertEquals(listOf(picture) + more, pictures(send(intent)))
    }

    @Test
    fun `takes every picture of a Share's clip, as an app may send several that way`() {
        val intent = Intent(Intent.ACTION_SEND).setType("image/*")
        intent.clipData = ClipData.newRawUri("", picture).apply { more.forEach { addItem(ClipData.Item(it)) } }

        assertEquals(listOf(picture) + more, pictures(send(intent)))
    }

    @Test
    fun `passes a picture shared twice once`() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(picture, more[0], picture))

        assertEquals(listOf(picture, more[0]), pictures(send(intent)))
    }

    @Test
    fun `passes a share of a hundred pictures`() {
        val hundred = (1..100).map { Uri.parse("content://com.example.files/$it.jpg") }

        assertEquals(hundred, pictures(send(Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(hundred)))))
    }

    @Test
    fun `passes none of a share of more than a hundred pictures, and has the window say so`() {
        val more = (1..101).map { Uri.parse("content://com.example.files/$it.jpg") }

        val started = send(Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(more)))

        assertTrue(started.getBooleanExtra(PictureReceivedActivity.EXTRA_TOO_MANY, false))
        assertNull(pictures(started))
        assertNull(started.clipData)
    }

    @Test
    fun `with a single Uri where a share of several has a list, takes the clip`() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*").putExtra(Intent.EXTRA_STREAM, picture)
        intent.clipData = ClipData.newRawUri("", picture)

        assertEquals(listOf(picture), pictures(send(intent)))
    }

    // Robolectric's activity manager says every Uri is granted, so a picture Luncher was given no
    // access to is checked on a device (PictureShareTest).
    @Test
    fun `passes on the read access to a picture Luncher was given access to, in the clip`() {
        context.grantUriPermission(context.packageName, picture, Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val started = send(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, picture))

        assertEquals(picture, started.clipData?.getItemAt(0)?.uri)
        assertTrue(started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `while a window adds pictures, sends the window only, so the share joins them rather than closing it`() {
        val graph = TestPicturesGraph().also { (context as SettingsTestApplication).graph = it }
        graph.library.answerLater = true
        graph.receivedPictures.receive(listOf(Uri.parse("content://com.example.files/first.jpg")))
        windowShows()

        val started = started(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, picture)).single()

        assertPassesThePicture(started)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `while a window adds pictures, passes on the access to those still to add too, for a new window taking over`() {
        val first = Uri.parse("content://com.example.files/first.jpg")
        val graph = TestPicturesGraph().also { (context as SettingsTestApplication).graph = it }
        graph.library.answerLater = true
        graph.receivedPictures.receive(listOf(first))
        windowShows()

        val started = started(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, picture)).single()

        assertEquals(listOf(picture), pictures(started))
        assertEquals(listOf(first, picture), (0 until started.clipData!!.itemCount).map { started.clipData!!.getItemAt(it).uri })
        assertTrue(started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    // On API 29 Android closes Luncher's task, the window with it, when another app's opens; the
    // window is destroyed, and its session ended, only later.
    @Test
    fun `with a session open but no window showing, opens the home screen and a window that takes the session over`() {
        val first = Uri.parse("content://com.example.files/first.jpg")
        val graph = TestPicturesGraph().also { (context as SettingsTestApplication).graph = it }
        graph.library.answerLater = true
        val gone = graph.receivedPictures.receive(listOf(first))

        val window = send(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, picture))

        assertTrue(window.getBooleanExtra(PictureReceivedActivity.EXTRA_TAKE_OVER, false))
        assertEquals(listOf(first, picture), (0 until window.clipData!!.itemCount).map { window.clipData!!.getItemAt(it).uri })
        graph.receivedPictures.end(gone)
        assertEquals(1, graph.receivedPictures.total)
    }

    private companion object {
        const val HOME = "com.luncher.launcher.home.HomeActivity"
    }
}
