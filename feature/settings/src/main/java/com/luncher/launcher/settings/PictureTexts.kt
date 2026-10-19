package com.luncher.launcher.settings

import android.content.Context
import android.text.format.Formatter
import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PictureFormat
import com.luncher.domain.pictures.PictureLimits
import com.luncher.domain.pictures.PictureUse
import com.luncher.domain.pictures.ReceivedPictures.Outcome
import com.luncher.domain.pictures.SizeLimit
import com.luncher.domain.pictures.wasInLibrary

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

/** The title of the message for a picture not added: that Luncher's pictures had it already, or that it wasn't added. */
internal fun Context.notAddedTitle(result: ImportResult): String =
    getString(if (result.wasInLibrary) R.string.settings_pictures_already else R.string.settings_pictures_not_added)

/** Below [notAddedTitle]: why the picture wasn't added, or the name of the one Luncher's pictures had; null when it was added. */
internal fun Context.whyNotAdded(result: ImportResult): String? = when (result) {
    is ImportResult.Added -> null
    is ImportResult.AlreadyThere -> nameOf(result.picture)
    is ImportResult.SameFile -> nameOf(result.picture)
    is ImportResult.NameTaken -> getString(R.string.settings_pictures_name_taken, nameOf(result.picture))
    ImportResult.NotAPicture -> getString(R.string.settings_pictures_not_a_picture)
    is ImportResult.UnsupportedFormat -> getString(R.string.settings_pictures_unsupported, formatName(result.format))
    is ImportResult.TooLarge -> when (result.limit) {
        SizeLimit.BYTES -> getString(R.string.settings_pictures_too_large_bytes, size(PictureLimits.MAX_BYTES))
        SizeLimit.PIXELS -> resources.getQuantityString(R.plurals.settings_pictures_too_large_pixels, MEGAPIXELS, MEGAPIXELS)
    }
    ImportResult.Unreadable -> getString(R.string.settings_pictures_unreadable, dropFolder())
    ImportResult.NoSpace -> getString(R.string.settings_pictures_no_space)
}

/**
 * What Add from folder's row says of a picture that can't be added, as its listing tells
 * ([whyRefused][com.luncher.domain.pictures.whyRefused]); null for one that can, as far as it tells.
 */
internal fun Context.refusal(result: ImportResult?): String? = when (result) {
    is ImportResult.UnsupportedFormat -> getString(R.string.settings_pictures_folder_unsupported, formatName(result.format))
    is ImportResult.TooLarge -> when (result.limit) {
        SizeLimit.BYTES -> getString(R.string.settings_pictures_folder_too_large_bytes, size(PictureLimits.MAX_BYTES))
        SizeLimit.PIXELS -> resources.getQuantityString(R.plurals.settings_pictures_folder_too_large_pixels, MEGAPIXELS, MEGAPIXELS)
    }
    is ImportResult.SameFile -> getString(R.string.settings_pictures_already)
    is ImportResult.NameTaken -> getString(R.string.settings_pictures_folder_name_taken)
    null, is ImportResult.Added, is ImportResult.AlreadyThere, ImportResult.NotAPicture, ImportResult.Unreadable, ImportResult.NoSpace -> null
}

/**
 * The lines below the title after several pictures: how many weren't added, for each reason, in
 * this order, a reason none has left out; then those Cancel stopped, and a share of too many.
 */
internal fun Context.notAddedLines(outcome: Outcome.Several): List<String> {
    val results = outcome.results
    fun count(plural: Int, count: Int, vararg more: Any): String? =
        if (count == 0) null else resources.getQuantityString(plural, count, count, *more)
    return listOfNotNull(
        count(R.plurals.settings_received_already_count, results.count { it.wasInLibrary }),
        count(R.plurals.settings_received_not_a_picture_count, results.count { it == ImportResult.NotAPicture }),
        count(R.plurals.settings_received_unsupported_count, results.count { it is ImportResult.UnsupportedFormat }),
        count(R.plurals.settings_received_too_large_bytes_count, results.count { it == ImportResult.TooLarge(SizeLimit.BYTES) }, size(PictureLimits.MAX_BYTES)),
        count(R.plurals.settings_received_too_large_pixels_count, results.count { it == ImportResult.TooLarge(SizeLimit.PIXELS) }, MEGAPIXELS),
        count(R.plurals.settings_received_name_taken_count, results.count { it is ImportResult.NameTaken }),
        count(R.plurals.settings_received_unreadable_count, results.count { it == ImportResult.Unreadable }),
        count(R.plurals.settings_received_no_space_count, results.count { it == ImportResult.NoSpace }),
        count(R.plurals.settings_received_cancelled_count, outcome.cancelled),
        if (outcome.tooMany) resources.getQuantityString(R.plurals.settings_received_too_many, PictureLimits.MAX_SHARED, PictureLimits.MAX_SHARED) else null,
    )
}

/** The most pixels a picture can have, in millions, as the texts say it. */
private val MEGAPIXELS = (PictureLimits.MAX_PIXELS / 1_000_000).toInt()

/** What deleting a picture with [uses] does, said before deleting: one text for each case, whole sentences. */
internal fun Context.deleteText(uses: List<PictureUse>): String = getString(
    if (PictureUse.Wallpaper in uses) R.string.settings_pictures_delete_text_background else R.string.settings_pictures_delete_text,
)
