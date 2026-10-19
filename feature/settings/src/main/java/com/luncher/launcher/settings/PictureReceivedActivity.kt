package com.luncher.launcher.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PictureUse
import com.luncher.domain.pictures.ReceivedPictures.Outcome
import com.luncher.domain.pictures.pictureUseMenu
import com.luncher.launcher.ui.ShortestShowing

/**
 * The window over the home screen after another app shared pictures ([SharePictureActivity]
 * opens the home screen, then this): a floating panel like the settings panel, which shows the
 * process's [ReceivedPictures][com.luncher.domain.pictures.ReceivedPictures] session, the
 * pictures added one at a time. While they're added, "Adding the picture…", or "Adding picture 2
 * of 5…", and Cancel, focused; after a spinning wheel once a picture is being copied, the real
 * work, which then shows half a second at least ([ShortestShowing]), so it never flashes. A
 * picture refused for what its app says of its name or size shows no wheel: why is said at once.
 * Then, for one picture, "Picture added",
 * its name, and an entry for each use of [pictureUseMenu] (Background opens the background editor
 * with it chosen, not saved); or, with OK, that Luncher's pictures had it already, and its name,
 * or why it wasn't added. For several, how many were added, then how many weren't for each
 * reason ([notAddedLines]), and OK. A share of more pictures than Luncher takes at a time adds
 * none, and the window says so, at once or at the end of the others. Every [ImportResult] is
 * handled ([whyNotAdded]); an app that stops sending a picture ends as Unreadable, after the
 * library's time limit.
 *
 * Cancel, or Back, while they're added, stops the adding: once a picture was saved, the window
 * then says how it went, with how many were cancelled ([ReceivedPictures.stop][com.luncher.domain.pictures.ReceivedPictures.stop]),
 * so the user knows what was kept; before that, it closes.
 *
 * A share that arrives while the window shows comes to it ([onNewIntent]: it's `singleTop`, and
 * [SharePictureActivity] sends only it while it shows on top of one of Luncher's tasks), and its
 * pictures join the session, after the others.
 *
 * Closing the window ends the session ([finish]: OK, Cancel, Back, or an entry), the pictures
 * still to add cancelled, those added staying in the library; so does Android closing it
 * ([onDestroy]: Home, while Luncher is the home app, as the home screen clears its task), so no
 * picture is added for a window that's gone, nor read once its access, given to the window, has
 * gone with it. So does Home where Android leaves the window alive behind the home screen (on API
 * 24 to 27 while Luncher is the home app, where the window has a task of its own, and whenever
 * another app is the home app): the window closes once it's out of sight after the user chose
 * to leave it ([onUserLeaveHint], [onStop]). Out of sight for another reason, as when the TV
 * sleeps, it goes on. A configuration change doesn't end it: the window made anew finds the
 * session where it was. Only the window that took the session last ends it ([hold]): on API 29,
 * Android closes Luncher's task when another app's opens, and a share arriving then opens a new
 * window over the home screen, which takes the session over ([EXTRA_TAKE_OVER]), while the old
 * one is destroyed only later.
 */
class PictureReceivedActivity : Activity() {

    private lateinit var title: TextView
    private lateinit var text: TextView
    private lateinit var spinner: View
    private lateinit var entries: LinearLayout

    private val received get() = graph.receivedPictures

    /**
     * This window's hold on the session it shows ([ReceivedPictures.receive][com.luncher.domain.pictures.ReceivedPictures.receive]),
     * or 0 when it shows none: closed, or nothing to read. Only the window that took the session
     * last holds it, so an older window Android destroys late can't end it.
     */
    private var hold = 0

    /** Whether the share had no picture Luncher could read, which the window says, with no hold. */
    private var unreadable = false

    /** Whether the user chose to leave the window ([onUserLeaveHint]) since it was last in front: out of sight then, it closes. */
    private var leaving = false

    /** Whether the entries show Cancel, while the pictures are added: only the title changes then. */
    private var cancelShown = false

    /** The spinning wheel's wait: the end shows only once it has shown long enough. */
    private val wheel = ShortestShowing()
    private val main = Handler(Looper.getMainLooper())
    private val showLater = Runnable { show() }

