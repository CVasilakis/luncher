package com.luncher.launcher.settings

import android.content.Context
import android.text.format.Formatter
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PictureFormat
import com.luncher.domain.pictures.PictureLimits
import com.luncher.domain.pictures.PictureUse

// What the pictures' screens call things, in the user's language.

/** [bytes] as the language writes a file's size, e.g. "3.1 MB". */
internal fun Context.size(bytes: Long): String = Formatter.formatShortFileSize(this, bytes)

/** [picture]'s name, or "Picture" for one that came without one. */
internal fun Context.nameOf(picture: Picture): String = picture.name.ifEmpty { getString(R.string.settings_pictures_unnamed) }

/** A picture's width and height, and its file's size. */
internal fun Context.details(width: Int, height: Int, bytes: Long): String =
    getString(R.string.settings_pictures_details, width, height, size(bytes))

/** The drop folders' path, as a file manager shows it: `Android/media/<this app's ID>`. */
internal fun Context.dropFolder(): String = getString(R.string.settings_pictures_folder_path, packageName)

internal fun Context.formatName(format: PictureFormat): String = when (format) {
    PictureFormat.HEIF -> getString(R.string.settings_pictures_format_heif)
    PictureFormat.AVIF -> getString(R.string.settings_pictures_format_avif)
}

/** Why a picture wasn't added, for a message; null when it was, or was there already. */
internal fun Context.whyNotAdded(result: ImportResult): String? = when (result) {
    is ImportResult.Added, is ImportResult.AlreadyThere -> null
    ImportResult.NotAPicture -> getString(R.string.settings_pictures_not_a_picture)
    is ImportResult.UnsupportedFormat -> getString(R.string.settings_pictures_unsupported, formatName(result.format))
    ImportResult.TooLarge -> (PictureLimits.MAX_PIXELS / 1_000_000).toInt().let { megapixels ->
        resources.getQuantityString(R.plurals.settings_pictures_too_large, megapixels, size(PictureLimits.MAX_BYTES), megapixels)
    }
    ImportResult.Unreadable -> getString(R.string.settings_pictures_unreadable, dropFolder())
    ImportResult.NoSpace -> getString(R.string.settings_pictures_no_space)
}

/** What a picture is used for, as its tile says it: the uses, or that it has none. */
internal fun Context.useOf(uses: List<PictureUse>): String =
    if (uses.isEmpty()) getString(R.string.settings_pictures_not_in_use) else getString(R.string.settings_pictures_in_use, uses.joinToString { nameOf(it) })

/** A use's name. */
internal fun Context.nameOf(use: PictureUse): String = when (use) {
    PictureUse.Wallpaper -> getString(R.string.settings_pictures_wallpaper)
}

/** What deleting a picture with [use] does to it, said before deleting. */
internal fun Context.afterDeleting(use: PictureUse): String = when (use) {
    PictureUse.Wallpaper -> getString(R.string.settings_pictures_delete_wallpaper)
}

/** The title of Pictures opened to choose a picture for [use]. */
internal fun Context.chooseTitle(use: PictureUse): String = when (use) {
    PictureUse.Wallpaper -> getString(R.string.settings_pictures_choose_wallpaper)
}
