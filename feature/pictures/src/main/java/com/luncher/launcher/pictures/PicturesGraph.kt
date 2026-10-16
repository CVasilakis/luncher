package com.luncher.launcher.pictures

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import com.luncher.domain.appearance.AppearanceSettings
import com.luncher.domain.jobs.Jobs
import com.luncher.domain.pictures.FolderPictures
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.pictures.PictureImport
import com.luncher.domain.pictures.Pictures

/**
 * The ports the pictures' screens need. The app's composition root implements it with the
 * adapters, and tests with fakes; this module sees the ports only, never an adapter.
 */
interface PicturesGraph {
    val pictures: Pictures
    val pictureImport: PictureImport<Uri>
    val folderPictures: FolderPictures<Uri, Bitmap>
    val pictureImages: PictureImages<Bitmap>

    /** The wallpaper, which a picture of the library is made or stops being. */
    val appearanceSettings: AppearanceSettings

    /** The background thread, for what the screens load (thumbnails, previews). */
    val jobs: Jobs

    /** Implemented by the Application, which holds the process's graph. */
    interface Owner {
        val picturesGraph: PicturesGraph
    }
}

/** The process's [PicturesGraph], for the pictures' screens: `val pictures = graph.pictures`. */
internal val Activity.graph: PicturesGraph
    get() = (application as PicturesGraph.Owner).picturesGraph
