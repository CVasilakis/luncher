package com.luncher.launcher.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PictureLimits
import com.luncher.domain.pictures.PictureUse
import com.luncher.domain.pictures.ReceivedPictures.Outcome
import com.luncher.domain.pictures.pictureUseMenu

/**
 * The window over the home screen after another app shared pictures ([SharePictureActivity]
 * opens the home screen, then this): a floating panel like the settings panel, which shows the
 * process's [ReceivedPictures][com.luncher.domain.pictures.ReceivedPictures] session, the
 * pictures added one at a time. While they're added, "Adding the picture…", or "Adding picture 2
 * of 5…", after a spinning wheel, and Cancel, focused. Then, for one picture, "Picture saved",
 * its name, and an entry for each use of [pictureUseMenu] (Background opens the background editor
 * with it chosen, not saved), or why it wasn't added, and OK; for several, how many were saved
 * and how many weren't, and OK. A share of more pictures than Luncher takes at a time adds none,
 * and the window says so, at once or at the end of the others. Every [ImportResult] is handled ([whyNotAdded]); an app that stops
 * sending a picture ends as Unreadable, after the library's time limit.
 *
 * A share that arrives while the window shows comes to it ([onNewIntent]: it's `singleTop`, and
 * [SharePictureActivity] sends only it while a session is open), and its pictures join the
 * session, after the others.
 *
 * Closing the window ends the session ([finish]: OK, Cancel, Back, or an entry), the pictures
 * still to add cancelled, those added staying in the library; so does Android closing it
 * ([onDestroy]: Home, while Luncher is the home app, as the home screen clears its task), so no
 * picture is added for a window that's gone, nor read once its access, given to the window, has
 * gone with it. A configuration change doesn't: the window made anew finds the session where it
 * was.
 */
class PictureReceivedActivity : Activity() {

    private lateinit var title: TextView
    private lateinit var text: TextView
    private lateinit var spinner: View
    private lateinit var entries: LinearLayout

    private val received get() = graph.receivedPictures

    /** The session this window shows, or 0 when it shows none: closed, or nothing to read. */
    private var session = 0

    /** Whether the share had no picture Luncher could read, which the window says, with no session. */
    private var unreadable = false

    /** Whether the entries show Cancel, while the pictures are added: only the title changes then. */
    private var cancelShown = false

    private val changed: () -> Unit = { show() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_received_activity)
        title = findViewById(R.id.settings_received_title)
        text = findViewById(R.id.settings_received_text)
        spinner = findViewById(R.id.settings_received_spinner)
        entries = findViewById(R.id.settings_received_entries)
        val kept = savedInstanceState?.getInt(STATE_SESSION) ?: 0
        // Made anew after a configuration change, the session is where it was; after the process
        // was gone, it's gone too, and the share is taken again.
        session = if (kept != 0 && kept == received.session) kept else take(intent)
        unreadable = session == 0
        received.addListener(changed)
        show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val joined = take(intent)
        if (session == 0 && joined != 0) {
            session = joined
            unreadable = false
        }
        show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SESSION, session)
    }

    override fun finish() {
        close()
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        received.removeListener(changed)
        if (!isChangingConfigurations) close()
    }

    /** Ends the session this window shows, if it's still the one open. */
    private fun close() {
        received.end(session)
        session = 0
    }

    /** The pictures [intent] brings, into the session; 0 if it brings none Luncher could read. */
    private fun take(intent: Intent): Int {
        if (intent.getBooleanExtra(EXTRA_NOTHING_READABLE, false)) return 0
        if (intent.getBooleanExtra(EXTRA_TOO_MANY, false)) return received.refuse()
        val pictures = pictures(intent)
        return if (pictures.isEmpty()) 0 else received.receive(pictures)
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
        if (unreadable) return showOne(ImportResult.Unreadable)
        if (session == 0 || session != received.session) return
        when (val outcome = received.outcome) {
            null -> showAdding()
            is Outcome.One -> showOne(outcome.result)
            is Outcome.Several -> showSeveral(outcome)
        }
    }

    private fun showAdding() {
        val total = received.total
        title.text = if (total == 1) getString(R.string.settings_received_adding) else getString(R.string.settings_received_adding_count, received.answered + 1, total)
        if (cancelShown) return
        cancelShown = true
        spinner.visibility = View.VISIBLE
        text.visibility = View.GONE
        entries.removeAllViews()
        entry(getString(R.string.settings_pictures_cancel)) { finish() }
        focusFirst()
    }

    private fun showOne(result: ImportResult) {
        showDone()
        val picture = when (result) {
            is ImportResult.Added -> result.picture
            is ImportResult.AlreadyThere -> result.picture
            else -> {
                title.setText(R.string.settings_pictures_not_added)
                text.text = whyNotAdded(result)
                entry(getString(R.string.settings_pictures_ok)) { finish() }
                return focusFirst()
            }
        }
        title.setText(R.string.settings_received_saved)
        text.text = nameOf(picture)
        for (use in pictureUseMenu) entry(getString(label(use))) { use(picture, use) }
        focusFirst()
    }

    private fun showSeveral(outcome: Outcome.Several) {
        showDone()
        title.text = if (outcome.saved > 0) {
            resources.getQuantityString(R.plurals.settings_received_saved_count, outcome.saved, outcome.saved)
        } else {
            getString(R.string.settings_received_none_added)
        }
        val lines = listOfNotNull(
            if (outcome.saved > 0 && outcome.notAdded > 0) resources.getQuantityString(R.plurals.settings_received_not_added_count, outcome.notAdded, outcome.notAdded) else null,
            if (outcome.tooMany) resources.getQuantityString(R.plurals.settings_received_too_many, PictureLimits.MAX_SHARED, PictureLimits.MAX_SHARED) else null,
        )
        text.text = lines.joinToString("\n")
        text.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
        entry(getString(R.string.settings_pictures_ok)) { finish() }
        focusFirst()
    }

    /** The wheel stops and the entries go: what's done is said. */
    private fun showDone() {
        cancelShown = false
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

        private const val STATE_SESSION = "session"
    }
}
