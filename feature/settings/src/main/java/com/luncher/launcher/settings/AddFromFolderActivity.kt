package com.luncher.launcher.settings

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.LatestRequest
import com.luncher.domain.pictures.FolderPicture
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.inListOrder
import com.luncher.domain.pictures.whyRefused
import java.util.Locale

/**
 * The pictures in Luncher's drop folders, on the TV and on USB drives ([FolderPictures][graph]):
 * a list, in the order of [inListOrder], with a preview of the selected one beside it. OK on one
 * returns it to [BackgroundActivity], which adds it. One that its listing and the library already
 * tell can't be added ([whyRefused]: a format this TV can't show, too large, a name the library
 * has) is marked in the list, has no preview, and OK on it says why at once. The folders are
 * read again each time it shows, so a drive plugged in meanwhile counts.
 */
class AddFromFolderActivity : Activity() {

    private val folders by lazy { graph.folderPictures }
    private val library by lazy { graph.pictures }
    private val main = Handler(Looper.getMainLooper())
    private val previewing by lazy { LatestRequest(graph.jobs, JobPriority.PICTURE_SCREENS) { main.post(it) } }
    private lateinit var list: ListView
    private lateinit var state: TextView
    private lateinit var side: PreviewColumn
    private lateinit var preview: ImageView
    private var pictures: List<FolderPicture<Uri>> = emptyList()

    /** Why each of [pictures] can't be added, by its source, as far as the listing and the library tell. */
    private var refused: Map<Uri, ImportResult> = emptyMap()
    private val rows = Rows()

    /** Counts the listings: one that answers after [onStop] or a newer one is dropped. */
    private var listing = 0
    private var dialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_pictures_folder_activity)
        list = findViewById(R.id.settings_pictures_folder_list)
        state = findViewById(R.id.settings_pictures_folder_state)
        side = findViewById(R.id.settings_pictures_folder_side)
        preview = findViewById(R.id.settings_pictures_folder_preview)
        findViewById<TextView>(R.id.settings_pictures_folder_where).text = getString(R.string.settings_pictures_folder_where, dropFolder())
        list.adapter = rows
        list.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = showPreview(pictures[position])
            override fun onNothingSelected(parent: AdapterView<*>?) = showPreview(null)
        }
        list.setOnItemClickListener { _, _, position, _ -> choose(pictures[position]) }
        side.onPreviewSized = { showPreview(list.selectedItem as? FolderPicture<*>) }
        show(emptyList(), looking = true)
    }

    override fun onStart() {
        super.onStart()
        val request = ++listing
        folders.list { found ->
            if (request == listing) show(inListOrder(found, Locale.getDefault()), looking = false)
        }
    }

    override fun onStop() {
        super.onStop()
        listing++
        previewing.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        dialog?.dismiss()
    }

    private fun show(found: List<FolderPicture<Uri>>, looking: Boolean) {
        val selected = (list.selectedItem as? FolderPicture<*>)?.source
        pictures = found
        val known = library.read()
        refused = found.mapNotNull { picture -> whyRefused(picture, known)?.let { picture.source to it } }.toMap()
        rows.notifyDataSetChanged()
        state.visibility = if (found.isEmpty()) View.VISIBLE else View.GONE
        state.setText(if (looking) R.string.settings_pictures_folder_looking else R.string.settings_pictures_folder_empty)
        side.visibility = if (found.isEmpty()) View.INVISIBLE else View.VISIBLE
        if (found.isEmpty()) return showPreview(null)
        list.setSelection(found.indexOfFirst { it.source == selected }.coerceAtLeast(0))
        list.requestFocus()
        list.selectedItem?.let { showPreview(it as FolderPicture<*>) }
    }

    /**
     * Draws a preview of [picture] in the background, the latest selected one only: the decode of
     * one no longer selected stops. None of a picture this TV can't show, or too large to add.
     */
    private fun showPreview(picture: FolderPicture<*>?) {
        preview.setImageDrawable(null)
        val shown = pictures.firstOrNull { it.source == picture?.source }
        val (width, height) = side.previewSize
        val why = shown?.let { refused[it.source] }
        if (shown == null || why is ImportResult.UnsupportedFormat || why == ImportResult.TooLarge || width <= 0 || height <= 0) {
            previewing.stop()
            return
        }
        previewing.load({ stopped -> folders.preview(shown, width, height, Framing(), stopped) }) { preview.setImageBitmap(it) }
    }

    /** Returns [picture] to be added, or says at once why it can't be. */
    private fun choose(picture: FolderPicture<Uri>) {
        val why = refused[picture.source]
        if (why == null) {
            setResult(RESULT_OK, Intent().setData(picture.source))
            finish()
            return
        }
        dialog?.dismiss()
        dialog = PicturesDialog(
            this,
            getString(R.string.settings_pictures_not_added),
            whyNotAdded(why).orEmpty(),
            listOf(getString(R.string.settings_pictures_ok) to {}),
        ).apply { show() }
    }

    /** The rows of [pictures], reusing the views of rows scrolled away. */
    private inner class Rows : BaseAdapter() {

        override fun getCount() = pictures.size

        override fun getItem(position: Int) = pictures[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.settings_pictures_folder_row, parent, false)
            val picture = pictures[position]
            val where = getString(if (picture.removable) R.string.settings_pictures_folder_usb else R.string.settings_pictures_folder_this_tv)
            view.findViewById<TextView>(R.id.settings_pictures_folder_name).text = getString(R.string.settings_pictures_folder_row, where, picture.name)
            val why = refusal(refused[picture.source])
            view.findViewById<TextView>(R.id.settings_pictures_folder_details).text = when {
                // Its size unknown: only why.
                picture.unsupported != null -> why
                why != null -> getString(R.string.settings_pictures_folder_refused, details(picture.width, picture.height, picture.bytes), why)
                else -> details(picture.width, picture.height, picture.bytes)
            }
            return view
        }
    }
}
