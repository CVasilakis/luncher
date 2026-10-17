package com.luncher.launcher.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PictureUse
import com.luncher.domain.pictures.pictureUseMenu

/**
 * The window over the home screen after another app shared a picture ([SharePictureActivity]
 * opens the home screen, then this): a floating panel like the settings panel. While the picture
 * is added to the library, "Adding the picture…" and a spinning wheel; then "Picture saved", its
 * name, and an entry for each use of [pictureUseMenu] (Background opens the background editor
 * with it chosen, not saved); or why it wasn't added, and OK. Back closes it: the home screen, the
 * picture in the library. Every [ImportResult] is handled ([whyNotAdded]).
 *
 * A picture added twice (the window recreated, the same picture shared again) is the same one:
 * the library keeps one copy of the same content.
 */
class PictureReceivedActivity : Activity() {

    private lateinit var title: TextView
    private lateinit var text: TextView
    private lateinit var spinner: View
    private lateinit var entries: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_received_activity)
        title = findViewById(R.id.settings_received_title)
        text = findViewById(R.id.settings_received_text)
        spinner = findViewById(R.id.settings_received_spinner)
        entries = findViewById(R.id.settings_received_entries)
        val picture = intent.data
        if (picture == null || intent.getBooleanExtra(EXTRA_NOTHING_READABLE, false)) show(ImportResult.Unreadable) else add(picture)
    }

    private fun add(source: Uri) {
        title.setText(R.string.settings_received_adding)
        text.text = ""
        spinner.visibility = View.VISIBLE
        graph.pictureImport.add(source) { result -> if (!isDestroyed) show(result) }
    }

    private fun show(result: ImportResult) {
        spinner.visibility = View.GONE
        entries.removeAllViews()
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
        /** Set by [SharePictureActivity] when what another app sent had no picture it could pass on. */
        const val EXTRA_NOTHING_READABLE = "com.luncher.launcher.settings.NOTHING_READABLE"
    }
}
