package com.luncher.launcher.settings

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import com.luncher.domain.appearance.AppearanceSettings
import com.luncher.domain.apps.AppArrangements
import com.luncher.domain.apps.InstalledApps
import com.luncher.domain.jobs.Jobs
import com.luncher.domain.pictures.FolderPictures
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.pictures.PreparedPictures
import com.luncher.domain.pictures.PictureImport
import com.luncher.domain.pictures.Pictures
import com.luncher.domain.pictures.ReceivedPictures
import com.luncher.domain.wallpaper.Wallpaper

/**
 * The ports the settings screens need. The app's composition root implements it with the
 * adapters, and tests with fakes; this module sees the ports only, never an adapter.
 */
interface SettingsGraph {
    val installedApps: InstalledApps
    val appArrangements: AppArrangements

    /** The home screen's appearance; also the wallpaper, which a picture of the library is made or stops being. */
    val appearanceSettings: AppearanceSettings

    /** The picture library: its screens, and the wallpaper's name in the appearance editor. */
    val pictures: Pictures

    /** Adding a picture to the library, from a share or a drop folder. */
    val pictureImport: PictureImport<Uri>

    /** The pictures other apps shared, being added: one per process, so a window recreated finds them where they were. */
    val receivedPictures: ReceivedPictures<Uri>

    /** The drop folders' pictures, for Add from folder. */
    val folderPictures: FolderPictures<Uri, Bitmap>

    /** The pictures as the editors' previews show them, drawn at each change from what's prepared. */
    val pictureImages: PictureImages<Bitmap>

    /** The background editor's thumbnails, kept to show at once. */
    val thumbnails: PictureImages<Bitmap>

    /** Each picture prepared once, so the two above draw it in milliseconds; prepared anew at the editor's start when it isn't. */
    val preparedPictures: PreparedPictures

    /** The background thread, for what the screens load (thumbnails, previews). */
    val jobs: Jobs

    /** The home screen's wallpaper, which the background editor saves through: drawn, then stored. */
    val wallpaper: Wallpaper<Bitmap>

    /** Implemented by the Application, which holds the process's graph. */
    interface Owner {
        val settingsGraph: SettingsGraph
    }
}

/** The process's [SettingsGraph], for the settings screens: `val apps = graph.installedApps`. */
internal val Activity.graph: SettingsGraph
    get() = (application as SettingsGraph.Owner).settingsGraph
