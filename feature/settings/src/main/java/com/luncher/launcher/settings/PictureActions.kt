package com.luncher.launcher.settings

import android.app.Activity
import android.app.Dialog
import com.luncher.domain.appearance.AppearanceSettings
import com.luncher.domain.appearance.ColorRoles
import com.luncher.domain.apps.AppArrangements
import com.luncher.domain.apps.AppCustomizations
import com.luncher.domain.apps.ArrangedApps
import com.luncher.domain.apps.InstalledApps
import com.luncher.domain.apps.homeApps
import com.luncher.domain.pictures.Picture
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureLibrary
import com.luncher.domain.pictures.Pictures
import com.luncher.domain.pictures.Renaming
import com.luncher.domain.pictures.pictureUses
import com.luncher.domain.pictures.renaming
import com.luncher.domain.wallpaper.Wallpaper
import com.luncher.launcher.ui.Badge
import com.luncher.launcher.ui.DialogButton
import com.luncher.launcher.ui.MenuAction
import com.luncher.launcher.ui.MenuDialog
import com.luncher.launcher.ui.MessageDialog
import com.luncher.launcher.ui.NameDialog
import com.luncher.launcher.ui.R as UiR
import java.util.Locale

/**
 * The picture library as a picture editor shows it ([read]: with what each picture is used for),
 * and what can be done to its pictures there, each through its dialog (`:ui`'s): a long press of OK
 * on one offers Rename and Delete ([offer]); Delete all deletes every one ([confirmDeleteAll]).
 * Renaming asks for the new name, which another picture mustn't have ([renaming]); deleting asks
 * first, Cancel focused, saying what deleting does to the picture's uses (the wallpaper's goes back
 * to no picture, the tiles that show it to their app's own image; counted among the installed
 * apps, shown or hidden, those the user can find), and does it: the uses forgotten first (the
 * tiles' through [customizations], the wallpaper's through [Wallpaper.forget]), so nothing names
 * a picture that's gone, then the pictures, then [deleted] tells the screen. A renamed picture
 * needs nothing of it: the library's listeners read it again. In the theme's colors, [roles].
 */
internal class PictureActions(
    private val activity: Activity,
    private val pictures: Pictures,
    private val settings: AppearanceSettings,
    private val wallpaper: Wallpaper<*>,
    private val installedApps: InstalledApps,
    private val arrangements: AppArrangements,
    private val customizations: AppCustomizations,
    private val roles: () -> ColorRoles,
    private val deleted: (Set<PictureId>) -> Unit,
) {

    /** The library as [read] last; empty before. */
    var library = PictureLibrary(emptyList(), Locale.getDefault())
        private set

    private var dialog: Dialog? = null

    /** The library as it is now, by name, as the device's language sorts words, with each picture's uses. */
    fun read(): PictureLibrary {
        val apps = arranged()
        library = PictureLibrary(pictures.read(), Locale.getDefault(), pictureUses(settings.read(), apps.shown + apps.hidden))
        return library
    }

    /** The installed apps as the user set them up, shown and hidden, each with its tile's picture. */
    private fun arranged(): ArrangedApps =
        homeApps(installedApps.tvApps(), activity.packageName, arrangements.read(), customizations.read(), Locale.getDefault())

    /** What can be done to [picture]: rename it or delete it, under its name and size. */
    fun offer(picture: Picture) {
        show(
            MenuDialog(
                activity,
                roles(),
                activity.nameOf(picture),
                activity.details(picture.width, picture.height, picture.bytes),
                listOf(
                    MenuAction(activity.getString(R.string.settings_pictures_rename), UiR.drawable.icon_pencil) { askName(picture) },
                    MenuAction(activity.getString(R.string.settings_pictures_delete), UiR.drawable.icon_trash, deletes = true) { confirmDelete(picture) },
                ),
            ),
        )
    }

    /**
     * Asks before deleting every picture, Cancel focused, saying how many and what that does to
     * their uses (the background's picture, the tiles'); once they're deleted, [then]. Nothing
     * while there's none.
     */
    fun confirmDeleteAll(then: () -> Unit) {
        val count = library.pictures.size
        if (count == 0) return
        show(
            MessageDialog(
                activity,
                roles(),
                activity.getString(R.string.settings_background_delete_all_question),
                activity.deleteAllText(count, library.pictures.flatMap { library.usesOf(it.id) }),
                listOf(
                    DialogButton(activity.getString(R.string.settings_pictures_cancel), DialogButton.Kind.TONAL) {},
                    DialogButton(activity.getString(R.string.settings_pictures_delete), DialogButton.Kind.DANGER, UiR.drawable.icon_trash) {
                        delete(library.pictures.mapTo(HashSet()) { it.id })
                        then()
                    },
                ),
                Badge.Kind.DANGER to UiR.drawable.icon_trash,
            ),
        )
    }

    /** As the screen goes: the dialog that shows, at once. */
    fun dismiss() {
        dialog?.dismiss()
    }

    /** Asks for [picture]'s new name, and keeps the dialog, saying why, while it can't be that. */
    private fun askName(picture: Picture) {
        show(
            NameDialog(
                activity,
                roles(),
                activity.getString(R.string.settings_pictures_rename_title),
                picture.name,
                activity.getString(R.string.settings_pictures_cancel),
                activity.getString(R.string.settings_pictures_rename),
            ) { typed ->
                when (val renamed = renaming(picture, typed, library.pictures)) {
                    is Renaming.To -> {
                        pictures.rename(picture.id, renamed.name)
                        null
                    }
                    Renaming.NoName -> activity.getString(R.string.settings_pictures_rename_no_name)
                    Renaming.NameTaken -> activity.getString(R.string.settings_pictures_folder_name_taken)
                }
            },
        )
    }

    private fun confirmDelete(picture: Picture) {
        show(
            MessageDialog(
                activity,
                roles(),
                activity.getString(R.string.settings_pictures_delete_question, activity.nameOf(picture)),
                activity.deleteText(library.usesOf(picture.id)),
                // Cancel first, so a repeated OK doesn't delete.
                listOf(
                    DialogButton(activity.getString(R.string.settings_pictures_cancel), DialogButton.Kind.TONAL) {},
                    DialogButton(activity.getString(R.string.settings_pictures_delete), DialogButton.Kind.DANGER, UiR.drawable.icon_trash) { delete(setOf(picture.id)) },
                ),
                Badge.Kind.DANGER to UiR.drawable.icon_trash,
            ),
        )
    }

    /**
     * Deletes the pictures [ids]: the tiles that show one show their app's own image again, those of
     * hidden apps and of apps not installed now too, and if the wallpaper's is one, the background
     * has no picture any more, both stored at once; the wallpaper kept is deleted before the
     * pictures.
     */
    private fun delete(ids: Set<PictureId>) {
        arranged().withoutPictures(ids)?.let(customizations::save)
        wallpaper.forget(ids)
        pictures.delete(ids)
        deleted(ids)
    }

    private fun show(dialog: Dialog) {
        this.dialog?.dismiss()
        this.dialog = dialog
        dialog.show()
    }
}
