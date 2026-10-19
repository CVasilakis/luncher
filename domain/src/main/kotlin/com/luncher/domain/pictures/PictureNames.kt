package com.luncher.domain.pictures

/**
 * The name a picture is known by in the library, from the one its file or the app that shared it
 * gave: a label only, never a path, so without control characters and not longer than a name can
 * sensibly be. Empty when it had none.
 */
fun pictureName(given: String): String = given.filterNot { it.isISOControl() }.trim().take(MAX_NAME)

/** The longest name a picture keeps, in characters. */
private const val MAX_NAME = 120

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
    !PictureLimits.allowsBytes(picture.bytes) || !PictureLimits.allowsSize(picture.width, picture.height) -> ImportResult.TooLarge
    else -> nameClash(pictureName(picture.name), picture.bytes, library)
}
