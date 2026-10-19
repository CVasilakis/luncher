package com.luncher.domain.pictures

import java.text.BreakIterator

/**
 * The name a picture is known by in the library, from the one its file or the app that shared it
 * gave: a label only, never a path, so without control characters. Empty when it had none.
 *
 * At most [MAX_NAME] UTF-16 units, the longest file name of the filesystems a drop folder can be
 * on (ext4 and f2fs count 255 bytes, exFAT and NTFS 255 units), so a file's name is kept whole;
 * only an app's share can give a longer one. That keeps its start and its last [NAME_END] units,
 * where names that differ often do ("version 2.jpg"), with "…" between them, each part cut where
 * a character ends, so no emoji or accented letter is split.
 */
fun pictureName(given: String): String {
    val name = given.filterNot { it.isISOControl() }.trim()
    if (name.length <= MAX_NAME) return name
    val characters = BreakIterator.getCharacterInstance().apply { setText(name) }
    val start = name.substring(0, characters.atOrBefore(MAX_NAME - NAME_END - ELLIPSIS.length))
    val end = name.substring(characters.atOrAfter(name.length - NAME_END))
    return start + ELLIPSIS + end
}

/** The last place a character ends at or before [offset]. */
private fun BreakIterator.atOrBefore(offset: Int): Int = if (isBoundary(offset)) offset else preceding(offset)

/** The first place a character starts at or after [offset]. */
private fun BreakIterator.atOrAfter(offset: Int): Int = if (isBoundary(offset)) offset else following(offset)

/** The longest name a picture keeps, in UTF-16 units. */
private const val MAX_NAME = 255

/** The units at the end of a name too long that it keeps. */
private const val NAME_END = 50

private const val ELLIPSIS = "…"

/**
 * Why a picture named [name] ([pictureName]'s), of [bytes] (null when unknown), can't join
 * [library]: a picture there has exactly that name, and the same size ([ImportResult.SameFile],
 * the same file most likely) or another ([ImportResult.NameTaken]). Two pictures of one name
 * would be told apart by nothing on the screens, so the user deletes one, or renames the file.
 * Null when no picture has that name, or [name] is empty, as many shared pictures' are.
 */
fun nameClash(name: String, bytes: Long?, library: Collection<Picture>): ImportResult? {
    if (name.isEmpty()) return null
    val named = library.firstOrNull { it.name == name } ?: return null
    return if (bytes == named.bytes) ImportResult.SameFile(named) else ImportResult.NameTaken(named)
}

/**
 * Why a drop folder's [picture] can't be added to [library], as far as its listing tells, so Add
 * from folder says so at once, reading nothing: a format this TV can't show, more than
 * [PictureLimits] allow, or a name the library has ([nameClash]). Null when only adding it can
 * tell.
 */
fun whyRefused(picture: FolderPicture<*>, library: Collection<Picture>): ImportResult? = when {
    picture.unsupported != null -> ImportResult.UnsupportedFormat(picture.unsupported)
    else -> PictureLimits.exceeded(picture.bytes, picture.width, picture.height)?.let { ImportResult.TooLarge(it) }
        ?: nameClash(pictureName(picture.name), picture.bytes, library)
}
