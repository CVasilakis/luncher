package com.luncher.launcher.pictures

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle

/**
 * Where another app's Share (`SEND`) or "Set as" (`ATTACH_DATA`) of a picture arrives, with no
 * screen of its own: it passes the picture's Uri, and the read access the other app gave with it,
 * to [PicturesActivity], which adds it and shows the result, and finishes before it's ever shown.
 *
 * Exported, so any app can send it anything: it reads nothing itself, only takes the Uri out of
 * the intent, and anything it can't take one from (extras that don't unparcel, no Uri at all)
 * becomes a message in Pictures, never a crash. What the Uri points to is the library's to judge
 * ([PictureImport][com.luncher.domain.pictures.PictureImport]).
 */
class SharePictureActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val open = Intent(this, PicturesActivity::class.java)
        val picture = sharedPicture(intent)
        if (picture != null) {
            open.setData(picture).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            open.putExtra(PicturesActivity.EXTRA_NOTHING_READABLE, true)
        }
        startActivity(open)
        finish()   // before onResume, as its theme (windowNoDisplay) requires
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