    private val changed: () -> Unit = { show() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_received_activity)
        title = findViewById(R.id.settings_received_title)
        text = findViewById(R.id.settings_received_text)
        spinner = findViewById(R.id.settings_received_spinner)
        entries = findViewById(R.id.settings_received_entries)
        val kept = savedInstanceState?.getInt(STATE_HOLD) ?: 0
        // Made anew after a configuration change, the window still holds the session, where it
        // was; after the process was gone, it's gone too, and the share is taken again.
        hold = if (kept != 0 && kept == received.holder) kept else take(intent)
        unreadable = hold == 0
        received.addListener(changed)
        // Android 16 (API 36) on, for apps targeting it, sends Back here, not to onBackPressed.
        if (Build.VERSION.SDK_INT >= 36) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { back() }
        }
        show()
    }

    // Back on Android 15 (API 35) and older; onCreate handles Android 16, which lint doesn't see.
    // Deprecated, but its replacements need AndroidX (OnBackPressedCallback) or API 33
    // (OnBackInvokedCallback). Doesn't call super: back() closes the window when it should.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = back()

    /** Back: as Cancel while the pictures are added; else it closes the window. */
    private fun back() {
        if (cancelShown) cancel() else finish()
    }

    /** Stops adding: says how it went once a picture was saved, so the user knows what was kept; else closes the window. */
    private fun cancel() {
        if (hold != 0 && hold == received.holder && received.added > 0) received.stop() else finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val joined = take(intent)
        if (joined != 0) {
            hold = joined
            unreadable = false
        }
        show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_HOLD, hold)
    }

    override fun onResume() {
        super.onResume()
        leaving = false
    }

    // Home, or another screen the user opened. Android says so too as a share arrives over the
    // window, which stays in sight (the share screen shows nothing) and gets it: only once out of
    // sight, in onStop, has the user left it.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        leaving = true
    }

    override fun onStop() {
        super.onStop()
        if (leaving && !isFinishing && !isChangingConfigurations) finish()
    }

    override fun finish() {
        close()
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacks(showLater)
        received.removeListener(changed)
        if (!isChangingConfigurations) close()
    }

    /** Ends the session this window shows, if it still holds it. */
    private fun close() {
        received.end(hold)
        hold = 0
    }

    /** The pictures [intent] brings, into the session; the window's hold on it, or 0 if it brings none Luncher could read. */
    private fun take(intent: Intent): Int {
        // In place of a window that's gone: the session is this one's now, whatever the share brings.
        val over = if (intent.getBooleanExtra(EXTRA_TAKE_OVER, false)) received.hold() else 0
        if (intent.getBooleanExtra(EXTRA_NOTHING_READABLE, false)) return over
        if (intent.getBooleanExtra(EXTRA_TOO_MANY, false)) return received.refuse()
        val pictures = pictures(intent)
        return if (pictures.isEmpty()) over else received.receive(pictures)
    }

    private fun pictures(intent: Intent): List<Uri> =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(EXTRA_PICTURES, Uri::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(EXTRA_PICTURES).orEmpty()
        }

    /** The session as it stands, or why there's none. */
    private fun show() {
        main.removeCallbacks(showLater)
        if (unreadable) return showOne(ImportResult.Unreadable)
        if (hold == 0 || hold != received.holder) return
        val outcome = received.outcome ?: return showAdding()
        // The wheel, once it showed, stays half a second at least.
        val left = wheel.left
        if (left > 0) {
            main.postDelayed(showLater, left)
            return
        }
        when (outcome) {
            is Outcome.One -> showOne(outcome.result)
            is Outcome.Several -> showSeveral(outcome)
        }
    }

    private fun showAdding() {
        val total = received.total
        title.text = if (total == 1) getString(R.string.settings_received_adding) else getString(R.string.settings_received_adding_count, received.answered + 1, total)
        // From the first picture copied on, until the end: not on and off from one picture to the next.
        if (received.copying && !wheel.isShowing) {
            spinner.visibility = View.VISIBLE
            wheel.start()
        }
        if (cancelShown) return
        cancelShown = true
        if (!wheel.isShowing) spinner.visibility = View.GONE
        text.visibility = View.GONE
        entries.removeAllViews()
        entry(getString(R.string.settings_pictures_cancel)) { cancel() }
        focusFirst()
    }

    private fun showOne(result: ImportResult) {
        showDone()
        if (result !is ImportResult.Added) {
            title.text = notAddedTitle(result)
            text.text = whyNotAdded(result)
            entry(getString(R.string.settings_pictures_ok)) { finish() }
            return focusFirst()
        }
        val picture = result.picture
        title.setText(R.string.settings_received_saved)
        text.text = nameOf(picture)
        for (use in pictureUseMenu) entry(getString(label(use))) { use(picture, use) }
        focusFirst()
    }

    private fun showSeveral(outcome: Outcome.Several) {
        showDone()
        title.text = if (outcome.added > 0) {
            resources.getQuantityString(R.plurals.settings_received_saved_count, outcome.added, outcome.added)
        } else {
            getString(R.string.settings_received_none_added)
        }
        val lines = notAddedLines(outcome)
        text.text = lines.joinToString("\n")
        text.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
        entry(getString(R.string.settings_pictures_ok)) { finish() }
        focusFirst()
    }

    /** The wheel stops and the entries go: what's done is said. */
    private fun showDone() {
        cancelShown = false
        wheel.stop()
        spinner.visibility = View.GONE
        text.visibility = View.VISIBLE
        entries.removeAllViews()
    }

    private fun label(use: PictureUse): Int = when (use) {
        PictureUse.Wallpaper -> R.string.settings_appearance_background
    }

    /** Opens the screen where [picture] is put to [use], chosen there, and closes this window: Back there returns to the home screen. */
    private fun use(picture: Picture, use: PictureUse) {
        when (use) {
            PictureUse.Wallpaper -> startActivity(Intent(this, BackgroundActivity::class.java).putExtra(BackgroundActivity.EXTRA_PICTURE, picture.id.value))
        }
        finish()
    }

    private fun entry(label: String, ok: () -> Unit) {
        val view = layoutInflater.inflate(R.layout.settings_entry, entries, false) as TextView
        view.text = label
        view.setOnClickListener { ok() }
        entries.addView(view)
    }

    private fun focusFirst() {
        entries.getChildAt(0)?.requestFocus()
    }

    internal companion object {
        /** The shared pictures' Uris, in order ([SharePictureActivity]); the access to read them comes with the intent's clip. */
        const val EXTRA_PICTURES = "com.luncher.launcher.settings.PICTURES"

        /** Set by [SharePictureActivity] when a share had more pictures than [PictureLimits.MAX_SHARED]: none is passed. */
        const val EXTRA_TOO_MANY = "com.luncher.launcher.settings.TOO_MANY"

        /** Set by [SharePictureActivity] when what another app sent had no picture it could pass on. */
        const val EXTRA_NOTHING_READABLE = "com.luncher.launcher.settings.NOTHING_READABLE"

        /** Set by [SharePictureActivity] when it opened this window in place of one that's gone, for which it took the session over: the window takes it. */
        const val EXTRA_TAKE_OVER = "com.luncher.launcher.settings.TAKE_OVER"

        private const val STATE_HOLD = "hold"
    }
}
